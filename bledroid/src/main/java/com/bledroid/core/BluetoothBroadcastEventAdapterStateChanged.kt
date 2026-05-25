package com.bledroid.core

data class BluetoothBroadcastEventAdapterStateChanged(
    val state: BluetoothAdapterPowerState,
    val previousState: BluetoothAdapterPowerState,
) : BluetoothBroadcastEvent
