package com.example.lakki_phone.bluetooth

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import java.util.UUID

// Cap device advertises the Nordic UART Service (NUS) UUIDs for BLE transport.
private val CAP_DEVICE_SERVICE_UUID: UUID =
    UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
private val CAP_DEVICE_RX_CHARACTERISTIC_UUID: UUID =
    UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
private val CAP_DEVICE_TX_CHARACTERISTIC_UUID: UUID =
    UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
private val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID =
    UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

enum class BleGattConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
}

class BleGattClient(
    private val context: Context,
    private val serviceUuid: UUID = CAP_DEVICE_SERVICE_UUID,
    private val rxCharacteristicUuid: UUID = CAP_DEVICE_RX_CHARACTERISTIC_UUID,
    private val txCharacteristicUuid: UUID = CAP_DEVICE_TX_CHARACTERISTIC_UUID,
    private val onConnectionStateChanged: (BleGattConnectionState) -> Unit = {},
    private val onMessageReceived: (ByteArray) -> Unit = {},
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var bluetoothGatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private val writeQueue = ArrayDeque<ByteArray>()
    private var isWriting = false
    private var maxPayloadSize = DEFAULT_GATT_PAYLOAD_SIZE
    private val messageFramer = BleMessageFramer { maxPayloadSize }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: BluetoothDevice) {
        if (!hasConnectPermission()) {
            updateState(BleGattConnectionState.DISCONNECTED)
            return
        }
        disconnect()
        updateState(BleGattConnectionState.CONNECTING)
        bluetoothGatt = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattCallback)
            }
        } catch (_: SecurityException) {
            updateState(BleGattConnectionState.DISCONNECTED)
            null
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        if (!hasConnectPermission()) {
            updateState(BleGattConnectionState.DISCONNECTED)
            return
        }
        try {
            bluetoothGatt?.disconnect()
        } catch (_: SecurityException) {
            // Ignore disconnect failure when permissions are missing.
        }
        closeGatt()
        updateState(BleGattConnectionState.DISCONNECTED)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendMessage(payload: ByteArray): Boolean {
        if (!hasConnectPermission()) {
            return false
        }
        if (bluetoothGatt == null || rxCharacteristic == null) {
            return false
        }
        val frames = messageFramer.frame(payload)
        if (frames.isEmpty()) {
            return false
        }
        writeQueue.addAll(frames)
        if (!isWriting) {
            writeNextFrame()
        }
        return true
    }

    private fun updateState(state: BleGattConnectionState) {
        mainHandler.post { onConnectionStateChanged(state) }
    }

    private fun closeGatt() {
        if (hasConnectPermission()) {
            try {
                bluetoothGatt?.close()
            } catch (_: SecurityException) {
                // Ignore close failure when permission is missing.
            }
        }
        bluetoothGatt = null
        rxCharacteristic = null
        txCharacteristic = null
        writeQueue.clear()
        isWriting = false
    }

    private fun configureGatt(gatt: BluetoothGatt): Boolean {
        if (!hasConnectPermission()) {
            return false
        }
        val service = gatt.getService(serviceUuid) ?: return false
        rxCharacteristic = service.getCharacteristic(rxCharacteristicUuid)
        txCharacteristic = service.getCharacteristic(txCharacteristicUuid)
        return rxCharacteristic != null && enableNotifications(gatt, txCharacteristic)
    }

    private fun writeNextFrame() {
        val gatt = bluetoothGatt
        val characteristic = rxCharacteristic
        if (gatt == null || characteristic == null) {
            writeQueue.clear()
            isWriting = false
            return
        }
        val frame = writeQueue.removeFirstOrNull()
        if (frame == null) {
            isWriting = false
            return
        }
        isWriting = true
        val writeStarted = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(
                    characteristic,
                    frame,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                characteristic.value = frame
                gatt.writeCharacteristic(characteristic)
            }
        } catch (_: SecurityException) {
            false
        }
        if (!writeStarted) {
            writeQueue.clear()
            isWriting = false
        }
    }

    private fun enableNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic?,
    ): Boolean {
        if (!hasConnectPermission()) {
            return false
        }
        characteristic ?: return false
        val notificationSet = try {
            gatt.setCharacteristicNotification(characteristic, true)
        } catch (_: SecurityException) {
            false
        }
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
            ?: return false
        val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        val descriptorWriteResult = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
            } else {
                descriptor.value = value
                gatt.writeDescriptor(descriptor)
            }
        } catch (_: SecurityException) {
            false
        }
        return notificationSet && descriptorWriteResult
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int,
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                try {
                    gatt.disconnect()
                } catch (_: SecurityException) {
                    // Ignore disconnect when permission is missing.
                }
                closeGatt()
                updateState(BleGattConnectionState.DISCONNECTED)
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    try {
                        gatt.discoverServices()
                    } catch (_: SecurityException) {
                        closeGatt()
                        updateState(BleGattConnectionState.DISCONNECTED)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    closeGatt()
                    updateState(BleGattConnectionState.DISCONNECTED)
                }
                else -> Unit
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || !configureGatt(gatt)) {
                try {
                    gatt.disconnect()
                } catch (_: SecurityException) {
                    // Ignore disconnect when permission is missing.
                }
                closeGatt()
                updateState(BleGattConnectionState.DISCONNECTED)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (characteristic.uuid == rxCharacteristicUuid) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    writeNextFrame()
                } else {
                    writeQueue.clear()
                    isWriting = false
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (characteristic.uuid == txCharacteristicUuid) {
                val payload = characteristic.value ?: return
                mainHandler.post { onMessageReceived(payload) }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (characteristic.uuid == txCharacteristicUuid) {
                mainHandler.post { onMessageReceived(value) }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (descriptor.uuid == CLIENT_CHARACTERISTIC_CONFIG_UUID) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    updateState(BleGattConnectionState.CONNECTED)
                } else {
                    try {
                        gatt.disconnect()
                    } catch (_: SecurityException) {
                        // Ignore disconnect when permission is missing.
                    }
                    closeGatt()
                    updateState(BleGattConnectionState.DISCONNECTED)
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                maxPayloadSize = (mtu - ATT_HEADER_SIZE).coerceAtLeast(1)
            }
        }
    }

    private fun hasConnectPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private class BleMessageFramer(
        private val maxPayloadSizeProvider: () -> Int,
    ) {
        fun frame(payload: ByteArray): List<ByteArray> {
            if (payload.isEmpty()) {
                return emptyList()
            }
            val maxPayloadSize = maxPayloadSizeProvider().coerceAtLeast(1)
            if (payload.size <= maxPayloadSize) {
                return listOf(payload)
            }
            val frames = ArrayList<ByteArray>()
            var offset = 0
            while (offset < payload.size) {
                val end = (offset + maxPayloadSize).coerceAtMost(payload.size)
                frames.add(payload.copyOfRange(offset, end))
                offset = end
            }
            return frames
        }
    }

    private companion object {
        const val DEFAULT_GATT_PAYLOAD_SIZE = 20
        const val ATT_HEADER_SIZE = 3
    }
}
