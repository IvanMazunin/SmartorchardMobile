package com.smartorchard.mobile.services

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.TextPaint
import android.text.TextUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.posprinter.*
import javax.inject.Inject
import javax.inject.Singleton

data class PairedPrinter(val name: String, val address: String)
data class PrinterStatus(
    val connected: PairedPrinter? = null,
    val connecting: PairedPrinter? = null,
    val error: String? = null
)

data class PlantLabel(val garden: String, val code: String, val row: Int, val place: Int, val payload: String)

interface PlantPrinter {
    val status: StateFlow<PrinterStatus>
    fun pairedDevices(): List<PairedPrinter>
    fun connect(device: PairedPrinter)
    fun disconnect()
    fun print(label: PlantLabel, copies: Int)
}

@Singleton
@SuppressLint("MissingPermission") // The printer screen requests Nearby devices before listing and connecting.
class BluetoothPlantPrinter @Inject constructor(@ApplicationContext private val context: Context) : PlantPrinter {
    private val state = MutableStateFlow(PrinterStatus())
    override val status = state.asStateFlow()
    private var session: IDeviceConnection? = null

    init { POSConnect.init(context) }

    override fun pairedDevices(): List<PairedPrinter> {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: error("На устройстве нет Bluetooth.")
        check(adapter.isEnabled) { "Включите Bluetooth в настройках телефона." }
        return adapter.bondedDevices.map { PairedPrinter(it.name ?: "Bluetooth-устройство", it.address) }
            .sortedWith(compareBy(PairedPrinter::name, PairedPrinter::address))
    }

    @Synchronized
    override fun connect(device: PairedPrinter) {
        disconnect()
        val next = POSConnect.createDevice(POSConnect.DEVICE_TYPE_BLUETOOTH)
        session = next
        state.value = PrinterStatus(connecting = device)
        try {
            next.connect(device.address, IConnectListener { code, _, _ ->
                synchronized(this) {
                    if (session === next) when (code) {
                        POSConnect.CONNECT_SUCCESS -> state.value = PrinterStatus(connected = device)
                        POSConnect.CONNECT_FAIL, POSConnect.CONNECT_INTERRUPT, POSConnect.BLUETOOTH_INTERRUPT -> {
                            disconnect()
                            state.value = PrinterStatus(error = "Нет связи с принтером ${device.name}.")
                        }
                        POSConnect.SEND_FAIL -> state.value = state.value.copy(error = "Ошибка отправки. Проверьте бумагу и связь с принтером.")
                    }
                }
            })
        } catch (e: Exception) {
            disconnect()
            throw e
        }
    }

    @Synchronized
    override fun disconnect() {
        val old = session
        session = null
        state.value = PrinterStatus()
        old?.close()
    }

    @Synchronized
    override fun print(label: PlantLabel, copies: Int) {
        require(copies in 1..12) { "Укажите от 1 до 12 копий." }
        val connection = session
        check(connection != null && state.value.connected != null && connection.isConnect) { "Подключите принтер." }
        val bitmap = renderPlantLabel(label)
        state.value = state.value.copy(error = null)
        TSPLPrinter(connection).sizeMm(60.0, 40.0).gapInch(0.0, 0.0)
            .offsetInch(0.0).direction(TSPLConst.DIRECTION_FORWARD).reference(0, 0)
            .cls().bitmap(0, 0, TSPLConst.BMP_MODE_OVERWRITE, bitmap.width, bitmap).print(copies)
    }
}

// 60 × 40 mm at 8 dots/mm (203 dpi). Keep the QR module grid and its quiet zone intact.
internal fun renderPlantLabel(label: PlantLabel): Bitmap {
    val sheet = Bitmap.createBitmap(480, 320, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(sheet)
    canvas.drawColor(Color.WHITE)
    val ink = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textAlign = Paint.Align.CENTER }
    fun line(text: String, baseline: Float, size: Float) {
        ink.textSize = size
        val singleLine = text.replace(Regex("\\s+"), " ").trim()
        val fitted = TextUtils.ellipsize(singleLine, ink, 452f, TextUtils.TruncateAt.END)
        canvas.drawText(fitted.toString(), 240f, baseline, ink)
    }
    line(label.garden, 29f, 23f)
    val qr = qrBitmap(label.payload, 208)
    canvas.drawBitmap(qr, null, Rect(136, 36, 344, 244), Paint().apply { isFilterBitmap = false })
    line(label.code, 270f, 25f)
    line("Ряд ${label.row} · место ${label.place}", 301f, 20f)
    return sheet
}
