package com.bledroid.core

import android.bluetooth.BluetoothDevice
import java.util.UUID

/** Result emitted by BLE scanning. */
data class BleScanResult(
    val device: BluetoothDeviceInfo,
    val rssi: Int,
    val serviceUuids: List<UUID>,
    val manufacturerData: Map<Int, ByteArray>,
    val rawDevice: BluetoothDevice,
)
