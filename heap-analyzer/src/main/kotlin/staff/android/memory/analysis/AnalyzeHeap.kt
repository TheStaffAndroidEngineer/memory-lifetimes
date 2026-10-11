package staff.android.memory.analysis

import shark.*
import shark.HprofHeapGraph.Companion.openHeapGraph
import java.io.File
import java.security.MessageDigest

private fun json(value: Any?): String = when(value) {
    null -> "null"
    is String -> "\""+value.flatMap { c -> when(c) {
        '\\' -> "\\\\".toList(); '"' -> "\\\"".toList(); '\n' -> "\\n".toList(); '\r' -> "\\r".toList(); '\t' -> "\\t".toList()
        else -> if(c.code<32) "\\u%04x".format(c.code).toList() else listOf(c)
    } }.joinToString("")+"\""
    is Number,is Boolean -> value.toString()
    is Map<*,*> -> value.entries.joinToString(",","{","}") { json(it.key.toString())+":"+json(it.value) }
    is Iterable<*> -> value.joinToString(",","[","]") {json(it)}
    else -> error("Unsupported JSON value ${value.javaClass}")
}
fun main(args: Array<String>) {
    require(args.size==2) { "AnalyzeHeap INPUT.hprof OUTPUT_DIRECTORY" }
    val file=File(args[0]);val output=File(args[1]);require(file.isFile);require(!output.exists());output.mkdirs()
    val digest=MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") {"%02x".format(it)}
    file.openHeapGraph().use { graph ->
        val targets=graph.instances.filter {it.instanceClassName=="staff.android.memory.ProductDetailActivity"}.toList()
        val states=targets.map { instance ->
            val destroyed=instance["android.app.Activity","mDestroyed"]?.value?.asBoolean
                ?: error("Missing boolean Activity.mDestroyed on ${instance.objectId}")
            mapOf("objectId" to instance.objectId,"destroyed" to destroyed)
        }
        val selected=states.filter {it["destroyed"]==true}.map {it["objectId"] as Long}.toSet()
        val analyzer=HeapAnalyzer(OnAnalysisProgressListener {System.err.println("SHARK $it")})
        val result=analyzer.analyze(file,graph,LeakingObjectFinder {selected},
            referenceMatchers=AndroidReferenceMatchers.buildKnownReferences(setOf(AndroidReferenceMatchers.REFERENCES)),
            computeRetainedHeapSize=true,objectInspectors=AndroidObjectInspectors.appDefaults)
        File(output,"analysis.txt").writeText(result.toString())
        check(result is HeapAnalysisSuccess) {result.toString()}
        val paths=result.allLeaks.flatMap {it.leakTraces.asSequence()}.map { trace ->
            mapOf("gcRoot" to trace.gcRootType.name,"targetClass" to trace.leakingObject.className,
                "retainedSizeEstimateBytes" to trace.retainedHeapByteSize,"retainedObjectCount" to trace.retainedObjectCount,
                "path" to trace.toSimplePathString(),"references" to trace.referencePath.map {
                    mapOf("ownerClass" to it.originObject.className,"reference" to it.referenceName,"type" to it.referenceType.name)
                })
        }.toList()
        val record=mapOf("status" to "ANALYZED","sharkVersion" to "2.14","heapSha256" to digest,
            "heapBytes" to file.length(),"targetInstances" to states,"selectedDestroyedIds" to selected,
            "applicationLeakGroups" to result.applicationLeaks.size,"libraryLeakGroups" to result.libraryLeaks.size,
            "unreachableSelectedObjects" to result.unreachableObjects.size,"paths" to paths,
            "matcherPolicy" to "AndroidReferenceMatchers.REFERENCES only; weak/soft/phantom and finalizer/cleaner exclusions",
            "scope" to "Diagnostic debug heap. Analyzer-reported approximate retained size, not PSS/native-total or physical layout bytes; selected shortest path does not enumerate all root paths.")
        File(output,"analysis.json").writeText(json(record)+"\n")
        println(json(mapOf("targets" to states.size,"destroyed" to selected.size,"paths" to paths.size,"unreachable" to result.unreachableObjects.size)))
    }
}
