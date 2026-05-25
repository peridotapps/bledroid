package com.bledroid.ble

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import com.bledroid.core.BleCharacteristicId
import com.bledroid.core.BluetoothConnectionState
import com.bledroid.core.BluetoothDeviceInfo
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** BLE GATT client with serialized operations and Flow-based notifications. */
interface BleClient : AutoCloseable {
    val connectionState: StateFlow<BluetoothConnectionState>
    val characteristicWriteStates: StateFlow<Map<BleCharacteristicId, Boolean>>

    suspend fun connect(
        address: String,
        autoConnect: Boolean = false,
        timeoutMillis: Long = 15_000L,
    ): BluetoothDeviceInfo

    suspend fun discoverServices(
        timeoutMillis: Long = 10_000L,
    ): List<BluetoothGattService>

    suspend fun read(
        characteristic: BluetoothGattCharacteristic,
        timeoutMillis: Long = 10_000L,
    ): ByteArray

    @Deprecated("Pass the discovered BluetoothGattCharacteristic object instead.")
    suspend fun read(
        service: BluetoothGattService,
        characteristicUuid: UUID,
        timeoutMillis: Long = 10_000L,
    ): ByteArray

    @Deprecated("Pass the discovered BluetoothGattCharacteristic object instead.")
    suspend fun read(
        id: BleCharacteristicId,
        timeoutMillis: Long = 10_000L,
    ): ByteArray

    suspend fun readRssi(
        timeoutMillis: Long = 10_000L,
    ): Int

    suspend fun write(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
        timeoutMillis: Long = 10_000L,
    )

    fun writeAndObserveNotifications(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
        operationTimeoutMillis: Long = 10_000L,
        responseTimeoutMillis: Long = 10_000L,
        disableNotificationsAfterResponse: Boolean = true,
    ): Flow<ByteArray>

    fun writePacketsAndObserveNotifications(
        characteristic: BluetoothGattCharacteristic,
        packets: Collection<ByteArray>,
        writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
        operationTimeoutMillis: Long = 10_000L,
        responseTimeoutMillis: Long = 10_000L,
        disableNotificationsAfterResponse: Boolean = true,
    ): Flow<ByteArray>

    @Deprecated("Pass the discovered BluetoothGattCharacteristic object instead.")
    suspend fun write(
        service: BluetoothGattService,
        characteristicUuid: UUID,
        value: ByteArray,
        writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
        timeoutMillis: Long = 10_000L,
    )

    @Deprecated("Pass the discovered BluetoothGattCharacteristic object instead.")
    suspend fun write(
        id: BleCharacteristicId,
        value: ByteArray,
        writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
        timeoutMillis: Long = 10_000L,
    )

    suspend fun enableNotifications(
        characteristic: BluetoothGattCharacteristic,
        enabled: Boolean,
        timeoutMillis: Long = 10_000L,
    )

    @Deprecated("Pass the discovered BluetoothGattCharacteristic object instead.")
    suspend fun enableNotifications(
        service: BluetoothGattService,
        characteristicUuid: UUID,
        enabled: Boolean,
        timeoutMillis: Long = 10_000L,
    )

    @Deprecated("Pass the discovered BluetoothGattCharacteristic object instead.")
    suspend fun enableNotifications(
        id: BleCharacteristicId,
        enabled: Boolean,
        timeoutMillis: Long = 10_000L,
    )

    fun notifications(
        characteristic: BluetoothGattCharacteristic,
    ): Flow<ByteArray>

    @Deprecated("Pass the discovered BluetoothGattCharacteristic object instead.")
    fun notifications(
        service: BluetoothGattService,
        characteristicUuid: UUID,
    ): Flow<ByteArray>

    @Deprecated("Pass the discovered BluetoothGattCharacteristic object instead.")
    fun notifications(
        id: BleCharacteristicId,
    ): Flow<ByteArray>

    fun disconnect()
}
