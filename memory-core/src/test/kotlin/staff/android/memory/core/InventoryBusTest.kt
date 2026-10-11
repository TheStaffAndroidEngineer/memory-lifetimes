package staff.android.memory.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class InventoryBusTest {
    @Test fun subscriberImmediatelyReceivesCurrentStock() {
        val bus=InventoryBus(18);val values=mutableListOf<Int>()
        bus.subscribe { values+=it };bus.publish(12)
        assertEquals(listOf(18,12),values)
    }
    @Test fun closingUnregistersAndPreventsFurtherCallbacks() {
        val bus=InventoryBus();val values=mutableListOf<Int>()
        val subscription=bus.subscribe { values+=it };subscription.close();bus.publish(9)
        assertEquals(listOf(18),values);assertEquals(0,bus.listenerCount)
    }
    @Test fun closeIsIdempotentAndReleasesAction() {
        var calls=0;val subscription=Subscription { calls++ };subscription.close();subscription.close()
        assertEquals(1,calls)
    }
    @Test fun closeOneDoesNotCloseAnother() {
        val bus=InventoryBus();val first=bus.subscribe {};val seen=mutableListOf<Int>()
        bus.subscribe { seen+=it };first.close();bus.publish(7)
        assertEquals(1,bus.listenerCount);assertEquals(listOf(18,7),seen)
    }
    @Test fun failureInInitialCallbackDoesNotLeakRegistration() {
        val bus=InventoryBus();assertThrows(IllegalStateException::class.java) {bus.subscribe {error("test")}}
        assertEquals(0,bus.listenerCount)
    }
    @Test fun removalDuringDispatchSkipsRemovedListener() {
        val bus=InventoryBus();var second:Subscription?=null
        bus.subscribe { if(it==3) second?.close() };val seen=mutableListOf<Int>()
        second=bus.subscribe {seen+=it};bus.publish(3)
        assertEquals(listOf(18),seen)
    }
    @Test fun reopeningObservesLatestValue() {
        val bus=InventoryBus();bus.subscribe {}.close();bus.publish(4)
        var latest=-1;val next=bus.subscribe {latest=it}
        assertEquals(4,latest);assertEquals(1,bus.listenerCount);next.close()
    }
    @Test fun invalidStockDoesNotMutateAcceptedStock() {
        assertThrows(IllegalArgumentException::class.java) {InventoryBus(-1)}
        val bus=InventoryBus(5);assertThrows(IllegalArgumentException::class.java) {bus.publish(-2)}
        assertEquals(5,bus.stock)
    }
    @Test fun labResetReleasesAllRegistrations() {
        val bus=InventoryBus();val a=bus.subscribe {};bus.subscribe {};bus.clearForLab();a.close()
        assertEquals(0,bus.listenerCount)
    }
}
