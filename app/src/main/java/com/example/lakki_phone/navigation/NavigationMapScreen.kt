package com.example.lakki_phone.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.lakki_phone.BluetoothConnectionState
import com.example.lakki_phone.CURRENT_LOCATION_SOURCE_ID
import com.example.lakki_phone.createCurrentLocationLayer
import com.example.lakki_phone.createCurrentLocationSource
import com.example.lakki_phone.bluetooth.ExternalNavigationProtocol
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

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

@Composable
fun NavigationMapScreen(
    selectedDestination: LatLng?,
    currentLocation: LatLng?,
    connectionState: BluetoothConnectionState,
    onDestinationChanged: (LatLng) -> Unit,
    onSendDestinationMessage: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val currentLocationState = rememberUpdatedState(currentLocation)
    val destinationColor = MaterialTheme.colorScheme.primary.toArgb()
    val currentLocationColor = MaterialTheme.colorScheme.tertiary.toArgb()
    var hasCenteredOnLocation by remember { mutableStateOf(false) }
    var isTestMode by remember { mutableStateOf(false) }
    var testPayload by remember { mutableStateOf<ByteArray?>(null) }
    var testHeader by remember { mutableStateOf<ExternalNavigationProtocol.DestinationHeader?>(null) }
    val mapView = remember(destinationColor, currentLocationColor) {
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
                        .withSource(createCurrentLocationSource())
                        .withLayer(
                            CircleLayer(DESTINATION_LAYER_ID, DESTINATION_SOURCE_ID).withProperties(
                                circleRadius(8f),
                                circleColor(destinationColor),
                            )
                        )
                        .withLayer(createCurrentLocationLayer(currentLocationColor))
                ) { style ->
                    selectedDestination?.let { latLng ->
                        updateDestinationSource(style, latLng)
                    }
                    currentLocation?.let { latLng ->
                        updateCurrentLocationSource(style, latLng, map.cameraPosition.zoom)
                    }
                }

                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(62.0, 25.0))
                    .zoom(5.0)
                    .build()

                map.addOnCameraMoveListener {
                    val latestLocation = currentLocationState.value
                    val style = map.style
                    if (latestLocation != null && style != null) {
                        updateCurrentLocationSource(style, latestLocation, map.cameraPosition.zoom)
                    }
                }

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
        Text(
            text = currentLocation
                ?.let { "Current location: %.6f, %.6f".format(it.latitude, it.longitude) }
                ?: "Current location: none"
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Switch(
                checked = isTestMode,
                onCheckedChange = { isEnabled ->
                    isTestMode = isEnabled
                    if (!isEnabled) {
                        testPayload = null
                        testHeader = null
                    }
                }
            )
            Text(text = "Test mode (show message instead of sending)")
        }
        if (isTestMode) {
            val header = testHeader
            val payload = testPayload
            val testMessage = when {
                header == null || payload == null ->
                    "Test mode enabled. Tap the button to compute a destination message."
                else -> buildString {
                    append("Last computed destination message")
                    append("\nDirection: ${header.direction}°")
                    append("\nDistance: ${header.distanceMeters} m")
                    append("\nPayload (hex): ${payload.toHexString()}")
                }
            }
            Text(
                text = testMessage,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        AndroidView(
            modifier = Modifier.weight(1f),
            factory = { mapView },
            update = {
                it.getMapAsync { map ->
                    map.style?.let { style ->
                        if (selectedDestination != null) {
                            updateDestinationSource(style, selectedDestination)
                        }
                        if (currentLocation != null) {
                            updateCurrentLocationSource(style, currentLocation, map.cameraPosition.zoom)
                            if (!hasCenteredOnLocation) {
                                map.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(currentLocation, 12.0),
                                )
                                hasCenteredOnLocation = true
                            }
                        }
                    }
                }
            }
        )

        val isConnected = connectionState == BluetoothConnectionState.CONNECTED
        val isSendEnabled = selectedDestination != null &&
            currentLocation != null &&
            (isTestMode || isConnected)
        val helperText = when {
            !isTestMode && !isConnected -> "Connect to the device to send a destination."
            selectedDestination == null -> "Choose a destination on the map to enable sending."
            currentLocation == null -> "Waiting for current location to send a destination."
            isTestMode -> "Test mode is enabled. The destination message will be shown here."
            else -> "Ready to send the selected destination."
        }

        Button(
            onClick = {
                val location = currentLocation ?: return@Button
                val destination = selectedDestination ?: return@Button
                val header = computeDestinationHeader(location, destination)
                val payload = ExternalNavigationProtocol.buildDestinationMessage(header)
                if (isTestMode) {
                    testHeader = header
                    testPayload = payload
                } else {
                    onSendDestinationMessage(payload)
                }
            },
            enabled = isSendEnabled,
            colors = ButtonDefaults.buttonColors(),
        ) {
            Text(text = if (isTestMode) "Compute destination" else "Send destination")
        }
        Text(
            text = helperText,
            style = MaterialTheme.typography.bodySmall,
            color = if (isSendEnabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            }
        )
    }
}

private fun updateDestinationSource(style: Style, destination: LatLng) {
    style.getSourceAs<GeoJsonSource>(DESTINATION_SOURCE_ID)?.setGeoJson(
        Feature.fromGeometry(Point.fromLngLat(destination.longitude, destination.latitude))
    )
}

private fun updateCurrentLocationSource(style: Style, currentLocation: LatLng, zoom: Double) {
    val delta = calculateTriangleDelta(zoom)
    val triangle = Polygon.fromLngLats(
        listOf(
            listOf(
                Point.fromLngLat(currentLocation.longitude, currentLocation.latitude + delta),
                Point.fromLngLat(currentLocation.longitude - delta, currentLocation.latitude - delta),
                Point.fromLngLat(currentLocation.longitude + delta, currentLocation.latitude - delta),
                Point.fromLngLat(currentLocation.longitude, currentLocation.latitude + delta),
            )
        )
    )
    style.getSourceAs<GeoJsonSource>(CURRENT_LOCATION_SOURCE_ID)?.setGeoJson(
        Feature.fromGeometry(triangle)
    )
}

private fun calculateTriangleDelta(zoom: Double): Double {
    val baseDelta = 0.0015
    val scale = 2.0.coerceAtLeast(Math.pow(2.0, 12.0 - zoom))
    return baseDelta * scale
}

private fun ByteArray.toHexString(): String {
    return joinToString(separator = " ") { byte -> "%02X".format(byte) }
}
