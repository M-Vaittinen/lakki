# ESP32 cap BLE sketch aligned with Lakki phone app

This sketch is designed to match the Android BLE GATT client in
`app/src/main/java/com/example/lakki_phone/bluetooth/BleGattClient.kt`.

## UUID alignment

The app expects Nordic UART Service (NUS)-style UUIDs:

- Service: `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`
- RX (phone writes to cap): `6E400002-B5A3-F393-E0A9-E50E24DCCA9E`
- TX (cap notifies phone): `6E400003-B5A3-F393-E0A9-E50E24DCCA9E`
- CCCD descriptor (`BLE2902`) must be present on TX.

## Complete Arduino ESP32 sketch

```cpp
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// Matches app/src/main/java/com/example/lakki_phone/bluetooth/BleGattClient.kt
static BLEUUID SERVICE_UUID("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
static BLEUUID RX_CHAR_UUID("6E400002-B5A3-F393-E0A9-E50E24DCCA9E"); // phone -> cap (WRITE)
static BLEUUID TX_CHAR_UUID("6E400003-B5A3-F393-E0A9-E50E24DCCA9E"); // cap -> phone (NOTIFY)

BLEServer* pServer = nullptr;
BLEService* pService = nullptr;
BLECharacteristic* pRxCharacteristic = nullptr;
BLECharacteristic* pTxCharacteristic = nullptr;

volatile bool deviceConnected = false;
volatile bool previouslyConnected = false;

class CapServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* server) override {
    deviceConnected = true;
    Serial.println("[BLE] Phone connected");

    // Optional: try larger MTU for larger app payload framing.
    // Android side already handles MTU changes if they happen.
    // server->updatePeerMTU(server->getConnId(), 185);
  }

  void onDisconnect(BLEServer* server) override {
    deviceConnected = false;
    Serial.println("[BLE] Phone disconnected");
  }
};

class CapRxCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* characteristic) override {
    std::string value = characteristic->getValue();
    if (value.empty()) {
      return;
    }

    Serial.printf("[BLE] RX %u bytes: ", (unsigned)value.size());
    for (size_t i = 0; i < value.size(); ++i) {
      Serial.printf("%02X ", (uint8_t)value[i]);
    }
    Serial.println();

    // TODO: parse app frames and execute command.
    // Example echo ACK with same payload:
    if (deviceConnected && pTxCharacteristic != nullptr) {
      pTxCharacteristic->setValue((uint8_t*)value.data(), value.size());
      pTxCharacteristic->notify();
    }
  }
};

void setupAdvertising() {
  BLEAdvertising* advertising = BLEDevice::getAdvertising();
  advertising->addServiceUUID(SERVICE_UUID);
  advertising->setScanResponse(true);

  // Common compatibility hint values for Android BLE central devices.
  advertising->setMinPreferred(0x06);
  advertising->setMinPreferred(0x12);

  BLEDevice::startAdvertising();
  Serial.println("[BLE] Advertising started");
}

void setup() {
  Serial.begin(115200);
  delay(300);
  Serial.println("[SYS] Boot");

  BLEDevice::init("LakkiCap");

  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new CapServerCallbacks());

  pService = pServer->createService(SERVICE_UUID);

  // RX characteristic: app writes commands with WRITE_TYPE_DEFAULT.
  pRxCharacteristic = pService->createCharacteristic(
      RX_CHAR_UUID,
      BLECharacteristic::PROPERTY_WRITE
  );
  pRxCharacteristic->setCallbacks(new CapRxCallbacks());

  // TX characteristic: cap notifies app.
  pTxCharacteristic = pService->createCharacteristic(
      TX_CHAR_UUID,
      BLECharacteristic::PROPERTY_NOTIFY
  );
  pTxCharacteristic->addDescriptor(new BLE2902());

  pService->start();
  setupAdvertising();
}

void loop() {
  // Restart advertising after disconnect (if needed).
  if (!deviceConnected && previouslyConnected) {
    delay(150);
    BLEDevice::startAdvertising();
    Serial.println("[BLE] Restarted advertising after disconnect");
    previouslyConnected = deviceConnected;
  }

  // Connection edge.
  if (deviceConnected && !previouslyConnected) {
    previouslyConnected = deviceConnected;
  }

  // Example periodic notification payload (replace with real cap data).
  static uint32_t lastMs = 0;
  const uint32_t now = millis();
  if (deviceConnected && (now - lastMs) >= 1000) {
    lastMs = now;

    // Example payload: 4-byte big-endian int for direction-like telemetry.
    uint8_t payload[4];
    int32_t demoDirection = 90;
    payload[0] = (demoDirection >> 24) & 0xFF;
    payload[1] = (demoDirection >> 16) & 0xFF;
    payload[2] = (demoDirection >> 8) & 0xFF;
    payload[3] = demoDirection & 0xFF;

    pTxCharacteristic->setValue(payload, sizeof(payload));
    pTxCharacteristic->notify();
    Serial.println("[BLE] TX notify sent");
  }

  delay(10);
}
```

## Why this matches the app

- The app connects as BLE GATT client and discovers the NUS service UUID above.
- The app writes commands to the RX characteristic UUID (`...0002...`).
- The app subscribes to notifications on TX UUID (`...0003...`) by writing CCCD.
- The sketch includes `BLE2902` on TX so CCCD writes succeed.

## Audit of current Android connection flow

Based on `BleGattClient.kt`, the current flow is:

1. `connectGatt(...)`
2. `discoverServices()` in `onConnectionStateChange(...STATE_CONNECTED...)`
3. Resolve service and characteristics in `onServicesDiscovered(...)`
4. Enable local notification routing via `setCharacteristicNotification(...)`
5. Write TX CCCD descriptor with `ENABLE_NOTIFICATION_VALUE`
6. Mark client `CONNECTED` in `onDescriptorWrite(...)`

This is a valid and expected Android BLE flow.

### Potentially unnecessary/redundant step

- The previously redundant `CONNECTING` emission on `STATE_CONNECTED` has been removed
  from the app client implementation.

### Optional improvements (not strictly unnecessary)

- Request MTU (`requestMtu(185)` for example) after connect/discovery to improve throughput
  for larger protocol messages; the client already handles `onMtuChanged(...)`.
- Optionally call `discoverServices()` only once per new GATT object (already effectively true).
- Keep descriptor write as-is; it is required for notifications.

## Cap-side checklist for first successful notify

1. Boot + `BLEDevice::init("LakkiCap")`
2. Create server, service, RX(write), TX(notify), TX `BLE2902`
3. Start service and advertising with service UUID
4. Android connects and discovers services
5. Android writes CCCD for TX notifications
6. Cap calls `pTxCharacteristic->notify()` with payload bytes


## Phone app connection behavior (updated)

The app now actively performs BLE scanning for the cap when Connect is pressed,
instead of only trying bonded devices.

### Connect button flow

1. Request `BLUETOOTH_CONNECT` + `BLUETOOTH_SCAN` runtime permissions if missing.
2. Start the foreground service.
3. Service checks bonded devices by name (`LakkiCap`).
4. If not bonded/found, service starts LE scan filtered by service UUID
   `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`.
5. On matching scan result, service stops scan and calls `BleGattClient.connect(device)`.
6. Connection state becomes:
   - `CONNECTING` while scanning/connecting
   - `CONNECTED` only after CCCD write succeeds (notifications enabled)
   - `DISCONNECTED` on errors/disconnect.

This behavior matches a first-time device that is advertising but not yet bonded.
