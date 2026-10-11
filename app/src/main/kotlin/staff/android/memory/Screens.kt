package staff.android.memory

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import staff.android.memory.core.*

object ShoppingState {
    var saved by mutableStateOf(emptySet<String>())
    fun toggle(id: String) { saved = if (id in saved) saved - id else saved + id }
}

@Composable fun BookWindow(content: @Composable () -> Unit) {
    CommonTheme {
        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            Box(Modifier.fillMaxSize(), contentAlignment=Alignment.TopCenter) {
                Box(Modifier.widthIn(max=960.dp).fillMaxWidth().semantics { testTagsAsResourceId=true }) { content() }
            }
        }
    }
}

@Composable fun Brand(action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
        Image(painterResource(R.drawable.ic_common_goods),null,Modifier.size(40.dp))
        Spacer(Modifier.width(10.dp))
        Text("Common Goods",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
        TextButton(onClick=onAction) { Text(action) }
    }
}

@Composable fun ProductPhoto(product: Product, modifier: Modifier) {
    val context=LocalContext.current
    val store=remember(context.applicationContext) { ProductImages.get(context) }
    key(product.id,store) {
        var loading by remember { mutableStateOf(true) }
        val bitmap by produceState<android.graphics.Bitmap?>(null) { value=store.load(product.id);loading=false }
        Box(modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center) {
            if(bitmap!=null) Image(bitmap!!.asImageBitmap(),product.title,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            else Text(if(loading) "Loading image…" else "Image unavailable",Modifier.padding(16.dp))
        }
    }
}

@Composable fun ShopScreen(onProduct: (Product)->Unit,onLab: ()->Unit) {
    Column {
        Brand("Lab",onLab)
        Column(Modifier.padding(20.dp)) {
            Text("Good things,\nevery day.",style=MaterialTheme.typography.displaySmall)
            Text("A considered collection for everyday living.",style=MaterialTheme.typography.bodyLarge,modifier=Modifier.padding(top=10.dp))
        }
        val config=LocalConfiguration.current
        val columns=if(config.fontScale>=1.5f) 1 else if(config.screenWidthDp>=700) 3 else 2
        LazyVerticalGrid(GridCells.Fixed(columns),Modifier.testTag("shop"),contentPadding=PaddingValues(20.dp),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
            items(products,key={it.id}) { product ->
                Column {
                    ProductPhoto(product,Modifier.fillMaxWidth().aspectRatio(1f))
                    Text(product.title,style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(top=12.dp))
                    Text(product.price,style=MaterialTheme.typography.bodyLarge)
                    TextButton(onClick={onProduct(product)},contentPadding=PaddingValues(0.dp)) { Text("Explore ${product.title}") }
                }
            }
        }
    }
}

@Composable fun DetailScreen(product: Product, available: Int, onBack: ()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("detail")) {
        Brand("Back",onBack)
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("THE EVERYDAY COLLECTION",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
            Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
                ProductPhoto(product,Modifier.widthIn(max=504.dp).fillMaxWidth().aspectRatio(1.2f))
            }
            Text(product.title,style=MaterialTheme.typography.headlineLarge)
            Text(product.price,style=MaterialTheme.typography.titleLarge)
            Text(product.description,style=MaterialTheme.typography.bodyLarge)
            Text("$available available · Dispatches in 2–3 days",style=MaterialTheme.typography.bodyMedium,modifier=Modifier.testTag("availability"))
            Button(onClick={ ShoppingState.toggle(product.id) },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text(if(product.id in ShoppingState.saved) "Saved" else "Save for later") }
            HorizontalDivider()
            Text("Made for your everyday",style=MaterialTheme.typography.titleMedium)
            Text("Thoughtful details. Familiar materials. A useful addition to your day.",style=MaterialTheme.typography.bodyMedium)
        }
    }
}
