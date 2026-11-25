package com.meloberry.android.core.ui.components

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.meloberry.android.core.ui.theme.LocalMeloberryColors
import java.io.File

object SharedSvgImageLoader {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: android.content.Context): ImageLoader {
        instance?.let { return it }
        return synchronized(this) {
            instance ?: ImageLoader.Builder(context.applicationContext)
                .components {
                    add(SvgDecoder.Factory())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        add(ImageDecoderDecoder.Factory())
                    } else {
                        add(GifDecoder.Factory())
                    }
                }
                .build()
                .also { instance = it }
        }
    }
}

@Composable
fun MeloberryAssetImage(
    filePath: String,
    contentDescription: String?,
    maxHeightDp: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalMeloberryColors.current
    val imageLoader = remember(context) { SharedSvgImageLoader.get(context) }
    val assetFile = remember(filePath) { File(filePath) }
    val model = remember(filePath, context) {
        ImageRequest.Builder(context)
            .data(assetFile)
            .crossfade(true)
            .build()
    }
    var hasError by remember(filePath) { mutableStateOf(!assetFile.isFile) }
    var isZoomOpen by remember(filePath) { mutableStateOf(false) }

    if (hasError) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = palette.background,
            border = BorderStroke(1.dp, palette.border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BrokenImage,
                    contentDescription = contentDescription ?: "Image unavailable",
                    tint = palette.muted,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Image unavailable",
                    color = palette.muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeightDp.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { isZoomOpen = true }
    ) {
        AsyncImage(
            model = model,
            imageLoader = imageLoader,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            onError = { hasError = true },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeightDp.dp)
        )

        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.55f)
        ) {
            Icon(
                imageVector = Icons.Default.ZoomIn,
                contentDescription = "Zoom image",
                tint = Color.White,
                modifier = Modifier
                    .padding(6.dp)
                    .size(16.dp)
            )
        }
    }

    if (isZoomOpen) {
        ZoomableImageDialog(
            model = model,
            imageLoader = imageLoader,
            contentDescription = contentDescription,
            onDismiss = { isZoomOpen = false }
        )
    }
}

@Composable
private fun ZoomableImageDialog(
    model: ImageRequest,
    imageLoader: ImageLoader,
    contentDescription: String?,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    fun clampOffset(candidate: Offset, targetScale: Float): Offset {
        if (targetScale <= 1f || containerSize == IntSize.Zero) return Offset.Zero
        val maxX = ((containerSize.width * (targetScale - 1f)) / 2f).coerceAtLeast(0f)
        val maxY = ((containerSize.height * (targetScale - 1f)) / 2f).coerceAtLeast(0f)
        return Offset(
            x = candidate.x.coerceIn(-maxX, maxX),
            y = candidate.y.coerceIn(-maxY, maxY)
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .onSizeChanged { newSize ->
                        containerSize = newSize
                        offset = clampOffset(offset, scale)
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val nextScale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (nextScale > 1f) {
                                clampOffset(Offset(offset.x + pan.x, offset.y + pan.y), nextScale)
                            } else {
                                Offset.Zero
                            }
                            scale = nextScale
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { tapOffset ->
                                if (scale > 1.05f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    val targetScale = 2.5f
                                    val centerX = containerSize.width / 2f
                                    val centerY = containerSize.height / 2f
                                    val reanchored = Offset(
                                        x = (centerX - tapOffset.x) * (targetScale - 1f),
                                        y = (centerY - tapOffset.y) * (targetScale - 1f)
                                    )
                                    scale = targetScale
                                    offset = clampOffset(reanchored, targetScale)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = model,
                    imageLoader = imageLoader,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = "${(scale * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (scale > 1.01f) {
                        TextButton(
                            onClick = {
                                scale = 1f
                                offset = Offset.Zero
                            }
                        ) {
                            Text("Reset", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close zoom view",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
