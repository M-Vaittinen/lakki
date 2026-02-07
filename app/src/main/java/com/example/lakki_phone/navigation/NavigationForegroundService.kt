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
import android.content.pm.ServiceInfo
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.lakki_phone.BluetoothConnectionState
import com.example.lakki_phone.R
import com.example.lakki_phone.bluetooth.BleGattClient
import com.example.lakki_phone.bluetooth.BleGattConnectionState
import com.example.lakki_phone.bluetooth.BluetoothConnector
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.maplibre.android.geometry.LatLng

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
        if (!hasBluetoothConnectPermission()) {
            return
        }
        if (connectionState.value != BluetoothConnectionState.DISCONNECTED) {
            return
        }
        val device = try {
            bluetoothConnector.getBondedDevices().firstOrNull()
        } catch (_: SecurityException) {
            null
        }
        device?.let { gattClient.connect(it) }
    }

    private fun connectToBondedDevice() {
        mainHandler.post { ensureGattConnection() }
    }

    @SuppressLint("MissingPermission")
    private fun disconnectGatt() {
        if (hasBluetoothConnectPermission()) {
            gattClient.disconnect()
        } else {
            connectionState.value = BluetoothConnectionState.DISCONNECTED
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendDestinationMessage(payload: ByteArray): Boolean {
        return if (hasBluetoothConnectPermission()) {
            gattClient.writeMessage(payload)
        } else {
            false
        }
    }

    private fun handleIncomingGattMessage(payload: ByteArray) {
        lastReceivedMessage.value = payload
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT,
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

        val currentLocation = mutableStateOf<LatLng?>(null)
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

        val lastReceivedMessage = mutableStateOf<ByteArray?>(null)
    }
}
