package com.bledroid.core

/** Permission-safe summary of a remote Bluetooth device. */
data class BluetoothDeviceInfo(
    val name: String?,
    val address: String,
    val transport: BluetoothTransport,
    val bondState: BluetoothBondState,
)
