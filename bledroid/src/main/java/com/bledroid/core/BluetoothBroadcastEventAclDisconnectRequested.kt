package com.bledroid.core

data class BluetoothBroadcastEventAclDisconnectRequested(
    val device: BluetoothDeviceInfo,
) : BluetoothBroadcastEvent
