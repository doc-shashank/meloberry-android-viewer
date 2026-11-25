package com.meloberry.android.feature.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meloberry.android.core.model.ArticleBlockKind
import com.meloberry.android.core.model.ArticleBlockModel
import com.meloberry.android.core.model.CalloutTone
import com.meloberry.android.core.model.ContentAlign
import com.meloberry.android.core.model.ResolvedSlideLayout
import com.meloberry.android.core.model.SlideUiModel
import com.meloberry.android.core.model.TableBlockPayload
import com.meloberry.android.core.ui.components.MeloberryAssetImage
import com.meloberry.android.core.ui.markdown.MarkdownText
import com.meloberry.android.core.ui.theme.LocalMeloberryColors

@Composable
internal fun ArticleSlideCard(
    slide: SlideUiModel.ArticleSlide,
    modifier: Modifier = Modifier
) {
    val layout = slide.layout
    val isCompact = LocalConfiguration.current.screenWidthDp < 600
    val effectivePaddingDp = if (isCompact) layout.cardPaddingDp.coerceAtMost(24) else layout.cardPaddingDp
    val tabLabel = slide.title?.takeIf { it.isNotBlank() }?.let { "ARTICLE • $it" } ?: "ARTICLE"

    FramedSlideCard(
        tabLabel = tabLabel,
        modifier = modifier,
        maxWidthDp = layout.cardMaxWidthDp,
        contentPaddingDp = effectivePaddingDp,
        contentSpacingDp = layout.blockSpacingDp
    ) {
        slide.blocks.forEach { block ->
            when (block.kind) {
                ArticleBlockKind.TEXT -> {
                    if (!block.textContent.isNullOrBlank()) {
                        MarkdownText(
                            markdown = block.textContent.orEmpty(),
                            baseFontSizeSp = layout.bodyFontSizeSp,
                            promptFontSizeSp = layout.promptFontSizeSp,
                            contentAlign = layout.contentAlign
                        )
                    }
                }
                ArticleBlockKind.IMAGE -> {
                    ArticleImageBlock(block = block, layout = layout)
                }
                ArticleBlockKind.CALLOUT -> {
                    CalloutBlockCard(block = block, layout = layout)
                }
                ArticleBlockKind.TABLE -> {
                    block.tablePayload?.let { table ->
                        InteractiveTableBlockCard(
                            blockId = block.id,
                            table = table,
                            baseFontSizeSp = layout.bodyFontSizeSp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArticleImageBlock(
    block: ArticleBlockModel,
    layout: ResolvedSlideLayout
) {
    val palette = LocalMeloberryColors.current
    if (block.imageFilePath.isNullOrBlank()) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MeloberryAssetImage(
            filePath = block.imageFilePath.orEmpty(),
            contentDescription = block.alt ?: "Article diagram",
            maxHeightDp = layout.imageMaxHeightDp
        )
        if (!block.caption.isNullOrBlank()) {
            CompositionLocalProvider(
                LocalMeloberryColors provides palette.copy(text = palette.muted)
            ) {
                MarkdownText(
                    markdown = block.caption.orEmpty(),
                    baseFontSizeSp = (layout.bodyFontSizeSp - 2).coerceAtLeast(12),
                    contentAlign = ContentAlign.CENTER,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
internal fun CalloutBlockCard(
    block: ArticleBlockModel,
    layout: ResolvedSlideLayout,
    modifier: Modifier = Modifier
) {
    val palette = LocalMeloberryColors.current
    val tone = block.calloutTone ?: CalloutTone.PEARL
    val style = when (tone) {
        CalloutTone.PEARL -> CalloutVisualStyle(palette.pearlAccent, palette.pearlBg, Icons.Default.AutoAwesome, "Pearl")
        CalloutTone.CLINICAL -> CalloutVisualStyle(palette.clinicalAccent, palette.clinicalBg, Icons.Default.MedicalServices, "Clinical Correlation")
        CalloutTone.WARNING -> CalloutVisualStyle(palette.warningAccent, palette.warningBg, Icons.Default.WarningAmber, "Warning")
    }
    val accentColor = style.accentColor
    val bgColor = style.bgColor
    val icon = style.icon
    val defaultTitle = style.defaultTitle
    val headerTitle = block.calloutTitle?.takeIf { it.isNotBlank() } ?: defaultTitle
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(bgColor)
            .border(1.dp, accentColor.copy(alpha = 0.35f), shape)
    ) {
        Box(
            modifier = Modifier
                .width(5.dp)
                .fillMaxHeight()
                .background(accentColor)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = headerTitle,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = headerTitle,
                    color = accentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.3.sp
                )
            }
            if (!block.textContent.isNullOrBlank()) {
                MarkdownText(
                    markdown = block.textContent.orEmpty(),
                    baseFontSizeSp = (layout.bodyFontSizeSp - 1).coerceAtLeast(13),
                    contentAlign = layout.contentAlign
                )
            }
        }
    }
}

private data class CalloutVisualStyle(
    val accentColor: Color,
    val bgColor: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val defaultTitle: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InteractiveTableBlockCard(
    blockId: String,
    table: TableBlockPayload,
    baseFontSizeSp: Int,
    modifier: Modifier = Modifier
) {
    val palette = LocalMeloberryColors.current
    val colCount = table.headers.size.coerceAtLeast(1)
    val cellFontSize = (baseFontSizeSp - 1).coerceAtLeast(12)
    val hideableZeroBased = remember(table.hideableCols, colCount) {
        table.hideableCols.map { it - 1 }.filter { it in 0 until colCount }.toSet()
    }
    var hiddenCols by remember(blockId, hideableZeroBased) { mutableStateOf(hideableZeroBased) }
    var peekedCells by remember(blockId) { mutableStateOf(emptySet<Pair<Int, Int>>()) }

    fun toggleColumnHidden(colIdx: Int) {
        val wasHidden = colIdx in hiddenCols
        hiddenCols = if (wasHidden) hiddenCols - colIdx else hiddenCols + colIdx
        if (!wasHidden) {
            peekedCells = peekedCells.filterNot { it.second == colIdx }.toSet()
        }
    }

    val colWidths: List<Dp> = remember(table, cellFontSize) {
        List(colCount) { col ->
            val maxChars = maxOf(
                table.headers.getOrNull(col)?.length ?: 0,
                table.rows.maxOfOrNull { it.getOrNull(col)?.length ?: 0 } ?: 0
            )
            (maxChars * cellFontSize * 0.62f + 30f).dp.coerceIn(88.dp, 260.dp)
        }
    }
    val useStickyFirstCol = table.stickyFirstCol && colCount > 1
    val sharedHScroll = rememberScrollState()
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!table.title.isNullOrBlank()) {
            Text(
                text = table.title.orEmpty(),
                color = palette.text,
                fontSize = (baseFontSizeSp + 1).sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (!table.caption.isNullOrBlank()) {
            CompositionLocalProvider(
                LocalMeloberryColors provides palette.copy(text = palette.muted)
            ) {
                MarkdownText(
                    markdown = table.caption.orEmpty(),
                    baseFontSizeSp = (baseFontSizeSp - 2).coerceAtLeast(12)
                )
            }
        }

        if (hideableZeroBased.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                hideableZeroBased.sorted().forEach { colIdx ->
                    val isHidden = colIdx in hiddenCols
                    val headerLabel = table.headers.getOrNull(colIdx)?.take(24).orEmpty().ifBlank { "Col ${colIdx + 1}" }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (isHidden) palette.accentSoft else palette.background,
                        border = BorderStroke(1.dp, if (isHidden) palette.accent else palette.border),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { toggleColumnHidden(colIdx) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = if (isHidden) palette.accent else palette.muted,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isHidden) "Show $headerLabel" else "Hide $headerLabel",
                                color = if (isHidden) palette.accent else palette.text,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, palette.border, shape)
        ) {
            if (useStickyFirstCol) {
                // Header row with sticky column 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .background(palette.accentSoft)
                ) {
                    TableHeaderCell(
                        text = table.headers[0],
                        alignment = table.alignments.getOrNull(0),
                        width = colWidths[0],
                        fontSizeSp = cellFontSize,
                        isHideable = 0 in hideableZeroBased,
                        isHidden = 0 in hiddenCols,
                        onToggleHide = { toggleColumnHidden(0) }
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(palette.border)
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(sharedHScroll)
                    ) {
                        for (c in 1 until colCount) {
                            TableHeaderCell(
                                text = table.headers[c],
                                alignment = table.alignments.getOrNull(c),
                                width = colWidths[c],
                                fontSizeSp = cellFontSize,
                                isHideable = c in hideableZeroBased,
                                isHidden = c in hiddenCols,
                                onToggleHide = { toggleColumnHidden(c) }
                            )
                        }
                    }
                }
                table.rows.forEachIndexed { rowIdx, row ->
                    HorizontalDivider(color = palette.border)
                    val rowBg = if (rowIdx % 2 == 1) palette.background else palette.surface
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .background(rowBg)
                    ) {
                        val coord0 = rowIdx to 0
                        val col0Hidden = 0 in hiddenCols
                        TableBodyCell(
                            text = row.getOrNull(0).orEmpty(),
                            alignment = table.alignments.getOrNull(0),
                            width = colWidths[0],
                            fontSizeSp = cellFontSize,
                            isInHiddenColumn = col0Hidden,
                            isMasked = col0Hidden && (coord0 !in peekedCells),
                            onTogglePeek = {
                                peekedCells = if (coord0 in peekedCells) peekedCells - coord0 else peekedCells + coord0
                            },
                            isStickyCol = true
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(palette.border)
                        )
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(sharedHScroll)
                        ) {
                            for (c in 1 until colCount) {
                                val coord = rowIdx to c
                                val colHidden = c in hiddenCols
                                TableBodyCell(
                                    text = row.getOrNull(c).orEmpty(),
                                    alignment = table.alignments.getOrNull(c),
                                    width = colWidths[c],
                                    fontSizeSp = cellFontSize,
                                    isInHiddenColumn = colHidden,
                                    isMasked = colHidden && (coord !in peekedCells),
                                    onTogglePeek = {
                                        peekedCells = if (coord in peekedCells) peekedCells - coord else peekedCells + coord
                                    },
                                    isStickyCol = false
                                )
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(sharedHScroll)
                ) {
                    Row(
                        modifier = Modifier
                            .height(IntrinsicSize.Min)
                            .background(palette.accentSoft)
                    ) {
                        for (c in 0 until colCount) {
                            TableHeaderCell(
                                text = table.headers[c],
                                alignment = table.alignments.getOrNull(c),
                                width = colWidths[c],
                                fontSizeSp = cellFontSize,
                                isHideable = c in hideableZeroBased,
                                isHidden = c in hiddenCols,
                                onToggleHide = { toggleColumnHidden(c) }
                            )
                        }
                    }
                    table.rows.forEachIndexed { rowIdx, row ->
                        HorizontalDivider(color = palette.border)
                        val rowBg = if (rowIdx % 2 == 1) palette.background else palette.surface
                        Row(
                            modifier = Modifier
                                .height(IntrinsicSize.Min)
                                .background(rowBg)
                        ) {
                            for (c in 0 until colCount) {
                                val coord = rowIdx to c
                                val colHidden = c in hiddenCols
                                TableBodyCell(
                                    text = row.getOrNull(c).orEmpty(),
                                    alignment = table.alignments.getOrNull(c),
                                    width = colWidths[c],
                                    fontSizeSp = cellFontSize,
                                    isInHiddenColumn = colHidden,
                                    isMasked = colHidden && (coord !in peekedCells),
                                    onTogglePeek = {
                                        peekedCells = if (coord in peekedCells) peekedCells - coord else peekedCells + coord
                                    },
                                    isStickyCol = false
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!table.footnote.isNullOrBlank()) {
            Text(
                text = table.footnote.orEmpty(),
                color = palette.muted,
                fontSize = 12.sp,
                fontStyle = FontStyle.Italic,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun TableHeaderCell(
    text: String,
    alignment: String?,
    width: Dp,
    fontSizeSp: Int,
    isHideable: Boolean,
    isHidden: Boolean,
    onToggleHide: () -> Unit
) {
    val palette = LocalMeloberryColors.current
    val contentAlign = when (alignment?.lowercase()) {
        "center" -> ContentAlign.CENTER
        "right" -> ContentAlign.RIGHT
        else -> ContentAlign.LEFT
    }
    Column(
        modifier = Modifier
            .width(width)
            .let { if (isHideable) it.clickable(onClick = onToggleHide) else it }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CompositionLocalProvider(
            LocalMeloberryColors provides palette.copy(text = palette.accent)
        ) {
            MarkdownText(
                markdown = text,
                baseFontSizeSp = fontSizeSp,
                contentAlign = contentAlign,
                isBoldByDefault = true
            )
        }
        if (isHideable) {
            Text(
                text = if (isHidden) "Hidden • Tap" else "Visible • Tap",
                color = palette.accent.copy(alpha = 0.8f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun TableBodyCell(
    text: String,
    alignment: String?,
    width: Dp,
    fontSizeSp: Int,
    isInHiddenColumn: Boolean,
    isMasked: Boolean,
    onTogglePeek: () -> Unit,
    isStickyCol: Boolean
) {
    val palette = LocalMeloberryColors.current
    val contentAlign = when (alignment?.lowercase()) {
        "center" -> ContentAlign.CENTER
        "right" -> ContentAlign.RIGHT
        else -> ContentAlign.LEFT
    }
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .let { if (isInHiddenColumn) it.clickable(onClick = onTogglePeek) else it }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        contentAlignment = when (contentAlign) {
            ContentAlign.CENTER -> Alignment.Center
            ContentAlign.RIGHT -> Alignment.CenterEnd
            ContentAlign.LEFT -> Alignment.CenterStart
        }
    ) {
        if (isMasked) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = palette.accentSoft,
                border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.4f))
            ) {
                Text(
                    text = "Tap to peek",
                    color = palette.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        } else {
            MarkdownText(
                markdown = text,
                baseFontSizeSp = fontSizeSp,
                contentAlign = contentAlign,
                isBoldByDefault = isStickyCol
            )
        }
    }
}
