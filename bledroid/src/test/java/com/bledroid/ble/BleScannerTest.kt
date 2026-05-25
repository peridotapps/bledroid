package com.bledroid.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.bluetooth.le.ScanFilter
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.SparseArray
import com.bledroid.core.BluetoothUnavailableException
import com.bledroid.core.MissingBluetoothPermissionException
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BleScannerTest {
    @Test
    fun scanFailsWhenPermissionsMissing() = runTest {
        val context = mockk<Context>()
        every { context.applicationContext } returns context
        every { context.checkSelfPermission(any()) } returns PackageManager.PERMISSION_DENIED

        val scanner: BleScanner = BleScannerImpl(context)

        try {
            scanner.scan().first()
            fail("Expected missing permissions to fail.")
        } catch (error: MissingBluetoothPermissionException) {
            assertTrue(error.message.orEmpty().contains("Missing Bluetooth scan permission"))
        }
    }

    @Test
    fun scanFailsWhenBluetoothLeScannerUnavailable() = runTest {
        val context = mockk<Context>()
        val manager = mockk<BluetoothManager>()
        val adapter = mockk<BluetoothAdapter>()

        every { context.applicationContext } returns context
        every { context.checkSelfPermission(any()) } answers {
            if (firstArg<String>() == Manifest.permission.BLUETOOTH_SCAN) {
                PackageManager.PERMISSION_GRANTED
            } else {
                PackageManager.PERMISSION_DENIED
            }
        }
        every { context.getSystemService(BluetoothManager::class.java) } returns manager
        every { manager.adapter } returns adapter
        every { adapter.isEnabled } returns true
        every { adapter.bluetoothLeScanner } returns null

        val scanner: BleScanner = BleScannerImpl(context)

        try {
            scanner.scan().first()
            fail("Expected missing BLE scanner to fail.")
        } catch (error: BluetoothUnavailableException) {
            assertEquals("BLE scanning is unavailable on this device.", error.message)
        }
    }

    @Test
    fun scanEmitsConvertedBleScanResult() = runTest {
        val context = mockk<Context>()
        val manager = mockk<BluetoothManager>()
        val adapter = mockk<BluetoothAdapter>()
        val leScanner = mockk<BluetoothLeScanner>()
        val callbackSlot = slot<ScanCallback>()

        every { context.applicationContext } returns context
        every { context.checkSelfPermission(any()) } answers {
            if (firstArg<String>() == Manifest.permission.BLUETOOTH_SCAN) {
                PackageManager.PERMISSION_GRANTED
            } else {
                PackageManager.PERMISSION_DENIED
            }
        }
        every { context.getSystemService(BluetoothManager::class.java) } returns manager
        every { manager.adapter } returns adapter
        every { adapter.isEnabled } returns true
        every { adapter.bluetoothLeScanner } returns leScanner
        every { leScanner.startScan(any<List<android.bluetooth.le.ScanFilter>>(), any(), capture(callbackSlot)) } just runs
        every { leScanner.stopScan(any<ScanCallback>()) } just runs

        val device = mockk<BluetoothDevice>()
        val record = mockk<ScanRecord>()
        val scanResult = mockk<ScanResult>()
        val manufacturerData = SparseArray<ByteArray>().apply {
            put(42, byteArrayOf(0x0A, 0x0B))
        }
        val serviceUuid = UUID.randomUUID()

        every { device.name } returns "Thermometer"
        every { device.address } returns "00:11:22:33:44:55"
        every { device.type } returns BluetoothDevice.DEVICE_TYPE_LE
        every { device.bondState } returns BluetoothDevice.BOND_NONE
        every { scanResult.device } returns device
        every { scanResult.rssi } returns -61
        every { scanResult.scanRecord } returns record
        every { record.deviceName } returns "Thermometer Advertisement"
        every { record.serviceUuids } returns listOf(ParcelUuid(serviceUuid))
        every { record.manufacturerSpecificData } returns manufacturerData

        val scanner: BleScanner = BleScannerImpl(context)
        val deferred = async {
            withTimeout(2_000) {
                scanner.scan().first()
            }
        }
        withTimeout(2_000) {
            while (!callbackSlot.isCaptured) {
                yield()
            }
        }
        callbackSlot.captured.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, scanResult)
        val result = deferred.await()

        assertEquals("Thermometer", result.device.name)
        assertEquals("00:11:22:33:44:55", result.device.address)
        assertEquals(-61, result.rssi)
        assertEquals(listOf(serviceUuid), result.serviceUuids)
        assertArrayEquals(byteArrayOf(0x0A, 0x0B), result.manufacturerData[42])
    }

    @Test
    fun scanEmitsBatchResultsAndHandlesNullRecordCollections() = runTest {
        val context = mockk<Context>()
        val manager = mockk<BluetoothManager>()
        val adapter = mockk<BluetoothAdapter>()
        val leScanner = mockk<BluetoothLeScanner>()
        val callbackSlot = slot<ScanCallback>()

        every { context.applicationContext } returns context
        every { context.checkSelfPermission(any()) } answers {
            if (firstArg<String>() == Manifest.permission.BLUETOOTH_SCAN) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
        }
        every { context.getSystemService(BluetoothManager::class.java) } returns manager
        every { manager.adapter } returns adapter
        every { adapter.isEnabled } returns true
        every { adapter.bluetoothLeScanner } returns leScanner
        every { leScanner.startScan(any<List<ScanFilter>>(), any(), capture(callbackSlot)) } just runs
        every { leScanner.stopScan(any<ScanCallback>()) } just runs

        val d1 = mockk<BluetoothDevice>()
        val d2 = mockk<BluetoothDevice>()
        every { d1.name } returns "A"
        every { d1.address } returns "00:00:00:00:00:01"
        every { d1.type } returns BluetoothDevice.DEVICE_TYPE_LE
        every { d1.bondState } returns BluetoothDevice.BOND_NONE
        every { d2.name } returns "B"
        every { d2.address } returns "00:00:00:00:00:02"
        every { d2.type } returns BluetoothDevice.DEVICE_TYPE_LE
        every { d2.bondState } returns BluetoothDevice.BOND_NONE

        val r1 = mockk<ScanResult>()
        val r2 = mockk<ScanResult>()
        every { r1.device } returns d1
        every { r1.rssi } returns -30
        every { r1.scanRecord } returns null
        every { r2.device } returns d2
        every { r2.rssi } returns -40
        every { r2.scanRecord } returns null

        val deferred = async { withTimeout(2_000) { (BleScannerImpl(context) as BleScanner).scan().take(2).toList() } }
        withTimeout(2_000) { while (!callbackSlot.isCaptured) yield() }
        callbackSlot.captured.onBatchScanResults(mutableListOf(r1, r2))
        val items = deferred.await()

        assertEquals(2, items.size)
        assertEquals("00:00:00:00:00:01", items[0].device.address)
        assertTrue(items[0].manufacturerData.isEmpty())
        assertTrue(items[0].serviceUuids.isEmpty())
        assertEquals("00:00:00:00:00:02", items[1].device.address)
    }

    @Test
    fun scanFailsWhenCallbackReportsScanFailure() = runTest {
        val context = mockk<Context>()
        val manager = mockk<BluetoothManager>()
        val adapter = mockk<BluetoothAdapter>()
        val leScanner = mockk<BluetoothLeScanner>()
        every { context.applicationContext } returns context
        every { context.checkSelfPermission(any()) } answers {
            if (firstArg<String>() == Manifest.permission.BLUETOOTH_SCAN) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
        }
        every { context.getSystemService(BluetoothManager::class.java) } returns manager
        every { manager.adapter } returns adapter
        every { adapter.isEnabled } returns true
        every { adapter.bluetoothLeScanner } returns leScanner
        every { leScanner.startScan(any<List<ScanFilter>>(), any<ScanSettings>(), any<ScanCallback>()) } answers {
            thirdArg<ScanCallback>().onScanFailed(5)
        }
        every { leScanner.stopScan(any<ScanCallback>()) } just runs

        try {
            (BleScannerImpl(context) as BleScanner).scan().first()
            fail("Expected scan failure.")
        } catch (error: BluetoothUnavailableException) {
            assertEquals("BLE scan failed with code 5.", error.message)
        }
    }
}
