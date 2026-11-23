package com.meloberry.android.core.ui.markdown

import android.content.Context
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
import ru.noties.jlatexmath.JLatexMathAndroid
import ru.noties.jlatexmath.JLatexMathDrawable
import java.util.concurrent.atomic.AtomicBoolean

private object LatexEngineInit {
    private val initialized = AtomicBoolean(false)

    fun ensureInitialized(context: Context) {
        if (initialized.compareAndSet(false, true)) {
            runCatching { JLatexMathAndroid.init(context.applicationContext) }
        }
    }
}

private fun buildLatexDrawable(
    context: Context,
    tex: String,
    textSizePx: Float,
    colorArgb: Int
): JLatexMathDrawable? {
    LatexEngineInit.ensureInitialized(context)
    return runCatching {
        JLatexMathDrawable.builder(tex)
            .textSize(textSizePx)
            .color(colorArgb)
            .padding(4)
            .build()
    }.getOrNull()
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

    val drawable = remember(context, tex, textSizePx, colorArgb) {
        buildLatexDrawable(context, tex, textSizePx, colorArgb)
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
        if (drawable != null) {
            val widthDp = with(density) { drawable.intrinsicWidth.coerceAtLeast(1).toDp() }
            val heightDp = with(density) { drawable.intrinsicHeight.coerceAtLeast(1).toDp() }
            Canvas(
                modifier = Modifier
                    .width(widthDp)
                    .height(heightDp)
            ) {
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    native.save()
                    drawable.setBounds(0, 0, size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))
                    drawable.draw(native)
                    native.restore()
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
    val colorArgb = color.toArgb()

    return remember(context, formulas, textSizePx, colorArgb) {
        formulas.mapValues { (_, tex) ->
            val drawable = buildLatexDrawable(context, tex, textSizePx, colorArgb)
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
                    Canvas(modifier = Modifier.width(with(density) { wSp.toDp() }).height(with(density) { hSp.toDp() })) {
                        drawIntoCanvas { canvas ->
                            val native = canvas.nativeCanvas
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
