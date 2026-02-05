package com.dogfit.logger

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity(), BleManager.BleListener {
    private lateinit var bleManager: BleManager
    private lateinit var logger: Logger
    private lateinit var statusView: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    private var linesReceived: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusView = findViewById(R.id.textStatus)
        startButton = findViewById(R.id.buttonStart)
        stopButton = findViewById(R.id.buttonStop)

        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        val adapter: BluetoothAdapter? = bluetoothManager.adapter
        bleManager = BleManager(this, adapter, this)
        logger = Logger(this)

        startButton.setOnClickListener {
            if (bleManager.isConnected()) {
                bleManager.sendCommand("START")
            }
            val fileName = logger.startNewFile()
            updateStatus("Logging to $fileName")
        }

        stopButton.setOnClickListener {
            if (bleManager.isConnected()) {
                bleManager.sendCommand("STOP")
            }
            logger.stop()
            updateStatus("Logging stopped")
        }

        ensurePermissions()
    }

    override fun onResume() {
        super.onResume()
        if (hasBlePermissions()) {
            bleManager.start()
        }
    }

    override fun onPause() {
        super.onPause()
        bleManager.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        logger.stop()
    }

    private fun updateStatus(message: String) {
        statusView.text = "${message}\nConnected: ${bleManager.isConnected()}\nLines: $linesReceived"
    }

    private fun ensurePermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1001)
        }
    }

    private fun hasBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onDeviceConnected(name: String?) {
        updateStatus("Connected to ${name ?: "device"}")
    }

    override fun onDeviceDisconnected() {
        updateStatus("Disconnected")
    }

    override fun onLineReceived(line: String) {
        if (logger.isActive()) {
            logger.appendLine(line)
        }
        linesReceived += 1
        if (linesReceived % 10L == 0L) {
            updateStatus("Receiving data")
        }
    }

    override fun onStatusMessage(message: String) {
        updateStatus(message)
    }
}
