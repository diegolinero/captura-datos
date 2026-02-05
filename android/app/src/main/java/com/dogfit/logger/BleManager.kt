package com.dogfit.logger

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.nio.charset.StandardCharsets
import java.util.UUID

class BleManager(
    private val context: Context,
    private val adapter: BluetoothAdapter?,
    private val listener: BleListener
) {
    interface BleListener {
        fun onDeviceConnected(name: String?)
        fun onDeviceDisconnected()
        fun onLineReceived(line: String)
        fun onStatusMessage(message: String)
    }

    private val serviceUuid = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    private val rxUuid = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
    private val txUuid = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    private val clientConfigUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val handler = Handler(Looper.getMainLooper())
    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private val buffer = StringBuilder()

    private var scanning = false

    fun start() {
        if (adapter == null || !adapter.isEnabled) {
            listener.onStatusMessage("Bluetooth disabled")
            return
        }
        scanner = adapter.bluetoothLeScanner
        startScan()
    }

    fun stop() {
        stopScan()
        gatt?.close()
        gatt = null
    }

    fun isConnected(): Boolean = gatt != null && txCharacteristic != null

    fun sendCommand(command: String) {
        val rx = rxCharacteristic ?: return
        rx.value = command.toByteArray(StandardCharsets.UTF_8)
        gatt?.writeCharacteristic(rx)
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        if (scanning) return
        scanning = true
        listener.onStatusMessage("Scanning...")
        scanner?.startScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (!scanning) return
        scanning = false
        scanner?.stopScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: return
            if (name.contains("DOGFIT-IMU", ignoreCase = true)) {
                stopScan()
                connect(result.device)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice) {
        listener.onStatusMessage("Connecting to ${device.name}")
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                cleanup()
                listener.onDeviceDisconnected()
                scheduleReconnect()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                scheduleReconnect()
                return
            }
            val service: BluetoothGattService = gatt.getService(serviceUuid) ?: return
            rxCharacteristic = service.getCharacteristic(rxUuid)
            txCharacteristic = service.getCharacteristic(txUuid)
            if (txCharacteristic != null) {
                enableNotifications(gatt, txCharacteristic!!)
                listener.onDeviceConnected(gatt.device.name)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == txUuid) {
                val data = characteristic.value ?: return
                val text = String(data, StandardCharsets.UTF_8)
                buffer.append(text)
                while (true) {
                    val newlineIndex = buffer.indexOf("\n")
                    if (newlineIndex == -1) break
                    val line = buffer.substring(0, newlineIndex).trim()
                    buffer.delete(0, newlineIndex + 1)
                    if (line.isNotEmpty()) {
                        listener.onLineReceived(line)
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(clientConfigUuid)
        descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        if (descriptor != null) {
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun cleanup() {
        txCharacteristic = null
        rxCharacteristic = null
        gatt?.close()
        gatt = null
    }

    private fun scheduleReconnect() {
        handler.postDelayed({
            startScan()
        }, 2000)
    }
}
