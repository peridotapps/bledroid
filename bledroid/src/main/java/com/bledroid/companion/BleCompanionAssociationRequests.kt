package com.bledroid.companion

import android.annotation.SuppressLint
import android.bluetooth.le.ScanFilter
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.os.ParcelUuid
import java.util.UUID
import java.util.regex.Pattern

object BleCompanionAssociationRequests {
    @SuppressLint("MissingPermission")
    fun createRequest(
        singleDevice: Boolean = true,
        deviceNamePattern: Pattern? = null,
        serviceUuid: UUID? = null,
    ): AssociationRequest {
        val scanFilterBuilder = ScanFilter.Builder()
        if (serviceUuid != null) {
            val parcelUuid = ParcelUuid(serviceUuid)
            scanFilterBuilder.setServiceUuid(parcelUuid)
        }

        val deviceFilterBuilder = BluetoothLeDeviceFilter.Builder()
        if (deviceNamePattern != null) {
            deviceFilterBuilder.setNamePattern(deviceNamePattern)
        }
        deviceFilterBuilder.setScanFilter(scanFilterBuilder.build())

        return AssociationRequest.Builder()
            .setSingleDevice(singleDevice)
            .addDeviceFilter(deviceFilterBuilder.build())
            .build()
    }
}
