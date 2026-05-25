package com.bledroid.core

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import java.util.UUID

/** Android transport family used by a remote device or connection. */
enum class BluetoothTransport {
    Unknown,
    Classic,
    Ble,
    Dual,
}

/** Bonding state reported by Android for a remote device. */
enum class BluetoothBondState {
    Unknown,
    None,
    Bonding,
    Bonded,
}

/** Permission-safe summary of a remote Bluetooth device. */
data class BluetoothDeviceInfo(
    val name: String?,
    val address: String,
    val transport: BluetoothTransport,
    val bondState: BluetoothBondState,
)

/** A characteristic identifier that is stable across GATT rediscovery. */
data class BleCharacteristicId(
    val serviceUuid: UUID,
    val characteristicUuid: UUID,
)

/** Result emitted by BLE scanning. */
data class BleScanResult(
    val device: BluetoothDeviceInfo,
    val rssi: Int,
    val serviceUuids: List<UUID>,
    val manufacturerData: Map<Int, ByteArray>,
    val rawDevice: BluetoothDevice,
)

sealed interface BluetoothConnectionState {
    data object Disconnected : BluetoothConnectionState
    data object Connecting : BluetoothConnectionState
    data class Connected(val device: BluetoothDeviceInfo) : BluetoothConnectionState
    data object Disconnecting : BluetoothConnectionState
    data class Failed(val error: Throwable) : BluetoothConnectionState
}

@SuppressLint("MissingPermission")
internal fun BluetoothDevice.toDeviceInfo(fallbackName: String? = null): BluetoothDeviceInfo {
    val safeName = runCatching { name }.getOrNull() ?: fallbackName
    val safeAddress = runCatching { address }.getOrNull() ?: "unknown"
    val safeType = runCatching { type }.getOrDefault(BluetoothDevice.DEVICE_TYPE_UNKNOWN)
    val safeBondState = runCatching { bondState }.getOrDefault(-1)

    return BluetoothDeviceInfo(
        name = safeName,
        address = safeAddress,
        transport = safeType.toBluetoothTransport(),
        bondState = safeBondState.toBluetoothBondState(),
    )
}

internal fun BluetoothGattCharacteristic.supportsNotify(): Boolean =
    properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0

internal fun BluetoothGattCharacteristic.supportsIndicate(): Boolean =
    properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0

private fun Int.toBluetoothTransport(): BluetoothTransport = when (this) {
    BluetoothDevice.DEVICE_TYPE_CLASSIC -> BluetoothTransport.Classic
    BluetoothDevice.DEVICE_TYPE_LE -> BluetoothTransport.Ble
    BluetoothDevice.DEVICE_TYPE_DUAL -> BluetoothTransport.Dual
    else -> BluetoothTransport.Unknown
}

internal fun Int.toBluetoothBondState(): BluetoothBondState = when (this) {
    BluetoothDevice.BOND_NONE -> BluetoothBondState.None
    BluetoothDevice.BOND_BONDING -> BluetoothBondState.Bonding
    BluetoothDevice.BOND_BONDED -> BluetoothBondState.Bonded
    else -> BluetoothBondState.Unknown
}
