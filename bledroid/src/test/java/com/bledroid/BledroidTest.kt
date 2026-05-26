package com.bledroid

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import com.bledroid.core.BluetoothBondState
import com.bledroid.core.BluetoothTransport
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BleDroidTest {
    @Test
    fun newClientsAndHelpersWorkWhenBluetoothManagerIsAvailable() {
        val context = mockk<Context>()
        val bluetoothManager = mockk<BluetoothManager>()
        val adapter = mockk<BluetoothAdapter>()
        val bondedDevice = mockk<BluetoothDevice>()

        every { context.applicationContext } returns context
        every { context.getSystemService(BluetoothManager::class.java) } returns bluetoothManager
        every { bluetoothManager.adapter } returns adapter
        every { adapter.isEnabled } returns true
        every { adapter.bondedDevices } returns setOf(bondedDevice)
        every { bondedDevice.name } returns "Thermometer"
        every { bondedDevice.address } returns "00:11:22:33:44:55"
        every { bondedDevice.type } returns BluetoothDevice.DEVICE_TYPE_DUAL
        every { bondedDevice.bondState } returns BluetoothDevice.BOND_BONDED

        val bleDroid = BleDroid(context)

        assertNotNull(bleDroid.bleScanner)
        assertNotNull(bleDroid.eventMonitor)
        assertNotNull(bleDroid.companionDeviceManager)
        assertNotNull(bleDroid.client())
        assertSame(bleDroid.client(), bleDroid.client())
        assertTrue(bleDroid.isBluetoothAvailable())
        assertTrue(bleDroid.isBluetoothEnabled())

        val bondedDevices = bleDroid.bondedDevices()
        assertEquals(1, bondedDevices.size)
        val bonded = bondedDevices.first()
        assertEquals("Thermometer", bonded.name)
        assertEquals("00:11:22:33:44:55", bonded.address)
        assertEquals(BluetoothTransport.Dual, bonded.transport)
        assertEquals(BluetoothBondState.Bonded, bonded.bondState)
    }

    @Test
    fun availabilityAndBondedDevicesFailClosedWhenBluetoothUnavailable() {
        val context = mockk<Context>()
        every { context.applicationContext } returns context
        every { context.getSystemService(BluetoothManager::class.java) } returns null

        val bleDroid = BleDroid(context)

        assertFalse(bleDroid.isBluetoothAvailable())
        assertFalse(bleDroid.isBluetoothEnabled())
        assertTrue(bleDroid.bondedDevices().isEmpty())
    }

    @Test
    fun isBluetoothEnabledReturnsFalseWhenAdapterDisabled() {
        val context = mockk<Context>()
        val bluetoothManager = mockk<BluetoothManager>()
        val adapter = mockk<BluetoothAdapter>()
        every { context.applicationContext } returns context
        every { context.getSystemService(BluetoothManager::class.java) } returns bluetoothManager
        every { bluetoothManager.adapter } returns adapter
        every { adapter.isEnabled } returns false

        val bleDroid = BleDroid(context)

        assertTrue(bleDroid.isBluetoothAvailable())
        assertFalse(bleDroid.isBluetoothEnabled())
    }

    @Test
    fun noArgConstructorUsesInitializedApplicationContext() {
        val context = mockk<Context>()
        val bluetoothManager = mockk<BluetoothManager>()
        val adapter = mockk<BluetoothAdapter>()
        every { context.applicationContext } returns context
        every { context.getSystemService(BluetoothManager::class.java) } returns bluetoothManager
        every { bluetoothManager.adapter } returns adapter
        every { adapter.isEnabled } returns true
        every { adapter.bondedDevices } returns emptySet()

        BleDroid.initialize(context)
        val bleDroid = BleDroid()

        assertTrue(bleDroid.isBluetoothAvailable())
        assertTrue(bleDroid.isBluetoothEnabled())
    }
}
