# Bledroid

Bledroid is an Android Bluetooth library for connecting to and communicating with devices over BLE GATT.

## Capabilities

- BLE scanning with `Flow<BleScanResult>`.
- BLE GATT connect, service discovery, characteristic read/write, and notifications.
- BLE client API exposes Flow-based response streams for notification-driven write flows.
- Android Companion Device Manager (CDM) association support for BLE companion pairing flows.
- Builder-based initialization with per-device defaults (timeouts, auto-connect, reconnect behavior, MTU, connection priority, preferred PHY).
- Bluetooth broadcast monitoring for adapter state, bond changes, discovery, device found, ACL connect/disconnect, pairing requests, scan mode, and local name changes.
- Encrypted bonded-connection metadata storage for reconnect on unexpected disconnect (configurable).
- Serialized connect/disconnect and per-characteristic write coordination.
- Runtime permission helpers for Android 6 through Android 15+ behavior.
- Manifest permissions packaged in the library manifest.

## Add The Library Module

From this repository, depend on the module from an Android app:

```kotlin
dependencies {
    implementation(project(":bledroid"))
}
```

For local Maven publishing:

```bash
./gradlew :bledroid:publishReleasePublicationToMavenLocal
```

Then consume it as:

```kotlin
dependencies {
    implementation("com.bledroid:bledroid:0.1.0")
}
```

## Runtime Permissions

The manifest entries are included by the library, but your app still needs to request runtime permissions before scanning or connecting.

```kotlin
val permissions = BluetoothPermissions.requiredRuntimePermissions(scan = true, connect = true)
```

On Android 12 and newer this returns `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT`. On Android 11 and older, scanning requires `ACCESS_FINE_LOCATION`; connecting does not require a runtime Bluetooth permission.

## Initialization

You can initialize with a context directly:

```kotlin
val bledroid = Bledroid(context)
```

Or use global initialization once at app startup and then create instances without passing context:

```kotlin
Bledroid.initialize(appContext)
val bledroid = Bledroid()
```

For device-specific behavior, use the builder pattern:

```kotlin
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

val bledroid = Bledroid.builder()
    .applicationContext(appContext) // optional if Bledroid.initialize(appContext) was already called
    .deviceTypeTag("sensor-v2")
    .defaultAutoConnect(true)
    .connectTimeout(20.seconds)
    .discoverServicesTimeout(15.seconds)
    .readTimeout(8.seconds)
    .writeTimeout(8.seconds)
    .rssiTimeout(5.seconds)
    .notificationOperationTimeout(8.seconds)
    .notificationResponseTimeout(12.seconds)
    .companionAssociationTimeout(20.seconds)
    .preferredMtu(247)
    .preferredConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
    .preferredPhy(
        txPhy = BluetoothDevice.PHY_LE_2M_MASK,
        rxPhy = BluetoothDevice.PHY_LE_2M_MASK,
        phyOptions = BluetoothDevice.PHY_OPTION_NO_PREFERRED,
    )
    .storeBondedConnectionMetadata(true)
    .autoReconnectOnUnexpectedDisconnect(true)
    .build()
```

## BLE Scan

```kotlin
val bledroid = Bledroid(context)

val job = lifecycleScope.launch {
    bledroid.bleScanner.scan().collect { result ->
        Log.d("Bluetooth", "${result.device.name} ${result.device.address} rssi=${result.rssi}")
    }
}

// Stop scanning by cancelling the collection job.
job.cancel()
```

## Companion Device Manager (CDM)

Build an association request for BLE companion pairing and ask the system for an `IntentSender`:

```kotlin
import com.bledroid.companion.BleCompanionAssociationRequests
import kotlin.time.Duration.Companion.seconds

val request = BleCompanionAssociationRequests.ble(
    singleDevice = true,
    serviceUuid = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb"),
)

val intentSender = bledroid.requestCompanionAssociation(
    request = request,
    timeout = 20.seconds,
)
```

Launch the returned `IntentSender` with `ActivityResultContracts.StartIntentSenderForResult()` to show system association UI:

```kotlin
val launcher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
    // Handle association result in result.data
}

launcher.launch(IntentSenderRequest.Builder(intentSender).build())
```

You can also check if CDM is available on the current device:

```kotlin
val available = bledroid.companionDeviceManager.isAvailable()
```

