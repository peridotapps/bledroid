package com.bledroid.core

data class BluetoothBroadcastEventAclConnected(
    val device: BluetoothDeviceInfo,
) : BluetoothBroadcastEvent
