package com.bledroid.ble

internal interface BondedConnectionStore {
    fun upsert(connection: BondedDeviceConnection)

    fun findByAddress(address: String): BondedDeviceConnection?

    fun deleteByAddress(address: String)
}
