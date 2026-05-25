package com.bledroid.core

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class BluetoothSystemTest {
    @Test
    fun bluetoothManagerReturnsSystemServiceFromApplicationContext() {
        val appContext = mockk<Context>()
        val context = mockk<Context>()
        val manager = mockk<BluetoothManager>()
        every { context.applicationContext } returns appContext
        every { appContext.getSystemService(BluetoothManager::class.java) } returns manager

        assertSame(manager, context.bluetoothManager())
    }

    @Test
    fun bluetoothManagerThrowsWhenSystemServiceIsMissing() {
        val context = mockk<Context>()
        every { context.applicationContext } returns context
        every { context.getSystemService(BluetoothManager::class.java) } returns null

        try {
            context.bluetoothManager()
            fail("Expected missing BluetoothManager to throw.")
        } catch (error: BluetoothUnavailableException) {
            // Expected.
        }
    }

    @Test
    fun requireAdapterReturnsAdapterWhenPresent() {
        val adapter = mockk<BluetoothAdapter>()
        val manager = mockk<BluetoothManager>()
        every { manager.adapter } returns adapter

        assertSame(adapter, manager.requireAdapter())
    }

    @Test
    fun requireAdapterThrowsWhenAdapterIsMissing() {
        val manager = mockk<BluetoothManager>()
        every { manager.adapter } returns null

        try {
            manager.requireAdapter()
            fail("Expected missing BluetoothAdapter to throw.")
        } catch (error: BluetoothUnavailableException) {
            // Expected.
        }
    }

    @Test
    fun requireEnabledThrowsWhenAdapterIsDisabled() {
        val adapter = mockk<BluetoothAdapter>()
        every { adapter.isEnabled } returns false

        try {
            adapter.requireEnabled()
            fail("Expected disabled BluetoothAdapter to throw.")
        } catch (error: BluetoothDisabledException) {
            // Expected.
        }
    }

    @Test
    fun requireEnabledReturnsWhenAdapterIsEnabled() {
        val adapter = mockk<BluetoothAdapter>()
        every { adapter.isEnabled } returns true

        adapter.requireEnabled()
    }
}
