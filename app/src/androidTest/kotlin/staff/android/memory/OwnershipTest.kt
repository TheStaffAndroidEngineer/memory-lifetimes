package staff.android.memory

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import staff.android.memory.core.*
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class OwnershipTest {
    @get:Rule val compose=createEmptyComposeRule()
    private fun main(block:()->Unit)=InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    private fun launch(mode:String="repaired")=ActivityScenario.launch<ProductDetailActivity>(Intent(ApplicationProvider.getApplicationContext(),ProductDetailActivity::class.java).putExtra("mode",mode).putExtra("product","camera"))
    @Before fun reset()=main { ProcessInventory.bus.clearForLab();ProcessInventory.bus.publish(18);ShoppingState.saved=emptySet() }
    @After fun cleanup()=main { ProcessInventory.bus.clearForLab() }

    @Test fun stoppedScreenReleasesAndRestartGetsLatestStock() {
        launch().use { scenario ->
            main { assertEquals(1,ProcessInventory.bus.listenerCount) }
            scenario.moveToState(Lifecycle.State.CREATED)
            main { assertEquals(0,ProcessInventory.bus.listenerCount);ProcessInventory.bus.publish(7) }
            scenario.moveToState(Lifecycle.State.RESUMED);compose.waitForIdle()
            compose.onNodeWithTag("availability").performScrollTo().assertTextContains("7 available",substring=true)
            main { assertEquals(1,ProcessInventory.bus.listenerCount) }
        }
        main { assertEquals(0,ProcessInventory.bus.listenerCount) }
    }
    @Test fun recreationLeavesOneActiveSubscription() {
        launch().use { scenario -> scenario.recreate();main { assertEquals(1,ProcessInventory.bus.listenerCount) } }
        main { assertEquals(0,ProcessInventory.bus.listenerCount) }
    }
    @Test fun sixClosedRepairedDetailsLeaveNoRegistrations() {
        repeat(6) { launch().close() };main { assertEquals(0,ProcessInventory.bus.listenerCount) }
    }
    @Test fun deliberateLeakyRouteLeavesThreeRegistrations() {
        repeat(3) { launch("leaky").close() };main { assertEquals(3,ProcessInventory.bus.listenerCount) }
    }
    @Test fun liveStockAndSaveStillWorkAfterRepair() {
        launch().use {
            main {ProcessInventory.bus.publish(12)};compose.waitForIdle()
            compose.onNodeWithTag("availability").performScrollTo().assertTextContains("12 available",substring=true)
            compose.onNodeWithText("Save for later").performScrollTo().performClick()
            compose.onNodeWithText("Saved").assertExists()
        }
        launch().use { compose.onNodeWithText("Saved").performScrollTo().assertExists() }
    }
    @Test fun cacheBoundAndEvictionDoNotRecycleBorrowedBitmap() {
        lateinit var store:ImageStore
        main {store=ProductImages.get(ApplicationProvider.getApplicationContext());store.clear()}
        runBlocking {
            val borrowed=requireNotNull(store.load("camera"))
            products.forEach {requireNotNull(store.load(it.id));assertTrue(store.cachedBytes<=8*1024*1024)}
            store.clear();assertEquals(0,store.cachedBytes);assertFalse(borrowed.isRecycled)
            assertTrue(borrowed.width>0)
        }
    }
}
