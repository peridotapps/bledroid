package com.bledroid.ble

internal data class BlePreferredPhy(
    val txPhy: Int,
    val rxPhy: Int,
    val phyOptions: Int,
)
