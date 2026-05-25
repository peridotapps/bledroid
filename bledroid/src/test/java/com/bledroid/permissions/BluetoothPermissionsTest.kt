package com.bledroid.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothPermissionsTest {
    @Test
    fun scanPermissionsUseBluetoothScanOnAndroid12AndNewer() {
        assertArrayEquals(
            arrayOf(Manifest.permission.BLUETOOTH_SCAN),
            BluetoothPermissions.requiredRuntimePermissionsForScan(Build.VERSION_CODES.S),
        )
    }

    @Test
    fun scanPermissionsUseLocationBeforeAndroid12() {
        assertArrayEquals(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            BluetoothPermissions.requiredRuntimePermissionsForScan(Build.VERSION_CODES.R),
        )
    }

    @Test
    fun connectPermissionsUseBluetoothConnectOnAndroid12AndNewer() {
        assertArrayEquals(
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
            BluetoothPermissions.requiredRuntimePermissionsForConnect(Build.VERSION_CODES.S),
        )
    }

    @Test
    fun connectPermissionsAreEmptyBeforeAndroid12() {
        assertArrayEquals(
            emptyArray<String>(),
            BluetoothPermissions.requiredRuntimePermissionsForConnect(Build.VERSION_CODES.R),
        )
    }

    @Test
    fun missingRuntimePermissionsReturnsOnlyDeniedPermissions() {
        val context = mockk<Context>()
        every { context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) } returns PackageManager.PERMISSION_GRANTED
        every { context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) } returns PackageManager.PERMISSION_DENIED

        assertArrayEquals(
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
            BluetoothPermissions.missingRuntimePermissions(
                context = context,
                scan = true,
                connect = true,
                sdkInt = Build.VERSION_CODES.S,
            ),
        )
        verify(exactly = 1) { context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) }
        verify(exactly = 1) { context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) }
    }

    @Test
    fun hasRuntimePermissionsReturnsTrueWhenNoPermissionsAreMissing() {
        val context = mockk<Context>()
        every { context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) } returns PackageManager.PERMISSION_GRANTED
        every { context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) } returns PackageManager.PERMISSION_GRANTED

        assertTrue(
            BluetoothPermissions.hasRuntimePermissions(
                context = context,
                sdkInt = Build.VERSION_CODES.S,
            ),
        )
    }

    @Test
    fun hasRuntimePermissionsReturnsFalseWhenAnyPermissionIsMissing() {
        val context = mockk<Context>()
        every { context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) } returns PackageManager.PERMISSION_DENIED
        every { context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) } returns PackageManager.PERMISSION_GRANTED

        assertFalse(
            BluetoothPermissions.hasRuntimePermissions(
                context = context,
                sdkInt = Build.VERSION_CODES.S,
            ),
        )
    }

    @Test
    fun defaultArgumentOverloadsAreCovered() {
        val context = mockk<Context>()
        every { context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) } returns PackageManager.PERMISSION_GRANTED
        every { context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) } returns PackageManager.PERMISSION_GRANTED

        val required = BluetoothPermissions.requiredRuntimePermissions()
        assertArrayEquals(
            BluetoothPermissions.requiredRuntimePermissions(
                scan = true,
                connect = true,
                sdkInt = Build.VERSION.SDK_INT,
            ),
            required,
        )
        assertArrayEquals(
            BluetoothPermissions.requiredRuntimePermissionsForScan(Build.VERSION.SDK_INT),
            BluetoothPermissions.requiredRuntimePermissionsForScan(),
        )
        assertArrayEquals(
            BluetoothPermissions.requiredRuntimePermissionsForConnect(Build.VERSION.SDK_INT),
            BluetoothPermissions.requiredRuntimePermissionsForConnect(),
        )
        assertArrayEquals(
            emptyArray<String>(),
            BluetoothPermissions.missingRuntimePermissions(context),
        )
        assertTrue(BluetoothPermissions.hasRuntimePermissions(context))
    }
}
