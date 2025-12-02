package ar.edu.unrn.echolens.ble

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

val NUS_SERVICE = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
val NUS_TX_CHAR = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E") // notify from ESP32
val NUS_RX_CHAR = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E") // write to ESP32
val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

class BleClient(private val ctx: Context) {

    // audio entrante por BLE
    val audioFlow = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)

    // debug/otros msgs si el ESP manda texto
    val textFlow = MutableStateFlow("")

    private val adapter: BluetoothAdapter by lazy {
        (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private val scanner: BluetoothLeScanner by lazy { adapter.bluetoothLeScanner }
    private var gatt: BluetoothGatt? = null

    private var rxChar: BluetoothGattCharacteristic? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device ?: return
            stopScan()
            gatt = device.connectGatt(ctx, false, gattCallback)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(NUS_SERVICE) ?: return

            val tx = service.getCharacteristic(NUS_TX_CHAR) ?: return
            rxChar = service.getCharacteristic(NUS_RX_CHAR)

            gatt.setCharacteristicNotification(tx, true)
            val cccd = tx.getDescriptor(CCCD)
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(cccd)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == NUS_TX_CHAR) {
                val bytes = characteristic.value ?: return

                // si tu protocolo distingue texto vs audio, parsealo acá.
                // por defecto lo tratamos como audio crudo:
                audioFlow.tryEmit(bytes)

                // opcional: para debug si viene texto UTF-8:
                // textFlow.value = bytes.toString(Charsets.UTF_8)
            }
        }
    }

    fun startScan() {
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(NUS_SERVICE)).build()
        val settings = ScanSettings.Builder().build()
        scanner.startScan(listOf(filter), settings, scanCallback)
    }

    fun stopScan() { scanner.stopScan(scanCallback) }

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        rxChar = null
    }

    fun sendTextToEsp(text: String) {
        val rx = rxChar ?: return
        rx.value = text.toByteArray(Charsets.UTF_8)
        gatt?.writeCharacteristic(rx)
    }
}
