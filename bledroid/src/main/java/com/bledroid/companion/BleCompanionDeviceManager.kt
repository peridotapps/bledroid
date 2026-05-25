package com.bledroid.companion

import android.companion.AssociationRequest
import android.content.IntentSender
import kotlin.time.Duration

/** Companion Device Manager support for BLE association flows. */
interface BleCompanionDeviceManager {
    fun isAvailable(): Boolean

    suspend fun requestAssociation(
        request: AssociationRequest,
        timeout: Duration,
    ): IntentSender
}
