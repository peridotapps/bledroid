package com.bledroid.ble

import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import com.bledroid.core.BleScanResult
import kotlinx.coroutines.flow.Flow

/** Scans for BLE advertisements and emits results as a cold Flow. */
interface BleScanner {
    fun scan(
        filters: List<ScanFilter> = emptyList(),
        settings: ScanSettings = defaultScanSettings(),
    ): Flow<BleScanResult>

    companion object {
        fun defaultScanSettings(): ScanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
    }
}
