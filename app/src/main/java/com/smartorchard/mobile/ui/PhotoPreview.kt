package com.smartorchard.mobile.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smartorchard.mobile.R
import com.smartorchard.mobile.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PhotoPreview(
    path: String?,
    sample: Boolean
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = path) {
        value = if (path == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val bounds = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeFile(path, bounds)
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 1024)
                    }
                    BitmapFactory.decodeFile(path, options)
                }.getOrNull()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFEAF0E1)),
        contentAlignment = Alignment.Center
    ) {
        val loadedBitmap = bitmap
        when {
            sample -> Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val leaf = Path().apply {
                    moveTo(w * .48f, h * .88f)
                    cubicTo(w * .22f, h * .65f, w * .38f, h * .23f, w * .65f, h * .07f)
                    cubicTo(w * .85f, h * .63f, w * .65f, h * .91f, w * .48f, h * .88f)
                }
                drawPath(leaf, Color(0xFF64874B))
                drawLine(
                    color = Color(0xFFC9D3A4),
                    start = Offset(w * .48f, h * .9f),
                    end = Offset(w * .64f, h * .1f),
                    strokeWidth = 3f
                )
                val spots = listOf(
                    Offset(w * .52f, h * .35f),
                    Offset(w * .62f, h * .6f),
                    Offset(w * .47f, h * .68f)
                )
                spots.forEach { center ->
                    drawCircle(
                        color = Color(0xFFAE8A4D),
                        radius = 8.dp.toPx(),
                        center = center
                    )
                }
            }

            loadedBitmap != null -> Image(
                bitmap = loadedBitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.photo_inspection_desc),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            else -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                OrchardIcon(
                    id = R.drawable.ic_camera,
                    modifier = Modifier.size(34.dp),
                    tint = OrchardMuted
                )
                Text(
                    text = if (path == null) {
                        stringResource(R.string.photo_add_prompt)
                    } else {
                        stringResource(R.string.photo_unavailable)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = OrchardMuted
                )
            }
        }

        if (sample) {
            Text(
                text = stringResource(R.string.photo_sample_caption),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(9.dp),
                style = MaterialTheme.typography.labelSmall,
                color = OrchardMuted
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PhotoPreviewSamplePreview() {
    SmartOrchardTheme {
        Box(Modifier.padding(16.dp)) {
            PhotoPreview(path = null, sample = true)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PhotoPreviewEmptyPreview() {
    SmartOrchardTheme {
        Box(Modifier.padding(16.dp)) {
            PhotoPreview(path = null, sample = false)
        }
    }
}
