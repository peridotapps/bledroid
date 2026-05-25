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

class BledroidTest {
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

        val bledroid = Bledroid(context)

        assertNotNull(bledroid.bleScanner)
        assertNotNull(bledroid.eventMonitor)
        assertNotNull(bledroid.client())
        assertSame(bledroid.client(), bledroid.client())
        assertTrue(bledroid.isBluetoothAvailable())
        assertTrue(bledroid.isBluetoothEnabled())

        val bondedDevices = bledroid.bondedDevices()
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

        val bledroid = Bledroid(context)

        assertFalse(bledroid.isBluetoothAvailable())
        assertFalse(bledroid.isBluetoothEnabled())
        assertTrue(bledroid.bondedDevices().isEmpty())
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

        val bledroid = Bledroid(context)

        assertTrue(bledroid.isBluetoothAvailable())
        assertFalse(bledroid.isBluetoothEnabled())
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

        Bledroid.initialize(context)
        val bledroid = Bledroid()

        assertTrue(bledroid.isBluetoothAvailable())
        assertTrue(bledroid.isBluetoothEnabled())
    }
}
