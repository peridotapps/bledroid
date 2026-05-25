package com.bledroid.core

data class BluetoothBroadcastEventAclDisconnected(
    val device: BluetoothDeviceInfo,
) : BluetoothBroadcastEvent
