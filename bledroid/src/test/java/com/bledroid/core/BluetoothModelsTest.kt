package com.bledroid.core

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothModelsTest {
    @Test
    fun bluetoothDeviceMapsToDeviceInfo() {
        val device = mockk<BluetoothDevice>()
        every { device.name } returns "Sensor"
        every { device.address } returns "01:23:45:67:89:AB"
        every { device.type } returns BluetoothDevice.DEVICE_TYPE_LE
        every { device.bondState } returns BluetoothDevice.BOND_BONDED

        assertEquals(
            BluetoothDeviceInfo(
                name = "Sensor",
                address = "01:23:45:67:89:AB",
                transport = BluetoothTransport.Ble,
                bondState = BluetoothBondState.Bonded,
            ),
            device.toDeviceInfo(),
        )
    }

    @Test
    fun bluetoothDeviceUsesFallbacksWhenPropertiesAreUnavailable() {
        val device = mockk<BluetoothDevice>()
        every { device.name } throws SecurityException("missing permission")
        every { device.address } throws SecurityException("missing permission")
        every { device.type } throws SecurityException("missing permission")
        every { device.bondState } throws SecurityException("missing permission")

        assertEquals(
            BluetoothDeviceInfo(
                name = "Fallback",
                address = "unknown",
                transport = BluetoothTransport.Unknown,
                bondState = BluetoothBondState.Unknown,
            ),
            device.toDeviceInfo(fallbackName = "Fallback"),
        )
    }

    @Test
    fun characteristicPropertyHelpersDetectNotifyAndIndicate() {
        val notifyCharacteristic = mockk<BluetoothGattCharacteristic>()
        val indicateCharacteristic = mockk<BluetoothGattCharacteristic>()
        val plainCharacteristic = mockk<BluetoothGattCharacteristic>()

        every { notifyCharacteristic.properties } returns BluetoothGattCharacteristic.PROPERTY_NOTIFY
        every { indicateCharacteristic.properties } returns BluetoothGattCharacteristic.PROPERTY_INDICATE
        every { plainCharacteristic.properties } returns BluetoothGattCharacteristic.PROPERTY_READ

        assertTrue(notifyCharacteristic.supportsNotify())
        assertFalse(notifyCharacteristic.supportsIndicate())
        assertTrue(indicateCharacteristic.supportsIndicate())
        assertFalse(indicateCharacteristic.supportsNotify())
        assertFalse(plainCharacteristic.supportsNotify())
        assertFalse(plainCharacteristic.supportsIndicate())
    }
}
