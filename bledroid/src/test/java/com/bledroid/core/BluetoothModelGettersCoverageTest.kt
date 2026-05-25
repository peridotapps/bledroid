package com.bledroid.core

import android.bluetooth.BluetoothDevice
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BluetoothModelGettersCoverageTest {
    @Test
    fun stateAndEventGettersAreCovered() {
        val deviceInfo = BluetoothDeviceInfo(
            name = "Sensor",
            address = "AA:BB:CC:DD:EE:FF",
            transport = BluetoothTransport.Ble,
            bondState = BluetoothBondState.Bonded,
        )
        val error = IllegalStateException("x")
        val connected = BluetoothConnectionStateConnected(deviceInfo)
        val failed = BluetoothConnectionStateFailed(error)
        assertEquals(deviceInfo, connected.device)
        assertSame(error, failed.error)

        val adapter = BluetoothBroadcastEventAdapterStateChanged(
            state = BluetoothAdapterPowerState.On,
            previousState = BluetoothAdapterPowerState.Off,
        )
        assertEquals(BluetoothAdapterPowerState.On, adapter.state)
        assertEquals(BluetoothAdapterPowerState.Off, adapter.previousState)

        val scanMode = BluetoothBroadcastEventScanModeChanged(
            mode = BluetoothScanMode.Connectable,
            previousMode = BluetoothScanMode.None,
        )
        assertEquals(BluetoothScanMode.Connectable, scanMode.mode)
        assertEquals(BluetoothScanMode.None, scanMode.previousMode)

        val found = BluetoothBroadcastEventDeviceFound(deviceInfo, (-40).toShort())
        assertEquals(deviceInfo, found.device)
        assertEquals((-40).toShort(), found.rssi)

        val bond = BluetoothBroadcastEventBondStateChanged(
            device = deviceInfo,
            state = BluetoothBondState.Bonded,
            previousState = BluetoothBondState.Bonding,
        )
        assertEquals(deviceInfo, bond.device)
        assertEquals(BluetoothBondState.Bonded, bond.state)
        assertEquals(BluetoothBondState.Bonding, bond.previousState)

        val pairing = BluetoothBroadcastEventPairingRequest(deviceInfo, BluetoothPairingVariant.Pin)
        assertEquals(deviceInfo, pairing.device)
        assertEquals(BluetoothPairingVariant.Pin, pairing.pairingVariant)

        val name = BluetoothBroadcastEventNameChanged("adapter")
        assertEquals("adapter", name.name)

        val aclConnected = BluetoothBroadcastEventAclConnected(deviceInfo)
        val aclDisconnected = BluetoothBroadcastEventAclDisconnected(deviceInfo)
        val aclDisconnectRequested = BluetoothBroadcastEventAclDisconnectRequested(deviceInfo)
        assertEquals(deviceInfo, aclConnected.device)
        assertEquals(deviceInfo, aclDisconnected.device)
        assertEquals(deviceInfo, aclDisconnectRequested.device)
    }

    @Test
    fun bleScanResultRawDeviceGetterIsCovered() {
        val rawDevice = mockk<BluetoothDevice>()
        every { rawDevice.address } returns "AA:BB:CC:DD:EE:11"
        val scanResult = BleScanResult(
            device = BluetoothDeviceInfo(
                name = null,
                address = "AA:BB:CC:DD:EE:11",
                transport = BluetoothTransport.Ble,
                bondState = BluetoothBondState.None,
            ),
            rssi = -55,
            serviceUuids = listOf(UUID.randomUUID()),
            manufacturerData = mapOf(1 to byteArrayOf(1, 2, 3)),
            rawDevice = rawDevice,
        )
        assertSame(rawDevice, scanResult.rawDevice)
    }
}
