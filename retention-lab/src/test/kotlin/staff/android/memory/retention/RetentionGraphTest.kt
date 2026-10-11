package staff.android.memory.retention

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RetentionGraphTest {
    private val analyzer: RetentionAnalysis =
        if (System.getProperty("retention.solution") == "learner") LearnerRetention else ReferenceRetention
    private fun node(id: String, bytes: Long, vararg refs: String) = HeapObject(id, bytes, refs.toSet())
    private val graph = listOf(
        node("bus", 40, "callback"), node("callback", 24, "detail"),
        node("detail", 80, "payload", "photo"), node("payload", 1000),
        node("cache", 64, "photo"), node("photo", 2000), node("orphan", 9000),
    )
    private fun analyze(subject: String = "detail") = analyzer.analyze(graph, setOf("bus", "cache"), subject)

    @Test fun sharedPhotoIsReachableButNotRetainedByDetail() {
        val report = analyze()
        assertEquals(setOf("bus", "callback", "detail", "payload", "cache", "photo"), report.reachable)
        assertEquals(setOf("detail", "payload"), report.retained)
        assertEquals(3208L, report.reachableBytes)
        assertEquals(1080L, report.retainedBytes)
    }
    @Test fun removingCallbackAlsoReleasesItsExclusivelyOwnedDetail() {
        assertEquals(setOf("callback", "detail", "payload"), analyze("callback").retained)
        assertEquals(1104L, analyze("callback").retainedBytes)
    }
    @Test fun removingOneOwnerDoesNotCollectSharedPhoto() {
        assertEquals(setOf("cache"), analyze("cache").retained)
        assertEquals(64L, analyze("cache").retainedBytes)
    }
    @Test fun aSubjectWhichIsItselfSharedStillRetainsItselfWhenRemoved() {
        assertEquals(setOf("photo"), analyze("photo").retained)
        assertEquals(2000L, analyze("photo").retainedBytes)
    }
    @Test fun unreachableSubjectRetainsNothingFromTheRootedGraph() {
        assertEquals(emptySet<String>(), analyze("orphan").retained)
        assertEquals(0L, analyze("orphan").retainedBytes)
    }
    @Test fun emptyRootsDoNotMakeAllObjectsLive() {
        val report = analyzer.analyze(graph, emptySet(), "detail")
        assertEquals(emptySet<String>(), report.reachable)
        assertEquals(0L, report.reachableBytes)
        assertEquals(0L, report.retainedBytes)
    }
    @Test fun aSubjectRootIsExcludedWhileOtherRootsRemain() {
        val report = analyzer.analyze(graph, setOf("bus", "cache", "detail"), "detail")
        assertEquals(setOf("detail", "payload"), report.retained)
    }
    @Test fun alternateRootToDescendantKeepsItReachable() {
        val report = analyzer.analyze(graph, setOf("bus", "cache", "payload"), "detail")
        assertEquals(setOf("detail"), report.retained)
        assertEquals(80L, report.retainedBytes)
    }
    @Test fun reachableCycleTerminatesAndCountsEachObjectOnce() {
        val nodes = listOf(node("root", 1, "a"), node("a", 2, "b"), node("b", 3, "a"))
        val report = analyzer.analyze(nodes, setOf("root"), "a")
        assertEquals(6L, report.reachableBytes)
        assertEquals(setOf("a", "b"), report.retained)
        assertEquals(5L, report.retainedBytes)
    }
    @Test fun separateCycleIsNotReachable() {
        val nodes = listOf(node("root", 1), node("a", 2, "b"), node("b", 3, "a"))
        val report = analyzer.analyze(nodes, setOf("root"), "a")
        assertEquals(setOf("root"), report.reachable)
        assertEquals(emptySet<String>(), report.retained)
    }
    @Test fun selfReferenceDoesNotPreventExclusiveRetention() {
        val report = analyzer.analyze(listOf(node("a", 7, "a")), setOf("a"), "a")
        assertEquals(setOf("a"), report.retained)
        assertEquals(7L, report.retainedBytes)
    }
    @Test fun inputOrderCannotChangeTheReport() {
        assertEquals(analyze(), analyzer.analyze(graph.reversed(), linkedSetOf("cache", "bus"), "detail"))
    }
    @Test fun snapshotResultDoesNotChangeWhenCallerChangesInputSets() {
        val refs = mutableSetOf("child")
        val roots = mutableSetOf("root")
        val report = analyzer.analyze(listOf(HeapObject("root", 1, refs), node("child", 2)), roots, "root")
        refs.clear(); roots.clear()
        assertEquals(setOf("root", "child"), report.reachable)
        assertEquals(setOf("root", "child"), report.retained)
        assertThrows(UnsupportedOperationException::class.java) { (report.retained as MutableSet).clear() }
    }
    @Test fun zeroByteObjectsStillParticipateInReachability() {
        val report = analyzer.analyze(listOf(node("root", 0, "child"), node("child", 0)), setOf("root"), "root")
        assertEquals(setOf("root", "child"), report.retained)
        assertEquals(0L, report.retainedBytes)
    }
    @Test fun duplicateIdsAreRejectedRatherThanOverwritten() {
        assertThrows(IllegalArgumentException::class.java) {
            analyzer.analyze(listOf(node("root", 1), node("root", 2)), setOf("root"), "root")
        }
    }
    @Test fun blankIdsAndNegativeSizesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { analyzer.analyze(listOf(node(" ", 1)), setOf(" "), " ") }
        assertThrows(IllegalArgumentException::class.java) { analyzer.analyze(listOf(node("root", -1)), setOf("root"), "root") }
    }
    @Test fun unknownEdgesAreRejectedEvenOnUnreachableObjects() {
        assertThrows(IllegalArgumentException::class.java) {
            analyzer.analyze(listOf(node("root", 1), node("orphan", 2, "absent")), setOf("root"), "root")
        }
    }
    @Test fun unknownRootsAndSubjectsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { analyzer.analyze(graph, setOf("absent"), "detail") }
        assertThrows(IllegalArgumentException::class.java) { analyzer.analyze(graph, setOf("bus"), "absent") }
    }
    @Test fun exactLongMaximumDoesNotOverflow() {
        val report = analyzer.analyze(listOf(node("root", Long.MAX_VALUE, "zero"), node("zero", 0)), setOf("root"), "root")
        assertEquals(Long.MAX_VALUE, report.retainedBytes)
    }
    @Test fun sumOverflowIsRejectedRatherThanWrapping() {
        assertThrows(ArithmeticException::class.java) {
            analyzer.analyze(listOf(node("root", Long.MAX_VALUE, "child"), node("child", 1)), setOf("root"), "root")
        }
    }
}
