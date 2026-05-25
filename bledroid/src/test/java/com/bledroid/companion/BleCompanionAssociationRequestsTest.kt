package com.bledroid.companion

import android.companion.AssociationRequest
import java.util.UUID
import java.util.regex.Pattern
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BleCompanionAssociationRequestsTest {
    @Test
    fun bleBuildsAssociationRequest() {
        val request: AssociationRequest = BleCompanionAssociationRequests.ble(
            singleDevice = true,
            deviceNamePattern = Pattern.compile("Sensor.*"),
            serviceUuid = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb"),
        )

        assertTrue(request.isSingleDevice)
        assertTrue(request.toString().isNotEmpty())
    }
}
