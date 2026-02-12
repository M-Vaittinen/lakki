package com.example.lakki_phone.navigation

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.bluetooth.le.ScanCallback
import android.content.pm.ServiceInfo
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.lakki_phone.BluetoothConnectionState
import com.example.lakki_phone.R
import com.example.lakki_phone.bluetooth.BleGattClient
import com.example.lakki_phone.bluetooth.BleGattConnectionState
import com.example.lakki_phone.bluetooth.BluetoothConnector
import com.example.lakki_phone.bluetooth.ExternalNavigationProtocol
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.maplibre.android.geometry.LatLng
import java.util.UUID

class NavigationForegroundService : Service() {
    private val bluetoothConnector = BluetoothConnector()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }
    private val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
        .setMinUpdateIntervalMillis(2_000L)
        .build()
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                currentLocation.value = LatLng(location.latitude, location.longitude)
                sendDestinationUpdateIfRequested()
            }
        }
    }
    private val gattClient = BleGattClient(
        context = this,
        onConnectionStateChanged = { state ->
            connectionState.value = when (state) {
                BleGattConnectionState.CONNECTED -> BluetoothConnectionState.CONNECTED
                BleGattConnectionState.CONNECTING -> BluetoothConnectionState.CONNECTING
                BleGattConnectionState.DISCONNECTED -> BluetoothConnectionState.DISCONNECTED
            }
            if (state == BleGattConnectionState.DISCONNECTED) {
                capDirection.value = null
            }
        },
        onMessageReceived = { payload ->
            handleIncomingGattMessage(payload)
        },
    )
    private val reconnectRunnable = object : Runnable {
        override fun run() {
            ensureGattConnection()
            reconnectHandler.postDelayed(this, RECONNECT_INTERVAL_MS)
        }
    }
    private var destinationRequestPending = false
    private var activeScanCallback: ScanCallback? = null
    private val scanTimeoutRunnable = Runnable {
        stopActiveScan()
        connectToBondedDevice()
    }

    override fun onCreate() {
        super.onCreate()
        activeService = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning.value = true
        startForegroundService()
        startLocationUpdates()
        startReconnectLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        stopLocationUpdates()
        stopReconnectLoop()
        disconnectGatt()
        isRunning.value = false
        activeService = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundService() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW,
                )
                notificationManager.createNotificationChannel(channel)
            }
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Navigation active")
            .setContentText("Tracking location and device connection.")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, resolveForegroundServiceType())
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun resolveForegroundServiceType(): Int {
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        if (hasBluetoothConnectPermission()) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        }
        return type
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            return
        }
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper(),
            )
        } catch (_: SecurityException) {
            // Ignore when permissions are missing at runtime.
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun startReconnectLoop() {
        reconnectHandler.removeCallbacks(reconnectRunnable)
        reconnectHandler.post(reconnectRunnable)
    }

    private fun stopReconnectLoop() {
        reconnectHandler.removeCallbacks(reconnectRunnable)
    }

    @SuppressLint("MissingPermission")
    private fun ensureGattConnection() {
        if (!hasBluetoothConnectPermission() || !hasBluetoothScanPermission()) {
            connectionState.value = BluetoothConnectionState.DISCONNECTED
            return
        }
        if (connectionState.value == BluetoothConnectionState.CONNECTED) {
            return
        }
        if (connectionState.value == BluetoothConnectionState.CONNECTING && activeScanCallback != null) {
            return
        }
        val device = try {
            bluetoothConnector.getBondedDevices().firstOrNull { bonded ->
                bonded.name == CAP_DEVICE_NAME
            }
        } catch (_: SecurityException) {
            null
        }
        if (device != null) {
            connectionState.value = BluetoothConnectionState.CONNECTING
            gattClient.connect(device)
            return
        }
        startScanAndConnect()
    }

    private fun connectToBondedDevice() {
        mainHandler.post { ensureGattConnection() }
    }

    @SuppressLint("MissingPermission")
    private fun startScanAndConnect() {
        stopActiveScan()
        val callback = try {
            bluetoothConnector.startLeScanForService(
                serviceUuid = CAP_DEVICE_SERVICE_UUID,
                preferredDeviceName = CAP_DEVICE_NAME,
                onDeviceFound = { device ->
                    mainHandler.post {
                        stopActiveScan()
                        gattClient.connect(device)
                    }
                },
                onScanFailed = {
                    mainHandler.post {
                        stopActiveScan()
                        connectionState.value = BluetoothConnectionState.DISCONNECTED
                    }
                },
            )
        } catch (_: SecurityException) {
            null
        }
        if (callback == null) {
            connectionState.value = BluetoothConnectionState.DISCONNECTED
            return
        }
        connectionState.value = BluetoothConnectionState.CONNECTING
        activeScanCallback = callback
        reconnectHandler.removeCallbacks(scanTimeoutRunnable)
        reconnectHandler.postDelayed(scanTimeoutRunnable, SCAN_TIMEOUT_MS)
    }

    @SuppressLint("MissingPermission")
    private fun stopActiveScan() {
        reconnectHandler.removeCallbacks(scanTimeoutRunnable)
        try {
            bluetoothConnector.stopLeScan(activeScanCallback)
        } catch (_: SecurityException) {
            // Ignore if permission changed while scanning.
        }
        activeScanCallback = null
    }

    @SuppressLint("MissingPermission")
    private fun disconnectGatt() {
        stopActiveScan()
        if (hasBluetoothConnectPermission()) {
            gattClient.disconnect()
        } else {
            connectionState.value = BluetoothConnectionState.DISCONNECTED
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendDestinationMessage(payload: ByteArray): Boolean {
        return gattClient.sendMessage(payload)
    }

    private fun sendCapDirectionRequestMessage(payload: ByteArray): Boolean {
        return gattClient.sendMessage(payload)
    }

    private fun handleIncomingGattMessage(payload: ByteArray) {
        lastReceivedMessage.value = payload
        val messageType = ExternalNavigationProtocol.readMessageType(payload) ?: return
        when (messageType) {
            ExternalNavigationProtocol.MessageType.DESTINATION_REQUEST -> {
                destinationRequestPending = true
                sendDestinationUpdateIfRequested()
            }
            ExternalNavigationProtocol.MessageType.CAP_DIRECTION -> {
                val header = ExternalNavigationProtocol.readCapDirectionHeader(payload) ?: return
                capDirection.value = header.direction
            }
            else -> Unit
        }
    }

    private fun sendDestinationUpdateIfRequested() {
        if (!destinationRequestPending) {
            return
        }
        val location = currentLocation.value ?: return
        val destination = selectedDestination.value ?: return
        val header = computeDestinationHeader(location, destination)
        val payload = ExternalNavigationProtocol.buildDestinationMessage(header)
        if (gattClient.sendMessage(payload)) {
            destinationRequestPending = false
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBluetoothScanPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_SCAN,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasLocationPermission(): Boolean {
        val finePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarsePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return finePermission || coarsePermission
    }

    companion object {
        private const val CHANNEL_ID = "navigation-foreground-channel"
        private const val CHANNEL_NAME = "Navigation Updates"
        private const val NOTIFICATION_ID = 1001
        private const val RECONNECT_INTERVAL_MS = 10_000L
        private const val SCAN_TIMEOUT_MS = 8_000L
        private const val CAP_DEVICE_NAME = "LakkiCap"
        private val CAP_DEVICE_SERVICE_UUID: UUID =
            UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")

        val currentLocation = mutableStateOf<LatLng?>(null)
        val selectedDestination = mutableStateOf<LatLng?>(null)
        val connectionState = mutableStateOf(BluetoothConnectionState.DISCONNECTED)
        val isRunning = mutableStateOf(false)

        @Volatile
        private var activeService: NavigationForegroundService? = null

        fun start(context: Context) {
            val intent = Intent(context, NavigationForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NavigationForegroundService::class.java))
        }

        fun connectToBondedDevice() {
            activeService?.connectToBondedDevice()
        }

        fun disconnectGatt() {
            activeService?.disconnectGatt()
        }

        fun sendDestination(payload: ByteArray): Boolean {
            return activeService?.sendDestinationMessage(payload) ?: false
        }

        fun sendCapDirectionRequestStart(): Boolean {
            val payload = ExternalNavigationProtocol.buildCapDirectionRequestStartMessage()
            return activeService?.sendCapDirectionRequestMessage(payload) ?: false
        }

        fun sendCapDirectionRequestStop(): Boolean {
            val payload = ExternalNavigationProtocol.buildCapDirectionRequestStopMessage()
            return activeService?.sendCapDirectionRequestMessage(payload) ?: false
        }

        val lastReceivedMessage = mutableStateOf<ByteArray?>(null)
        val capDirection = mutableStateOf<Int?>(null)
    }
}
