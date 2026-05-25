package com.bledroid.ble

import kotlin.time.Duration

internal data class BondedDeviceConnection(
    val address: String,
    val autoConnect: Boolean,
    val updatedAt: Duration,
)
