package com.bledroid.core

data class BluetoothBroadcastEventBondStateChanged(
    val device: BluetoothDeviceInfo,
    val state: BluetoothBondState,
    val previousState: BluetoothBondState,
) : BluetoothBroadcastEvent
