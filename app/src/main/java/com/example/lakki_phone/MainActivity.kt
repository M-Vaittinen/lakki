package com.example.lakki_phone

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.lakki_phone.bluetooth.BluetoothConnector
import com.example.lakki_phone.ui.theme.LakkiphoneTheme
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

private const val NLS_WMTS_API_KEY = "YOUR_NLS_API_KEY"
private const val NLS_WMTS_SOURCE_ID = "nls-wmts"
private const val NLS_WMTS_LAYER_ID = "nls-topographic"
private const val DESTINATION_SOURCE_ID = "destination-source"
private const val DESTINATION_LAYER_ID = "destination-layer"
private const val MAP_DATA_LOADING_KNOWN_ISSUE_NOTE =
    "If the map is blank, verify a valid NLS API key and WMTS parameters."

private const val NLS_WMTS_URL_TEMPLATE =
    "https://avoin-karttakuva.maanmittauslaitos.fi/avoin/wmts" +
        "?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0" +
        "&LAYER=maastokartta&STYLE=default" +
        "&TILEMATRIXSET=WGS84_Pseudo-Mercator" +
        "&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}" +
        "&FORMAT=image/png&api-key=$NLS_WMTS_API_KEY"

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
    val bluetoothConnector = remember { BluetoothConnector() }
    var connectionState by remember { mutableStateOf(BluetoothConnectionState.DISCONNECTED) }
    var selectedDestination by remember { mutableStateOf<LatLng?>(null) }

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
                        if (!hasBluetoothScanPermission(appContext)) {
                            connectionState = BluetoothConnectionState.DISCONNECTED
                        } else {
                            connectionState = BluetoothConnectionState.CONNECTING
                            val discoveryStarted = try {
                                bluetoothConnector.startDiscovery()
                            } catch (_: SecurityException) {
                                false
                            }
                            connectionState = if (discoveryStarted) {
                                BluetoothConnectionState.CONNECTED
                            } else {
                                BluetoothConnectionState.DISCONNECTED
                            }
                        }
                    },
                    onDisconnectClick = {
                        connectionState = BluetoothConnectionState.DISCONNECTED
                    },
                    modifier = Modifier.padding(innerPadding)
                )

                AppDestinations.FAVORITES -> NavigationMapScreen(
                    selectedDestination = selectedDestination,
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


private fun hasBluetoothScanPermission(context: android.content.Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH_SCAN,
    ) == PackageManager.PERMISSION_GRANTED
}

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
fun NavigationMapScreen(
    selectedDestination: LatLng?,
    onDestinationChanged: (LatLng) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val destinationColor = MaterialTheme.colorScheme.primary.toArgb()
    val mapView = remember(destinationColor) {
        MapView(context).apply {
            getMapAsync { map ->
                val tileSet = TileSet("2.0.0", NLS_WMTS_URL_TEMPLATE)
                tileSet.scheme = "xyz"
                tileSet.minZoom = 0f
                tileSet.maxZoom = 14f

                map.setStyle(
                    Style.Builder()
                        .withSource(RasterSource(NLS_WMTS_SOURCE_ID, tileSet, 256))
                        .withLayer(RasterLayer(NLS_WMTS_LAYER_ID, NLS_WMTS_SOURCE_ID))
                        .withSource(GeoJsonSource(DESTINATION_SOURCE_ID))
                        .withLayer(
                            CircleLayer(DESTINATION_LAYER_ID, DESTINATION_SOURCE_ID).withProperties(
                                circleRadius(8f),
                                circleColor(destinationColor),
                            )
                        )
                ) { style ->
                    selectedDestination?.let { latLng ->
                        updateDestinationSource(style, latLng)
                    }
                }

                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(62.0, 25.0))
                    .zoom(5.0)
                    .build()

                map.addOnMapClickListener { latLng ->
                    onDestinationChanged(latLng)
                    true
                }
                map.addOnMapLongClickListener { latLng ->
                    onDestinationChanged(latLng)
                    true
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Tap or long-press map to set destination")
        Text(
            text = MAP_DATA_LOADING_KNOWN_ISSUE_NOTE,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = selectedDestination
                ?.let { "Destination: %.6f, %.6f".format(it.latitude, it.longitude) }
                ?: "Destination: none"
        )

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapView },
            update = {
                it.getMapAsync { map ->
                    map.style?.let { style ->
                        if (selectedDestination != null) {
                            updateDestinationSource(style, selectedDestination)
                        }
                    }
                }
            }
        )
    }
}

private fun updateDestinationSource(style: Style, destination: LatLng) {
    style.getSourceAs<GeoJsonSource>(DESTINATION_SOURCE_ID)?.setGeoJson(
        Feature.fromGeometry(Point.fromLngLat(destination.longitude, destination.latitude))
    )
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
