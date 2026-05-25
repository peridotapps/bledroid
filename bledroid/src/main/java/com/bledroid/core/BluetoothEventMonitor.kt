package com.bledroid.core

import kotlinx.coroutines.flow.Flow

/**
 * Emits Bluetooth system broadcast events such as adapter state changes, discovery, and bond updates.
 */
interface BluetoothEventMonitor {
    fun events(): Flow<BluetoothBroadcastEvent>
}
