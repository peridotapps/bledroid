package com.bledroid.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BluetoothErrorsTest {
    @Test
    fun bluetoothUnavailableExceptionUsesDefaultMessage() {
        val exception = BluetoothUnavailableException()
        assertEquals("Bluetooth is not available on this device.", exception.message)
    }

    @Test
    fun bluetoothUnavailableExceptionCarriesCustomMessageAndCause() {
        val cause = IllegalStateException("boom")
        val exception = BluetoothUnavailableException("custom", cause)
        assertEquals("custom", exception.message)
        assertSame(cause, exception.cause)
    }

    @Test
    fun bluetoothDisabledExceptionUsesDefaultMessage() {
        val exception = BluetoothDisabledException()
        assertEquals("Bluetooth is disabled.", exception.message)
    }

    @Test
    fun missingPermissionExceptionCarriesMessageAndCause() {
        val cause = SecurityException("denied")
        val exception = MissingBluetoothPermissionException("missing", cause)
        assertEquals("missing", exception.message)
        assertSame(cause, exception.cause)
    }

    @Test
    fun notConnectedExceptionUsesDefaultMessage() {
        val exception = NotConnectedException()
        assertEquals("No Bluetooth connection is active.", exception.message)
    }

    @Test
    fun gattExceptionIncludesStatusAndOperation() {
        val cause = RuntimeException("gatt")
        val exception = BluetoothGattException(status = 133, operation = "Read remote RSSI", cause = cause)
        assertEquals(133, exception.status)
        assertEquals("Read remote RSSI failed with GATT status 133.", exception.message)
        assertSame(cause, exception.cause)
    }
}
