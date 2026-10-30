package com.smartorchard.mobile.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.FileOutputStream

fun qrBitmap(payload: String, size: Int = 600): Bitmap {
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 4))
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) Color.BLACK else Color.WHITE }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

fun printQrLabel(context: Context, garden: String, code: String, row: Int, place: Int, payload: String, copies: Int) {
    val bitmap = qrBitmap(payload)
    val adapter = object : PrintDocumentAdapter() {
        override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?, signal: CancellationSignal,
            callback: LayoutResultCallback, extras: Bundle?) {
            if (signal.isCanceled) { callback.onLayoutCancelled(); return }
            callback.onLayoutFinished(PrintDocumentInfo.Builder("SmartOrchard-$code.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(1).build(), true)
        }
        override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, signal: CancellationSignal, callback: WriteResultCallback) {
            if (signal.isCanceled) { callback.onWriteCancelled(); return }
            if (pages.none { 0 in it.start..it.end }) { callback.onWriteFinished(emptyArray()); return }
            val document = PdfDocument()
            try {
                val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textAlign = Paint.Align.CENTER }
                repeat(copies.coerceIn(1, 12)) { index ->
                    val x = 32f + index % 3 * 178f; val y = 28f + index / 3 * 194f
                    paint.textSize = 10f; page.canvas.drawText(garden.take(25), x + 80, y + 12, paint)
                    page.canvas.drawBitmap(bitmap, null, RectF(x + 18, y + 17, x + 142, y + 141), paint)
                    paint.textSize = 12f; page.canvas.drawText(code, x + 80, y + 155, paint)
                    paint.textSize = 9f; page.canvas.drawText("Ряд $row · место $place", x + 80, y + 170, paint)
                }
                document.finishPage(page)
                if (signal.isCanceled) { callback.onWriteCancelled(); return }
                FileOutputStream(destination.fileDescriptor).use { document.writeTo(it) }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (error: Exception) { callback.onWriteFailed(error.message ?: "Не удалось создать лист.") }
            finally { document.close() }
        }
    }
    (context.getSystemService(Context.PRINT_SERVICE) as PrintManager).print("SmartOrchard $code", adapter, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
}
