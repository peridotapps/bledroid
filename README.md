# Bledroid

Bledroid is an Android Bluetooth library for connecting to and communicating with devices over BLE GATT.

## Capabilities

- BLE scanning with `Flow<BleScanResult>`.
- BLE GATT connect, service discovery, characteristic read/write, and notifications.
- Bluetooth broadcast monitoring for adapter state, bond changes, discovery, device found, ACL connect/disconnect, pairing requests, scan mode, and local name changes.
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
val client = Bledroid(context).newBleClient()

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

For command-style characteristics that notify or indicate a response on the same characteristic after a write:

```kotlin
val response = client.writeAndObserveNotifications(
    characteristic = commandCharacteristic,
    value = commandBytes,
).first()
```

For commands that must be split into multiple BLE packets:

```kotlin
val response = client.writePacketsAndObserveNotifications(
    characteristic = commandCharacteristic,
    packets = listOf(headerBytes, bodyBytes, checksumBytes),
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
