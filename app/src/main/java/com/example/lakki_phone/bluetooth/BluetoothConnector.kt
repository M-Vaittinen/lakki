package com.example.lakki_phone.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import android.content.Context
import androidx.annotation.RequiresPermission
import java.io.IOException
import java.util.UUID

class BluetoothConnector {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startDiscovery(): Boolean {
        return bluetoothAdapter?.startDiscovery() ?: false
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun getBondedDevices(): Set<BluetoothDevice> {
        return bluetoothAdapter?.bondedDevices.orEmpty()
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    fun startLeScanForService(
        serviceUuid: UUID,
        preferredDeviceName: String,
        onDeviceFound: (BluetoothDevice) -> Unit,
        onScanFailed: () -> Unit,
    ): ScanCallback? {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return null
        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(serviceUuid))
                .build(),
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                val name = device.name ?: result.scanRecord?.deviceName
                if (name == preferredDeviceName || name == null) {
                    stopLeScan(this)
                    onDeviceFound(device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                onScanFailed()
            }
        }
        scanner.startScan(filters, settings, callback)
        return callback
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopLeScan(callback: ScanCallback?) {
        if (callback == null) {
            return
        }
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(callback)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connectToDevice(device: BluetoothDevice, serviceUuid: UUID): Result<BluetoothSocket> {
        return runCatching {
            val socket = device.createRfcommSocketToServiceRecord(serviceUuid)
            socket.connect()
            socket
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect(socket: BluetoothSocket) {
        try {
            socket.close()
        } catch (exception: IOException) {
            // TODO: handle disconnect errors.
        }
    }

    fun ensureBluetoothEnabled(context: Context) {
        // TODO: prompt the user to enable Bluetooth via an Activity Result flow.
    }
}
