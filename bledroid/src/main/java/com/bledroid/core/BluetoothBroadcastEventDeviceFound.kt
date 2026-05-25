package com.bledroid.core

data class BluetoothBroadcastEventDeviceFound(
    val device: BluetoothDeviceInfo,
    val rssi: Short?,
) : BluetoothBroadcastEvent
