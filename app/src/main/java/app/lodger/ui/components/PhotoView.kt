package app.lodger.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import app.lodger.container
import app.lodger.data.Photos
import app.lodger.domain.Pos
import app.lodger.domain.Trace
import app.lodger.R
import app.lodger.ui.art.Blend
import app.lodger.ui.art.Ink
import app.lodger.ui.art.Layer
import androidx.core.content.res.ResourcesCompat
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.unit.IntOffset
import app.lodger.ui.theme.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.random.Random

@Composable
fun rememberPhoto(rel: String?, max: Int): State<Bitmap?> {
    val c = LocalContext.current.container
    return produceState<Bitmap?>(null, rel, max) {
        value = if (rel.isNullOrEmpty()) null else withContext(Dispatchers.IO) { Photos.load(c.file(rel), max) }
    }
}

/** A night photo: drained of colour, darkened toward sepia. */
private val nightLook = ColorFilter.colorMatrix(ColorMatrix().apply {
    setToSaturation(.3f)
    timesAssign(ColorMatrix(floatArrayOf(.9f, 0f, 0f, 0f, 0f, 0f, .8f, 0f, 0f, 0f, 0f, 0f, .66f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
})

private val grain: ImageBitmap by lazy {
    val r = Random(3)
    val b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
    for (y in 0 until 96) for (x in 0 until 96) {
        val v = r.nextInt(256)
        b.setPixel(x, y, android.graphics.Color.argb(r.nextInt(70), v, v, v))
    }
    b.asImageBitmap()
}

/** Sizes the child to cover the parent at [aspect] (width/height), centred; the overflow is cut. */
private fun Modifier.cover(aspect: Float) = this.layout { m, cs ->
    val w = cs.maxWidth; val h = cs.maxHeight
    val (cw, ch) = if (w / aspect >= h) w to (w / aspect).toInt() else (h * aspect).toInt() to h
    val p = m.measure(Constraints.fixed(cw, ch))
    layout(w, h) { p.place((w - cw) / 2, (h - ch) / 2) }
}

/** Rendered marks, kept per photo and mark so tiles and pages don't paint them twice. */
private val inked = android.util.LruCache<String, List<Pair<ImageBitmap, Layer>>>(60)

/** Paints a mark's layers for this photo, off the main thread (cached). */
private fun ink(key: String, make: () -> List<Layer>) = inked.get(key) ?: make().map { it.bmp.asImageBitmap() to it }.also { inked.put(key, it) }

/**
 * A room photo with the lodger's marks on it. Marks in [develop] come up slowly, like a print in the tray;
 * [flicker] dims the picture now and then; [flash] (a spot) shows someone there for a blink.
 */
@Composable
fun PhotoView(
    rel: String?,
    marks: List<Pair<Trace, Pos>>,
    modifier: Modifier = Modifier,
    develop: Set<Trace> = emptySet(),
    flicker: Boolean = false,
    flash: Pos? = null,
    onFlash: () -> Unit = {},
    max: Int = 1280,
) {
    val ctx = LocalContext.current
    val bmp by rememberPhoto(rel, max)
    val inks by produceState<List<Pair<Trace, List<Pair<ImageBitmap, Layer>>>>>(emptyList(), bmp, marks) {
        val b = bmp ?: return@produceState
        value = withContext(Dispatchers.Default) {
            val hand = runCatching { ResourcesCompat.getFont(ctx, R.font.hand) }.getOrNull()
            val (lum, gw, gh) = Photos.lum(b)
            marks.mapIndexed { i, (t, p) ->
                val l = lum[(p.y * gh).toInt().coerceIn(0, gh - 1) * gw + (p.x * gw).toInt().coerceIn(0, gw - 1)]
                t to ink("$rel/${b.width}/${t.night}/${t.mark}/${t.text.hashCode()}/${p.x}/${p.y}") { Ink.layers(t, p, b.width, b.height, i * 31 + t.night, l, hand) }
            }
        }
    }
    val ghost by produceState<List<Pair<ImageBitmap, Layer>>>(emptyList(), bmp, flash) {
        val b = bmp ?: return@produceState
        val f = flash ?: return@produceState
        value = withContext(Dispatchers.Default) { ink("$rel/${b.width}/ghost/${f.x}/${f.y}") { Ink.figure(b.width, b.height, f, .46f) } }
    }
    val dev = remember(rel, develop) { Animatable(if (develop.isEmpty()) 1f else 0f) }
    LaunchedEffect(rel, develop, inks.isNotEmpty()) { if (inks.isNotEmpty() && develop.isNotEmpty()) { delay(500); dev.animateTo(1f, tween(2600, easing = LinearEasing)) } }
    var shown by remember(rel) { mutableStateOf(false) }
    LaunchedEffect(rel, flash, ghost.isNotEmpty()) {
        if (flash == null || ghost.isEmpty()) return@LaunchedEffect
        delay(1500L + Random.nextLong(2500))
        shown = true; onFlash(); delay(140); shown = false
    }
    val dim = if (flicker) {
        val t = rememberInfiniteTransition("flicker")
        t.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
            durationMillis = 7000
            0f at 6400; .45f at 6450; .05f at 6520; .3f at 6620; 0f at 6750
        }, RepeatMode.Restart), "dim").value
    } else 0f
    Box(modifier.clipToBounds().background(C.night)) {
        val b = bmp ?: return@Box
        val img = remember(b) { b.asImageBitmap() }
        Canvas(Modifier.fillMaxSize().cover(b.width / b.height.toFloat())) {
            drawImage(img, dstSize = IntSize(size.width.toInt(), size.height.toInt()), colorFilter = nightLook)
            val k = size.width / b.width
            fun lay(list: List<Pair<ImageBitmap, Layer>>, alpha: Float) {
                for ((im, l) in list) drawImage(
                    im, dstOffset = IntOffset((l.x * k).toInt(), (l.y * k).toInt()), dstSize = IntSize((im.width * k).toInt().coerceAtLeast(1), (im.height * k).toInt().coerceAtLeast(1)),
                    alpha = alpha, blendMode = if (l.blend == Blend.SCREEN) BlendMode.Screen else BlendMode.SrcOver,
                    colorFilter = if (l.blend == Blend.SCREEN) null else nightLook,
                )
            }
            for ((t, list) in inks) lay(list, if (t in develop) dev.value else 1f)
            if (shown) lay(ghost, 1f)
            drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)), alpha = .5f)
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0xB0000000)), center = center, radius = size.maxDimension * .72f))
            if (dim > 0f) drawRect(Color.Black, alpha = dim)
        }
    }
}
