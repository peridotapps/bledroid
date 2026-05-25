package com.bledroid.core

data class BluetoothBroadcastEventPairingRequest(
    val device: BluetoothDeviceInfo,
    val pairingVariant: BluetoothPairingVariant,
) : BluetoothBroadcastEvent
