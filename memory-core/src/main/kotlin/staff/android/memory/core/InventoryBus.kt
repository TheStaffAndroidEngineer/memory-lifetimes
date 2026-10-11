package staff.android.memory.core

fun interface InventoryListener { fun onStockChanged(available: Int) }

class Subscription(private var release: (() -> Unit)?) : AutoCloseable {
    override fun close() {
        val action = release ?: return
        release = null
        action()
    }
}

/** Synchronous, owner-thread-confined publisher. The app uses it only on main. */
class InventoryBus(initialStock: Int = 18) {
    private val listeners = LinkedHashMap<Long, InventoryListener>()
    private var nextId = 0L
    var stock: Int = initialStock
        private set
    val listenerCount: Int get() = listeners.size
    init { require(initialStock >= 0) }

    fun subscribe(listener: InventoryListener): Subscription {
        val id = Math.addExact(nextId, 1L)
        nextId = id
        listeners[id] = listener
        try { listener.onStockChanged(stock) }
        catch (failure: Throwable) { listeners.remove(id); throw failure }
        return Subscription { listeners.remove(id) }
    }

    fun publish(available: Int) {
        require(available >= 0)
        stock = available
        for ((id, listener) in listeners.toList()) {
            if (listeners[id] === listener) listener.onStockChanged(available)
        }
    }

    /** Diagnostic reset, never the production repair for a missing close. */
    fun clearForLab() { listeners.clear() }
}

object ProcessInventory {
    val bus = InventoryBus()
}
