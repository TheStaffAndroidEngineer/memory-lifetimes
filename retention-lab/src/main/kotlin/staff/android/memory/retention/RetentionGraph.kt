package staff.android.memory.retention

import java.util.Collections

/** Supplied strong-only graph; this is not an HPROF parser or physical memory model. */
data class HeapObject(val id: String, val shallowBytes: Long, val strongReferences: Set<String>)
data class RetentionReport(
    val reachable: Set<String>,
    val retained: Set<String>,
    val reachableBytes: Long,
    val retainedBytes: Long,
)
interface RetentionAnalysis {
    fun analyze(objects: List<HeapObject>, roots: Set<String>, subjectId: String): RetentionReport
}

/** Complete reference. Callers keep supplied collections stable during analysis. */
object ReferenceRetention : RetentionAnalysis {
    override fun analyze(objects: List<HeapObject>, roots: Set<String>, subjectId: String): RetentionReport {
        val graph = objects.associate { it.id to it.copy(strongReferences = it.strongReferences.toSet()) }
        require(graph.size == objects.size) { "Object IDs must be unique" }
        require(graph.values.all { it.id.isNotBlank() && it.shallowBytes >= 0 })
        require(graph.values.all { node -> node.strongReferences.all { it in graph } })
        val rootIds = roots.toSet()
        require(rootIds.all { it in graph }) { "Unknown root" }
        require(subjectId in graph) { "Unknown subject" }
        val before = reachableFrom(graph, rootIds, excluded = null)
        val after = reachableFrom(graph, rootIds, excluded = subjectId)
        val retained = before - after
        return RetentionReport(
            immutableIds(before), immutableIds(retained),
            totalBytes(graph, before), totalBytes(graph, retained),
        )
    }

    private fun reachableFrom(
        graph: Map<String, HeapObject>, roots: Set<String>, excluded: String?,
    ): Set<String> {
        val visited = mutableSetOf<String>()
        val pending = ArrayDeque<String>()
        roots.forEach { if (it != excluded) pending.addLast(it) }
        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            if (!visited.add(id)) continue
            graph.getValue(id).strongReferences.forEach { target ->
                if (target != excluded && target !in visited) pending.addLast(target)
            }
        }
        return visited
    }

    private fun totalBytes(graph: Map<String, HeapObject>, ids: Set<String>): Long =
        ids.fold(0L) { total, id -> Math.addExact(total, graph.getValue(id).shallowBytes) }

    private fun immutableIds(ids: Set<String>): Set<String> =
        Collections.unmodifiableSet(ids.toSortedSet())
}

/** Deliberately unfinished: every supplied object is assumed reachable and retained. */
object LearnerRetention : RetentionAnalysis {
    override fun analyze(objects: List<HeapObject>, roots: Set<String>, subjectId: String): RetentionReport {
        val ids = objects.map { it.id }.toSet()
        val bytes = objects.sumOf { it.shallowBytes }
        return RetentionReport(ids, ids, bytes, bytes)
    }
}
