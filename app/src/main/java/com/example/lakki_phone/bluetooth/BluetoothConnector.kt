package com.example.lakki_phone.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
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
