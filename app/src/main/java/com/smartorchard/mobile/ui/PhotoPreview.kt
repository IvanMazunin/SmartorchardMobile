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
import androidx.compose.ui.unit.dp
import com.smartorchard.mobile.R
import com.smartorchard.mobile.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun PhotoPreview(path: String?, sample: Boolean) {
    val bitmap by produceState<Bitmap?>(null, path) {
        value = if (path == null) null else withContext(Dispatchers.IO) { runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(path, bounds)
            val options = BitmapFactory.Options().apply { inSampleSize = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 1024) }
            BitmapFactory.decodeFile(path, options)
        }.getOrNull() }
    }
    Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFEAF0E1)), contentAlignment = Alignment.Center) {
        when {
            sample -> Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                val leaf = Path().apply { moveTo(w * .48f, h * .88f); cubicTo(w * .22f, h * .65f, w * .38f, h * .23f, w * .65f, h * .07f); cubicTo(w * .85f, h * .63f, w * .65f, h * .91f, w * .48f, h * .88f) }
                drawPath(leaf, Color(0xFF64874B)); drawLine(Color(0xFFC9D3A4), Offset(w * .48f, h * .9f), Offset(w * .64f, h * .1f), 3f)
                listOf(Offset(w * .52f, h * .35f), Offset(w * .62f, h * .6f), Offset(w * .47f, h * .68f)).forEach { drawCircle(Color(0xFFAE8A4D), 8.dp.toPx(), it) }
            }
            bitmap != null -> Image(bitmap!!.asImageBitmap(), "Фотография осмотра", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) { OrchardIcon(R.drawable.ic_camera, Modifier.size(34.dp), OrchardMuted); Text(if (path == null) "Добавьте фотографию дерева" else "Фотография недоступна", style = MaterialTheme.typography.bodyMedium, color = OrchardMuted) }
        }
        if (sample) Text("Пример фотографии", Modifier.align(Alignment.BottomCenter).padding(9.dp), style = MaterialTheme.typography.labelSmall, color = OrchardMuted)
    }
}
