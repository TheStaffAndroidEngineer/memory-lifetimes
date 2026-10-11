package staff.android.memory

import android.content.Intent
import android.os.Bundle
import android.os.Debug
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.*
import staff.android.memory.core.*
import java.io.File

class MainActivity : ComponentActivity() {
    private val scope = MainScope()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var mode by rememberSaveable { mutableStateOf("repaired") }
            var lab by remember { mutableStateOf(false) }
            var status by remember { mutableStateOf("") }
            BookWindow {
                ShopScreen(onProduct={ product -> startActivity(Intent(this,ProductDetailActivity::class.java).putExtra("product",product.id).putExtra("mode",mode)) },onLab={lab=true})
                if(lab) AlertDialog(onDismissRequest={lab=false},title={Text("Memory investigation")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Current path: $mode. Stock listeners: ${ProcessInventory.bus.listenerCount}. Image cache: ${ProductImages.get(this@MainActivity).cachedBytes} bytes. Counts locate a question; the heap supplies the reference path.")
                    TextButton(onClick={mode="leaky";lab=false}) { Text("Leaky listener") }
                    TextButton(onClick={mode="repaired";lab=false}) { Text("Repaired listener") }
                    TextButton(onClick={ProcessInventory.bus.publish(12);status="Local stock changed to 12"}) { Text("Change local stock") }
                    TextButton(onClick={ProcessInventory.bus.clearForLab();status="Listeners cleared for reset"}) { Text("Reset listeners") }
                    TextButton(onClick={ProductImages.get(this@MainActivity).clear();status="Cache entries evicted; visible images may still be owned"}) { Text("Clear image cache") }
                    if(BuildConfig.DEBUG) TextButton(onClick={dumpHeap("manual");status="Writing files/manual.hprof"}) { Text("Write heap snapshot") }
                    if(status.isNotEmpty()) Text(status)
                }},confirmButton={TextButton(onClick={lab=false}) {Text("Close")}})
            }
        }
        handleDiagnostic(intent)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent);handleDiagnostic(intent) }
    private fun handleDiagnostic(intent: Intent) {
        if(!BuildConfig.DEBUG) return
        when(intent.getStringExtra("diagnostic")) {
            "open" -> startActivity(Intent(this,ProductDetailActivity::class.java).putExtra("product","camera").putExtra("mode",intent.getStringExtra("mode")))
            "clear" -> ProcessInventory.bus.clearForLab()
            "dump" -> dumpHeap(intent.getStringExtra("name") ?: "snapshot")
        }
    }
    private fun dumpHeap(name: String) {
        if(!BuildConfig.DEBUG || !name.matches(Regex("[a-z0-9_-]+"))) return
        scope.launch {
            delay(800) // Allow completed Activity transitions to leave the main event queue.
            withContext(Dispatchers.IO) {
                Runtime.getRuntime().gc();System.runFinalization()
                val path=File(filesDir,"$name.hprof")
                Debug.dumpHprofData(path.absolutePath)
                Log.i("CG22","HEAP_READY ${path.name} bytes=${path.length()}")
            }
        }
    }
    override fun onDestroy() { scope.cancel();super.onDestroy() }
}
