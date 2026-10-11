package staff.android.memory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import staff.android.memory.core.*

enum class OwnershipMode { Leaky, Repaired }

class ProductDetailActivity : ComponentActivity() {
    private val availability = mutableIntStateOf(0)
    private var subscription: Subscription? = null
    private lateinit var mode: OwnershipMode
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = if(intent.getStringExtra("mode")=="leaky") OwnershipMode.Leaky else OwnershipMode.Repaired
        val product = products.firstOrNull { it.id==intent.getStringExtra("product") } ?: products.first()
        setContent { BookWindow { DetailScreen(product,availability.intValue,onBack={finish()}) } }
    }
    override fun onStart() {
        super.onStart()
        subscription = ProcessInventory.bus.subscribe { available ->
            availability.intValue = available
        }
    }
    override fun onStop() {
        if (mode == OwnershipMode.Repaired) {
            subscription?.close()
            subscription = null
        }
        // Deliberate Lab defect: Leaky retains its callback after the screen stops.
        super.onStop()
    }
}
