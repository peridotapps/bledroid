package com.bledroid.core

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic

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
