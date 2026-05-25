package com.bledroid.permissions

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** Runtime permission helper for Bluetooth operations across Android versions. */
@SuppressLint("InlinedApi")
object BluetoothPermissions {
    fun requiredRuntimePermissions(
        scan: Boolean = true,
        connect: Boolean = true,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): Array<String> = buildSet {
        if (scan) addAll(requiredRuntimePermissionsForScan(sdkInt).asList())
        if (connect) addAll(requiredRuntimePermissionsForConnect(sdkInt).asList())
    }.toTypedArray()

    fun requiredRuntimePermissionsForScan(
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): Array<String> = when {
        sdkInt >= Build.VERSION_CODES.S -> arrayOf(Manifest.permission.BLUETOOTH_SCAN)
        sdkInt >= Build.VERSION_CODES.M -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        else -> emptyArray()
    }

    fun requiredRuntimePermissionsForConnect(
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): Array<String> = when {
        sdkInt >= Build.VERSION_CODES.S -> arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
        else -> emptyArray()
    }

    fun missingRuntimePermissions(
        context: Context,
        scan: Boolean = true,
        connect: Boolean = true,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): Array<String> = requiredRuntimePermissions(scan, connect, sdkInt).filterNot { permission ->
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }.toTypedArray()

    fun hasRuntimePermissions(
        context: Context,
        scan: Boolean = true,
        connect: Boolean = true,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): Boolean = missingRuntimePermissions(context, scan, connect, sdkInt).isEmpty()
}
