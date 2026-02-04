package com.example.lakki_phone

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import com.example.lakki_phone.bluetooth.BluetoothConnector
import com.example.lakki_phone.ui.theme.LakkiphoneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    val bluetoothConnector = remember { BluetoothConnector() }
    var connectionState by rememberSaveable { mutableStateOf(BluetoothConnectionState.DISCONNECTED) }

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
                        connectionState = BluetoothConnectionState.CONNECTING
                        bluetoothConnector.startDiscovery()
                    },
                    onDisconnectClick = {
                        connectionState = BluetoothConnectionState.DISCONNECTED
                    },
                    modifier = Modifier.padding(innerPadding)
                )
                AppDestinations.FAVORITES -> NavigationStatusScreen(
                    modifier = Modifier.padding(innerPadding)
                )
                AppDestinations.PROFILE -> DiagnosticsScreen(
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
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
fun NavigationStatusScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Navigation display")
        Text(text = "Skeleton placeholder for route guidance visuals.")
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
