package com.bledroid.core

import android.bluetooth.BluetoothAdapter

internal fun Int.toBluetoothAdapterPowerState(): BluetoothAdapterPowerState = when (this) {
    BluetoothAdapter.STATE_OFF -> BluetoothAdapterPowerState.Off
    BluetoothAdapter.STATE_TURNING_ON -> BluetoothAdapterPowerState.TurningOn
    BluetoothAdapter.STATE_ON -> BluetoothAdapterPowerState.On
    BluetoothAdapter.STATE_TURNING_OFF -> BluetoothAdapterPowerState.TurningOff
    else -> BluetoothAdapterPowerState.Unknown
}

internal fun Int.toBluetoothScanMode(): BluetoothScanMode = when (this) {
    BluetoothAdapter.SCAN_MODE_NONE -> BluetoothScanMode.None
    BluetoothAdapter.SCAN_MODE_CONNECTABLE -> BluetoothScanMode.Connectable
    BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE -> BluetoothScanMode.ConnectableDiscoverable
    else -> BluetoothScanMode.Unknown
}
