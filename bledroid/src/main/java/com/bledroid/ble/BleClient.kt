package com.bledroid.ble

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import com.bledroid.core.BleCharacteristicId
import com.bledroid.core.BluetoothConnectionState
import com.bledroid.core.BluetoothDeviceInfo
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

/** BLE GATT client with serialized operations and Flow-based notifications. */
interface BleClient : AutoCloseable {
    val connectionState: StateFlow<BluetoothConnectionState>
    val characteristicWriteStates: StateFlow<Map<BleCharacteristicId, Boolean>>

    suspend fun connect(
        address: String,
        autoConnect: Boolean? = null,
        timeout: Duration? = null,
    ): BluetoothDeviceInfo

    suspend fun discoverServices(
        timeout: Duration? = null,
    ): List<BluetoothGattService>

    suspend fun read(
        characteristic: BluetoothGattCharacteristic,
        timeout: Duration? = null,
    ): ByteArray

    @Deprecated("Pass the discovered BluetoothGattCharacteristic object instead.")
    suspend fun read(
        service: BluetoothGattService,
        characteristicUuid: UUID,
        timeout: Duration? = null,
    ): ByteArray

    @Deprecated("Pass the discovered BluetoothGattCharacteristic object instead.")
    suspend fun read(
        id: BleCharacteristicId,
        timeout: Duration? = null,
    ): ByteArray

    suspend fun readRssi(
        timeout: Duration? = null,
    ): Int

    suspend fun write(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
        timeout: Duration? = null,
    )

    fun writeAndObserveNotifications(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
        operationTimeout: Duration? = null,
        responseTimeout: Duration? = null,
        disableNotificationsAfterResponse: Boolean = true,
    ): Flow<ByteArray>

    fun writePacketsAndObserveNotifications(
        characteristic: BluetoothGattCharacteristic,
        packets: Collection<ByteArray>,
        writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
        operationTimeout: Duration? = null,
        responseTimeout: Duration? = null,
        disableNotificationsAfterResponse: Boolean = true,
    ): Flow<ByteArray>

    @Deprecated("Pass the discovered BluetoothGattCharacteristic object instead.")
    suspend fun write(
        service: BluetoothGattService,
        characteristicUuid: UUID,
        value: ByteArray,
        writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
        timeout: Duration? = null,
    )

    @Deprecated("Pass the discovered BluetoothGattCharacteristic object instead.")
    suspend fun write(
        id: BleCharacteristicId,
        value: ByteArray,
        writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
        timeout: Duration? = null,
    )

    suspend fun enableNotifications(
        characteristic: BluetoothGattCharacteristic,
        enabled: Boolean,
        timeout: Duration? = null,
    )

    @Deprecated("Pass the discovered BluetoothGattCharacteristic object instead.")
    suspend fun enableNotifications(
        service: BluetoothGattService,
        characteristicUuid: UUID,
        enabled: Boolean,
        timeout: Duration? = null,
    )

    @Deprecated("Pass the discovered BluetoothGattCharacteristic object instead.")
    suspend fun enableNotifications(
        id: BleCharacteristicId,
        enabled: Boolean,
        timeout: Duration? = null,
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
