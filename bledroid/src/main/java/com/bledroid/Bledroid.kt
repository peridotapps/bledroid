package com.bledroid

import android.annotation.SuppressLint
import android.content.Context
import com.bledroid.ble.BleClient
import com.bledroid.ble.BleClientImpl
import com.bledroid.ble.BleScanner
import com.bledroid.ble.BleScannerImpl
import com.bledroid.core.BluetoothDeviceInfo
import com.bledroid.core.BluetoothEventMonitor
import com.bledroid.core.BluetoothEventMonitorImpl
import com.bledroid.core.bluetoothManager
import com.bledroid.core.requireAdapter
import com.bledroid.core.toDeviceInfo

/** Entry point for creating Bluetooth scanners and clients. */
class Bledroid(context: Context) {
    private val appContext = context.applicationContext

    val bleScanner: BleScanner = BleScannerImpl(appContext)
    val eventMonitor: BluetoothEventMonitor = BluetoothEventMonitorImpl(appContext)

    fun newBleClient(): BleClient = BleClientImpl(appContext)

    fun isBluetoothAvailable(): Boolean = runCatching {
        appContext.bluetoothManager().requireAdapter()
    }.isSuccess

    fun isBluetoothEnabled(): Boolean = runCatching {
        appContext.bluetoothManager().requireAdapter().isEnabled
    }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    fun bondedDevices(): Set<BluetoothDeviceInfo> = runCatching {
        appContext.bluetoothManager()
            .requireAdapter()
            .bondedDevices
            .map { it.toDeviceInfo() }
            .toSet()
    }.getOrDefault(emptySet())
}
