package com.bledroid.ble

import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.SparseArray
import com.bledroid.core.BleScanResult
import com.bledroid.core.BluetoothUnavailableException
import com.bledroid.core.MissingBluetoothPermissionException
import com.bledroid.core.bluetoothManager
import com.bledroid.core.requireAdapter
import com.bledroid.core.requireEnabled
import com.bledroid.core.toDeviceInfo
import com.bledroid.permissions.BluetoothPermissions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import java.util.UUID

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

@SuppressLint("MissingPermission")
internal class BleScannerImpl(context: Context) : BleScanner {
    private val appContext = context.applicationContext

    override fun scan(
        filters: List<ScanFilter>,
        settings: ScanSettings,
    ): Flow<BleScanResult> = callbackFlow {
        val missingPermissions = BluetoothPermissions.missingRuntimePermissions(
            context = appContext,
            scan = true,
            connect = false,
        )
        if (missingPermissions.isNotEmpty()) {
            close(
                MissingBluetoothPermissionException(
                    "Missing Bluetooth scan permission(s): ${missingPermissions.joinToString()}."
                ),
            )
            return@callbackFlow
        }

        val adapter = appContext.bluetoothManager().requireAdapter()
        adapter.requireEnabled()

        val scanner = adapter.bluetoothLeScanner
            ?: throw BluetoothUnavailableException("BLE scanning is unavailable on this device.")

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(result.toBleScanResult())
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { trySend(it.toBleScanResult()) }
            }

            override fun onScanFailed(errorCode: Int) {
                close(BluetoothUnavailableException("BLE scan failed with code $errorCode."))
            }
        }

        try {
            scanner.startScan(filters, settings, callback)
        } catch (error: SecurityException) {
            close(
                MissingBluetoothPermissionException(
                    "Missing Bluetooth scan permission. Request BluetoothPermissions.requiredRuntimePermissionsForScan().",
                    error,
                ),
            )
        }

        awaitClose {
            runCatching { scanner.stopScan(callback) }
        }
    }.flowOn(Dispatchers.Main.immediate)

}

private fun ScanResult.toBleScanResult(): BleScanResult {
    val record = scanRecord
    return BleScanResult(
        device = device.toDeviceInfo(record?.deviceName),
        rssi = rssi,
        serviceUuids = record?.serviceUuids.toUuidList(),
        manufacturerData = record?.manufacturerSpecificData.toMap(),
        rawDevice = device,
    )
}

private fun List<ParcelUuid>?.toUuidList(): List<UUID> =
    this?.map { it.uuid }.orEmpty()

private fun SparseArray<ByteArray>?.toMap(): Map<Int, ByteArray> {
    if (this == null) return emptyMap()
    return buildMap {
        for (index in 0 until size()) {
            put(keyAt(index), valueAt(index).copyOf())
        }
    }
}
