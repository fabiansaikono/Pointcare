package be.saikono.poincare.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private val curriculumIconBitmapCache = LruCache<String, Bitmap>(72)
private val curriculumIconDecodeSemaphore = Semaphore(2)

/** Renders the canonical SVG asset exported by the Curriculum Room. */
@Composable
fun CurriculumAssetIcon(
    assetPath: String?,
    @DrawableRes fallbackIconResId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    sizePx: Int = 160
) {
    val bitmap = rememberCurriculumAssetBitmap(assetPath, sizePx)
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else {
        Image(
            painter = painterResource(fallbackIconResId),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun rememberCurriculumAssetBitmap(assetPath: String?, sizePx: Int): ImageBitmap? {
    val context = LocalContext.current
    val cacheKey = assetPath?.let { "$it@$sizePx" }
    val bitmap by produceState<Bitmap?>(
        initialValue = cacheKey?.let(curriculumIconBitmapCache::get),
        key1 = cacheKey
    ) {
        if (assetPath == null || cacheKey == null || value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            curriculumIconDecodeSemaphore.withPermit {
                curriculumIconBitmapCache.get(cacheKey) ?: runCatching {
                    context.assets.open(assetPath).use { input ->
                        if (assetPath.endsWith(".svg", ignoreCase = true)) {
                            val svg = SVG.getFromInputStream(input)
                            Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also { decoded ->
                                svg.setDocumentWidth(sizePx.toFloat())
                                svg.setDocumentHeight(sizePx.toFloat())
                                svg.renderToCanvas(AndroidCanvas(decoded))
                            }
                        } else {
                            BitmapFactory.decodeStream(input)
                        }
                    }?.also { decoded -> curriculumIconBitmapCache.put(cacheKey, decoded) }
                }.getOrNull()
            }
        }
    }
    return bitmap?.asImageBitmap()
}
