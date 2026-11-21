package com.meloberry.android.feature.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meloberry.android.core.ui.theme.LocalMeloberryColors

@Composable
internal fun SlideTypeHeaderTab(
    label: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalMeloberryColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp),
        color = palette.accentSoft,
        border = BorderStroke(1.dp, palette.border)
    ) {
        Text(
            text = label,
            color = palette.accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
        )
    }
}

@Composable
internal fun FramedSlideCard(
    tabLabel: String,
    modifier: Modifier = Modifier,
    maxWidthDp: Int = 840,
    contentPaddingDp: Int = 24,
    topContentPaddingDp: Int = 14,
    bottomContentPaddingDp: Int = contentPaddingDp,
    contentSpacingDp: Int = 16,
    minHeightDp: Int = 0,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalMeloberryColors.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = maxWidthDp.dp)
            .let { if (minHeightDp > 0) it.defaultMinSize(minHeight = minHeightDp.dp) else it },
        shape = RoundedCornerShape(18.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border),
        shadowElevation = 3.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SlideTypeHeaderTab(
                label = tabLabel,
                modifier = Modifier.padding(start = contentPaddingDp.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = contentPaddingDp.dp,
                        end = contentPaddingDp.dp,
                        top = topContentPaddingDp.dp,
                        bottom = bottomContentPaddingDp.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(contentSpacingDp.dp),
                content = content
            )
        }
    }
}
