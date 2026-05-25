package com.bledroid.core

import java.util.UUID

/** A characteristic identifier that is stable across GATT rediscovery. */
data class BleCharacteristicId(
    val serviceUuid: UUID,
    val characteristicUuid: UUID,
)
