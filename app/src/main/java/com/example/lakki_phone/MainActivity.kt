package com.example.lakki_phone

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.bluetooth.BluetoothSocket
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.lakki_phone.bluetooth.BluetoothConnector
import com.example.lakki_phone.navigation.NavigationMapScreen
import com.example.lakki_phone.ui.theme.LakkiphoneTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.fillOutlineColor
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.UUID

const val CURRENT_LOCATION_SOURCE_ID = "current-location-source"
const val CURRENT_LOCATION_LAYER_ID = "current-location-layer"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        enableEdgeToEdge()
        setContent {
            LakkiphoneTheme {
                LakkiphoneApp()
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun LakkiphoneApp() {
    val appContext = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var currentDestination by remember { mutableStateOf(AppDestinations.HOME) }
    val bluetoothConnector = remember { BluetoothConnector() }
    var connectionState by remember { mutableStateOf(BluetoothConnectionState.DISCONNECTED) }
    var bluetoothSocket by remember { mutableStateOf<BluetoothSocket?>(null) }
    var selectedDestination by remember { mutableStateOf<LatLng?>(null) }
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(appContext)
    }
    val locationRequest = remember {
        LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateIntervalMillis(2_000L)
            .build()
    }
    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    currentLocation = LatLng(location.latitude, location.longitude)
                }
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasLocationPermission) {
            startLocationUpdates(fusedLocationClient, locationRequest, locationCallback)
        }
    }

    LaunchedEffect(Unit) {
        hasLocationPermission = hasLocationPermission(appContext)
    }

    DisposableEffect(lifecycleOwner, hasLocationPermission) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (hasLocationPermission) {
                        startLocationUpdates(fusedLocationClient, locationRequest, locationCallback)
                    } else {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            )
                        )
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    fusedLocationClient.removeLocationUpdates(locationCallback)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            it.icon,
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            when (currentDestination) {
                AppDestinations.HOME -> DeviceConnectionScreen(
                    connectionState = connectionState,
                    onConnectClick = {
                        if (!hasBluetoothConnectPermission(appContext)) {
                            connectionState = BluetoothConnectionState.DISCONNECTED
                        } else {
                            connectionState = BluetoothConnectionState.CONNECTING
                            val device = try {
                                bluetoothConnector.getBondedDevices().firstOrNull()
                            } catch (_: SecurityException) {
                                null
                            }
                            val connectionResult = device?.let {
                                bluetoothConnector.connectToDevice(it, DEFAULT_BLUETOOTH_SERVICE_UUID)
                            }
                            connectionResult
                                ?.onSuccess { socket ->
                                    bluetoothSocket = socket
                                    connectionState = BluetoothConnectionState.CONNECTED
                                }
                                ?.onFailure {
                                    bluetoothSocket = null
                                    connectionState = BluetoothConnectionState.DISCONNECTED
                                }
                                ?: run {
                                    bluetoothSocket = null
                                    connectionState = BluetoothConnectionState.DISCONNECTED
                                }
                        }
                    },
                    onDisconnectClick = {
                        bluetoothSocket?.let { bluetoothConnector.disconnect(it) }
                        bluetoothSocket = null
                        connectionState = BluetoothConnectionState.DISCONNECTED
                    },
                    modifier = Modifier.padding(innerPadding)
                )

                AppDestinations.FAVORITES -> NavigationMapScreen(
                    selectedDestination = selectedDestination,
                    currentLocation = currentLocation,
                    onDestinationChanged = { selectedDestination = it },
                    modifier = Modifier.padding(innerPadding)
                )

                AppDestinations.PROFILE -> DiagnosticsScreen(
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}


private fun hasBluetoothConnectPermission(context: android.content.Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH_CONNECT,
    ) == PackageManager.PERMISSION_GRANTED
}

private fun hasLocationPermission(context: android.content.Context): Boolean {
    val finePermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val coarsePermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    return finePermission || coarsePermission
}

private fun startLocationUpdates(
    client: FusedLocationProviderClient,
    request: LocationRequest,
    callback: LocationCallback,
) {
    client.requestLocationUpdates(request, callback, Looper.getMainLooper())
}

fun createCurrentLocationSource(): GeoJsonSource = GeoJsonSource(CURRENT_LOCATION_SOURCE_ID)

fun createCurrentLocationLayer(color: Int): FillLayer =
    FillLayer(CURRENT_LOCATION_LAYER_ID, CURRENT_LOCATION_SOURCE_ID).withProperties(
        fillColor(color),
        fillOpacity(0.9f),
        fillOutlineColor(color),
    )

private val DEFAULT_BLUETOOTH_SERVICE_UUID: UUID =
    UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

enum class BluetoothConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Device", Icons.Default.Home),
    FAVORITES("Navigation", Icons.Default.Favorite),
    PROFILE("Diagnostics", Icons.Default.AccountBox),
}

@Composable
fun DeviceConnectionScreen(
    connectionState: BluetoothConnectionState,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "External navigation device")
        Text(text = "Connection status: $connectionState")
        Button(onClick = onConnectClick) {
            Text(text = "Connect via Bluetooth")
        }
        Button(onClick = onDisconnectClick) {
            Text(text = "Disconnect")
        }
    }
}

@Composable
fun DiagnosticsScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Device diagnostics")
        Text(text = "Skeleton placeholder for sensor and health data.")
    }
}

@Preview(showBackground = true)
@Composable
fun LakkiphoneAppPreview() {
    LakkiphoneTheme {
        LakkiphoneApp()
    }
}
