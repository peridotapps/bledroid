package com.bledroid.core

data class BluetoothBroadcastEventScanModeChanged(
    val mode: BluetoothScanMode,
    val previousMode: BluetoothScanMode,
) : BluetoothBroadcastEvent