If you omit the timeout in `requestCompanionAssociation(...)`, the builder default
`companionAssociationTimeout(...)` is used.

## Bluetooth Broadcast Events

```kotlin
val bledroid = Bledroid(context)

val job = lifecycleScope.launch {
    bledroid.eventMonitor.events().collect { event ->
        when (event) {
            is BluetoothBroadcastEvent.AdapterStateChanged -> {
                Log.d("Bluetooth", "adapter=${event.state} previous=${event.previousState}")
            }
            is BluetoothBroadcastEvent.BondStateChanged -> {
                Log.d("Bluetooth", "bond ${event.device.address} -> ${event.state}")
            }
            is BluetoothBroadcastEvent.DeviceFound -> {
                Log.d("Bluetooth", "found ${event.device.address} rssi=${event.rssi}")
            }
            BluetoothBroadcastEvent.DiscoveryStarted -> Unit
            BluetoothBroadcastEvent.DiscoveryFinished -> Unit
            else -> Unit
        }
    }
}
```

## BLE GATT

```kotlin
val client = Bledroid(context).client() // singleton per Bledroid instance

lifecycleScope.launch {
    client.connect("AA:BB:CC:DD:EE:FF")
    val batteryService = client.discoverServices()
        .first { it.uuid == UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb") }

    val batteryLevelCharacteristic = batteryService.getCharacteristic(
        UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    )

    val batteryLevel = client.read(batteryLevelCharacteristic)

    client.notifications(batteryLevelCharacteristic).collect { value ->
        Log.d("Bluetooth", "notification=${value.joinToString()}")
    }
}
```

All timeout parameters are `Duration` values:

```kotlin
import kotlin.time.Duration.Companion.seconds

client.connect("AA:BB:CC:DD:EE:FF", timeout = 15.seconds)
client.read(batteryLevelCharacteristic, timeout = 5.seconds)
client.write(commandCharacteristic, commandBytes, timeout = 5.seconds)
```

For command-style characteristics that notify or indicate a response on the same characteristic after a write:

```kotlin
val response = client.writeAndObserveNotifications(
    characteristic = commandCharacteristic,
    value = commandBytes,
    operationTimeout = 5.seconds,
    responseTimeout = 10.seconds,
).first()
```

For commands that must be split into multiple BLE packets:

```kotlin
val response = client.writePacketsAndObserveNotifications(
    characteristic = commandCharacteristic,
    packets = listOf(headerBytes, bodyBytes, checksumBytes),
    operationTimeout = 5.seconds,
    responseTimeout = 10.seconds,
).first()
```

The notification flow is registered before packets are written. The response timeout starts after the final packet write completes.

If the device emits intermediate acknowledgements, use Flow operators to select the final response:

```kotlin
val response = client.writePacketsAndObserveNotifications(
    characteristic = commandCharacteristic,
    packets = packets,
)
    .filter { value -> value.firstOrNull() == FINAL_RESPONSE_CODE }
    .first()
```

Write state is tracked per characteristic:

```kotlin
val commandCharacteristicId = BleCharacteristicId(
    serviceUuid = commandCharacteristic.service.uuid,
    characteristicUuid = commandCharacteristic.uuid,
)

client.characteristicWriteStates.collect { writingByCharacteristic ->
    val isWritingCommand = writingByCharacteristic[commandCharacteristicId] == true
}
```

Call `client.close()` when the connection is no longer needed.

Connection behavior notes:
- `client()` returns a single lazily created `BleClient` per `Bledroid` instance.
- Overlapping `connect()` calls are guarded so only one connect attempt runs at a time.
- Calling `connect()` when already connected is a no-op and returns the currently connected device info.
- `disconnect()` is idempotent while a disconnect is already in progress.
- Calling `disconnect()` when not connected is ignored (no-op).
- Writes are serialized per characteristic, while writes to different characteristics can proceed concurrently.

## Reconnect Metadata

When `storeBondedConnectionMetadata(true)` is enabled (default), the library stores bonded-device connection metadata in an encrypted local table.

On unexpected disconnect, if `autoReconnectOnUnexpectedDisconnect(true)` is enabled (default), the client can use stored metadata to attempt reconnect automatically.

Reconnect behavior:
- The client waits 2 seconds between reconnect attempts.
- Only one reconnect loop can run at a time.
- Reconnect attempts stop when the app process stops or when a manual disconnect is requested.
