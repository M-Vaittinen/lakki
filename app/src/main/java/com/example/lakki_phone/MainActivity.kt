package com.example.lakki_phone

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.lakki_phone.bluetooth.ExternalNavigationProtocol.CapState
import com.example.lakki_phone.navigation.NavigationMapScreen
import com.example.lakki_phone.navigation.NavigationForegroundService
import com.example.lakki_phone.navigation.NavigationPreferences
import com.example.lakki_phone.ui.theme.LakkiphoneTheme
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.fillOutlineColor
import org.maplibre.android.style.sources.GeoJsonSource

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
    var currentDestination by remember { mutableStateOf(AppDestinations.HOME) }
    var selectedDestination by remember { mutableStateOf<LatLng?>(null) }
    val currentLocation by NavigationForegroundService.currentLocation
    val connectionState by NavigationForegroundService.connectionState
    val isServiceRunning by NavigationForegroundService.isRunning
    val capDirection by NavigationForegroundService.capDirection
    val capState by NavigationForegroundService.capState
    val capStateErrorExplanation by NavigationForegroundService.capStateErrorExplanation
    val capDebugLogLines by NavigationForegroundService.capDebugLogLines
    var hasLocationPermission by remember { mutableStateOf(false) }
    var hasRequestedLocationPermission by remember { mutableStateOf(false) }
    var hasBluetoothPermissions by remember { mutableStateOf(false) }
    val navigationEnabledFlow = remember(appContext) {
        NavigationPreferences.navigationEnabledFlow(appContext)
    }
    val navigationEnabled by navigationEnabledFlow.collectAsState(
        initial = NavigationPreferences.isNavigationEnabled(appContext),
    )
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasBluetoothPermissions = permissions[Manifest.permission.BLUETOOTH_CONNECT] == true &&
            permissions[Manifest.permission.BLUETOOTH_SCAN] == true
        if (hasBluetoothPermissions) {
            NavigationForegroundService.start(appContext)
            NavigationForegroundService.connectToBondedDevice()
        } else {
            NavigationForegroundService.connectionState.value =
                BluetoothConnectionState.DISCONNECTED
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasLocationPermission && navigationEnabled) {
            NavigationForegroundService.start(appContext)
        }
    }

    LaunchedEffect(Unit) {
        hasLocationPermission = hasLocationPermission(appContext)
        hasBluetoothPermissions = hasBluetoothPermissions(appContext)
    }

    LaunchedEffect(navigationEnabled) {
        if (!navigationEnabled) {
            hasRequestedLocationPermission = false
        }
    }

    LaunchedEffect(navigationEnabled, hasLocationPermission, hasRequestedLocationPermission) {
        when {
            navigationEnabled && hasLocationPermission -> {
                NavigationForegroundService.start(appContext)
            }
            navigationEnabled && !hasLocationPermission && !hasRequestedLocationPermission -> {
                hasRequestedLocationPermission = true
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                )
            }
            !navigationEnabled -> {
                NavigationForegroundService.stop(appContext)
            }
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
                        if (!hasBluetoothPermissions(appContext)) {
                            bluetoothPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.BLUETOOTH_CONNECT,
                                    Manifest.permission.BLUETOOTH_SCAN,
                                )
                            )
                        } else {
                            NavigationForegroundService.start(appContext)
                            NavigationForegroundService.connectToBondedDevice()
                        }
                    },
                    onDisconnectClick = {
                        if (hasBluetoothConnectPermission(appContext)) {
                            NavigationForegroundService.disconnectGatt()
                        }
                    },
                    modifier = Modifier.padding(innerPadding)
                )

                AppDestinations.FAVORITES -> NavigationMapScreen(
                    selectedDestination = selectedDestination,
                    currentLocation = currentLocation,
                    connectionState = connectionState,
                    navigationEnabled = navigationEnabled,
                    isServiceRunning = isServiceRunning,
                    capDirectionDegrees = capDirection,
                    onNavigationModeToggle = { isEnabled ->
                        NavigationPreferences.setNavigationEnabled(appContext, isEnabled)
                    },
                    onDestinationChanged = {
                        selectedDestination = it
                        NavigationForegroundService.selectedDestination.value = it
                    },
                    onSendDestinationMessage = { payload ->
                        val writeSuccess = if (hasBluetoothConnectPermission(appContext)) {
                            NavigationForegroundService.sendDestination(payload)
                        } else {
                            false
                        }
                        if (!writeSuccess) {
                            NavigationForegroundService.connectionState.value =
                                BluetoothConnectionState.DISCONNECTED
                        }
                    },
                    onCapDirectionRequestStart = {
                        val writeSuccess = if (hasBluetoothConnectPermission(appContext)) {
                            NavigationForegroundService.sendCapDirectionRequestStart()
                        } else {
                            false
                        }
                        if (!writeSuccess) {
                            NavigationForegroundService.connectionState.value =
                                BluetoothConnectionState.DISCONNECTED
                        }
                    },
                    onCapDirectionRequestStop = {
                        if (hasBluetoothConnectPermission(appContext)) {
                            NavigationForegroundService.sendCapDirectionRequestStop()
                        }
                    },
                    modifier = Modifier.padding(innerPadding)
                )

                AppDestinations.PROFILE -> DiagnosticsScreen(
                    capState = capState,
                    capStateErrorExplanation = capStateErrorExplanation,
                    capDebugLogLines = capDebugLogLines,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}


private fun hasBluetoothPermissions(context: android.content.Context): Boolean {
    val hasConnect = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH_CONNECT,
    ) == PackageManager.PERMISSION_GRANTED
    val hasScan = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH_SCAN,
    ) == PackageManager.PERMISSION_GRANTED
    return hasConnect && hasScan
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

fun createCurrentLocationSource(): GeoJsonSource = GeoJsonSource(CURRENT_LOCATION_SOURCE_ID)

fun createCurrentLocationLayer(color: Int): FillLayer =
    FillLayer(CURRENT_LOCATION_LAYER_ID, CURRENT_LOCATION_SOURCE_ID).withProperties(
        fillColor(color),
        fillOpacity(0.9f),
        fillOutlineColor(color),
    )

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
fun DiagnosticsScreen(
    capState: CapState,
    capStateErrorExplanation: String?,
    capDebugLogLines: List<String>,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Device diagnostics")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "Cap status:")
            Text(
                text = capState.name.lowercase().replaceFirstChar { it.titlecase() },
                color = when (capState) {
                    CapState.ERROR -> MaterialTheme.colorScheme.error
                    CapState.CALIBRATING -> MaterialTheme.colorScheme.tertiary
                    CapState.NAVIGATING -> MaterialTheme.colorScheme.primary
                    CapState.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (capState == CapState.ERROR &&
            !capStateErrorExplanation.isNullOrBlank()
        ) {
            Text(
                text = "Error details: $capStateErrorExplanation",
                color = MaterialTheme.colorScheme.error,
            )
        }

        Text(text = "Cap debug log")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (capDebugLogLines.isEmpty()) {
                Text(
                    text = "No debug messages received yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                capDebugLogLines.forEach { line ->
                    Text(
                        text = line,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LakkiphoneAppPreview() {
    LakkiphoneTheme {
        LakkiphoneApp()
    }
}
