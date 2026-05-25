package com.bledroid.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BleDeviceConfigurationCoverageTest {
    @Test
    fun gettersExposeConfiguredValues() {
        val config = BleDeviceConfiguration(
            deviceTypeTag = "sensor-v3",
            autoReconnectOnUnexpectedDisconnect = false,
        )

        assertEquals("sensor-v3", config.deviceTypeTag)
        assertFalse(config.autoReconnectOnUnexpectedDisconnect)
    }
}
