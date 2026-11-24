package com.meloberry.android.core.ui.markdown

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meloberry.android.core.model.ContentAlign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.noties.jlatexmath.JLatexMathAndroid
import ru.noties.jlatexmath.JLatexMathDrawable
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

private data class LatexCacheKey(
    val tex: String,
    val textSizePxRounded: Int,
    val colorArgb: Int
)

private object LatexDrawableCache {
    private val initialized = AtomicBoolean(false)
    private val cache = LruCache<LatexCacheKey, JLatexMathDrawable>(128)

    fun getCached(key: LatexCacheKey): JLatexMathDrawable? = synchronized(cache) {
        cache.get(key)
    }

    suspend fun getOrBuild(
        context: Context,
        tex: String,
        textSizePx: Float,
        colorArgb: Int
    ): JLatexMathDrawable? {
        val key = LatexCacheKey(tex, textSizePx.roundToInt(), colorArgb)
        getCached(key)?.let { return it }

        return withContext(Dispatchers.Default) {
            getCached(key)?.let { return@withContext it }
            ensureInitialized(context)
            val built = runCatching {
                JLatexMathDrawable.builder(tex)
                    .textSize(textSizePx)
                    .color(colorArgb)
                    .padding(4)
                    .build()
            }.getOrNull()
            if (built != null) {
                synchronized(cache) {
                    cache.put(key, built)
                }
            }
            built
        }
    }

    private fun ensureInitialized(context: Context) {
        if (initialized.compareAndSet(false, true)) {
            runCatching { JLatexMathAndroid.init(context.applicationContext) }
        }
    }
}

@Composable
internal fun DisplayMathBlock(
    tex: String,
    baseFontSizeSp: Int,
    color: Color,
    contentAlign: ContentAlign,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val formulaSp = (baseFontSizeSp * 1.15f).sp
    val textSizePx = with(density) { formulaSp.toPx() }
    val colorArgb = color.toArgb()
    val cacheKey = remember(tex, textSizePx, colorArgb) {
        LatexCacheKey(tex, textSizePx.roundToInt(), colorArgb)
    }
    val initialCached = remember(cacheKey) { LatexDrawableCache.getCached(cacheKey) }
    val drawable by produceState(initialValue = initialCached, key1 = context, key2 = cacheKey) {
        if (value == null) {
            value = LatexDrawableCache.getOrBuild(context, tex, textSizePx, colorArgb)
        }
    }

    val boxAlignment = when (contentAlign) {
        ContentAlign.LEFT -> Alignment.CenterStart
        ContentAlign.CENTER -> Alignment.Center
        ContentAlign.RIGHT -> Alignment.CenterEnd
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .horizontalScroll(rememberScrollState()),
        contentAlignment = boxAlignment
    ) {
        val resolvedDrawable = drawable
        if (resolvedDrawable != null) {
            val widthDp = with(density) { resolvedDrawable.intrinsicWidth.coerceAtLeast(1).toDp() }
            val heightDp = with(density) { resolvedDrawable.intrinsicHeight.coerceAtLeast(1).toDp() }
            Canvas(
                modifier = Modifier
                    .width(widthDp)
                    .height(heightDp)
            ) {
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    synchronized(resolvedDrawable) {
                        native.save()
                        resolvedDrawable.setBounds(
                            0,
                            0,
                            size.width.toInt().coerceAtLeast(1),
                            size.height.toInt().coerceAtLeast(1)
                        )
                        resolvedDrawable.draw(native)
                        native.restore()
                    }
                }
            }
        } else {
            Text(
                text = tex,
                color = color,
                fontFamily = FontFamily.Monospace,
                fontSize = baseFontSizeSp.sp,
                textAlign = when (contentAlign) {
                    ContentAlign.LEFT -> TextAlign.Start
                    ContentAlign.CENTER -> TextAlign.Center
                    ContentAlign.RIGHT -> TextAlign.End
                }
            )
        }
    }
}

@Composable
internal fun rememberInlineMathContent(
    formulas: Map<String, String>,
    fontSizeSp: Int,
    color: Color
): Map<String, InlineTextContent> {
    if (formulas.isEmpty()) return emptyMap()
    val context = LocalContext.current
    val density = LocalDensity.current
    val textSizePx = with(density) { fontSizeSp.sp.toPx() }
    val textSizeRounded = textSizePx.roundToInt()
    val colorArgb = color.toArgb()

    val initialMap = remember(formulas, textSizeRounded, colorArgb) {
        formulas.mapValues { (_, tex) ->
            LatexDrawableCache.getCached(LatexCacheKey(tex, textSizeRounded, colorArgb))
        }
    }

    val resolvedDrawables by produceState(
        initialValue = initialMap,
        key1 = context,
        key2 = formulas,
        key3 = textSizeRounded to colorArgb
    ) {
        if (value.values.any { it == null }) {
            val loaded = LinkedHashMap<String, JLatexMathDrawable?>(formulas.size)
            for ((id, tex) in formulas) {
                loaded[id] = value[id] ?: LatexDrawableCache.getOrBuild(context, tex, textSizePx, colorArgb)
            }
            value = loaded
        }
    }

    return remember(formulas, resolvedDrawables, textSizeRounded, colorArgb, fontSizeSp) {
        formulas.mapValues { (id, tex) ->
            val drawable = resolvedDrawables[id]
            if (drawable != null) {
                val wSp = with(density) { drawable.intrinsicWidth.coerceAtLeast(1).toSp() }
                val hSp = with(density) { drawable.intrinsicHeight.coerceAtLeast(1).toSp() }
                InlineTextContent(
                    placeholder = Placeholder(
                        width = wSp,
                        height = hSp,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                    )
                ) {
                    Canvas(
                        modifier = Modifier
                            .width(with(density) { wSp.toDp() })
                            .height(with(density) { hSp.toDp() })
                    ) {
                        drawIntoCanvas { canvas ->
                            val native = canvas.nativeCanvas
                            synchronized(drawable) {
                                native.save()
                                drawable.setBounds(
                                    0,
                                    0,
                                    size.width.toInt().coerceAtLeast(1),
                                    size.height.toInt().coerceAtLeast(1)
                                )
                                drawable.draw(native)
                                native.restore()
                            }
                        }
                    }
                }
            } else {
                val fallbackWidthSp = (tex.length.coerceAtLeast(1) * fontSizeSp * 0.62f).sp
                val fallbackHeightSp = (fontSizeSp * 1.25f).sp
                InlineTextContent(
                    placeholder = Placeholder(
                        width = fallbackWidthSp,
                        height = fallbackHeightSp,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                    )
                ) {
                    Text(
                        text = tex,
                        color = color,
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSizeSp.sp
                    )
                }
            }
        }
    }
}
