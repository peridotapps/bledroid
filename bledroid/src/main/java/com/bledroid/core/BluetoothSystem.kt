package com.bledroid.core

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context

internal fun Context.bluetoothManager(): BluetoothManager =
    applicationContext.getSystemService(BluetoothManager::class.java)
        ?: throw BluetoothUnavailableException()

internal fun BluetoothManager.requireAdapter(): BluetoothAdapter =
    adapter ?: throw BluetoothUnavailableException()

internal fun BluetoothAdapter.requireEnabled() {
    if (!isEnabled) throw BluetoothDisabledException()
}
