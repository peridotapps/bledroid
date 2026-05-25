package com.bledroid.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import com.bledroid.core.BleCharacteristicId
import com.bledroid.core.BluetoothConnectionState
import com.bledroid.core.BluetoothConnectionStateConnected
import com.bledroid.core.BluetoothConnectionStateConnecting
import com.bledroid.core.BluetoothConnectionStateDisconnected
import com.bledroid.core.BluetoothConnectionStateDisconnecting
import com.bledroid.core.BluetoothConnectionStateFailed
import com.bledroid.core.BluetoothDeviceInfo
import com.bledroid.core.BluetoothGattException
import com.bledroid.core.BluetoothUnavailableException
import com.bledroid.core.MissingBluetoothPermissionException
import com.bledroid.core.NotConnectedException
import com.bledroid.core.bluetoothManager
import com.bledroid.core.requireAdapter
import com.bledroid.core.requireEnabled
import com.bledroid.core.supportsIndicate
import com.bledroid.core.supportsNotify
import com.bledroid.core.toDeviceInfo
import com.bledroid.permissions.BluetoothPermissions
import java.util.UUID
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout

@SuppressLint("MissingPermission")
internal class BleClientImpl(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val bondedConnectionStore: BondedConnectionStore =
        EncryptedBondedConnectionStore(context.applicationContext),
    private val configuration: BleDeviceConfiguration = BleDeviceConfiguration(),
) : BleClient {
    private val appContext = context.applicationContext
    private val operationMutex = Mutex()
    private val characteristicWriteMutexes = ConcurrentHashMap<BleCharacteristicId, Mutex>()
    private val cleanupScope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _connectionState = MutableStateFlow<BluetoothConnectionState>(BluetoothConnectionStateDisconnected)
    override val connectionState: StateFlow<BluetoothConnectionState> = _connectionState.asStateFlow()

    private val _characteristicWriteStates = MutableStateFlow<Map<BleCharacteristicId, Boolean>>(emptyMap())
    override val characteristicWriteStates: StateFlow<Map<BleCharacteristicId, Boolean>> =
        _characteristicWriteStates.asStateFlow()

    @Volatile
    private var gatt: BluetoothGatt? = null

    @Volatile
    private var callback: GattCallback? = null
    private val manualDisconnectRequested = AtomicBoolean(false)
    private val allowAutoReconnect = AtomicBoolean(true)
    private val reconnectInProgress = AtomicBoolean(false)
    private val connectInProgress = AtomicBoolean(false)
    private val lastAutoConnect = AtomicBoolean(false)
    @Volatile
    private var reconnectJob: Job? = null

    override suspend fun connect(
        address: String,
        autoConnect: Boolean?,
        timeout: Duration?,
    ): BluetoothDeviceInfo {
        val connectedDevice = (_connectionState.value as? BluetoothConnectionStateConnected)?.device
        if (connectedDevice != null) {
            return connectedDevice
        }
        if (!connectInProgress.compareAndSet(false, true)) {
            throw BluetoothUnavailableException("A connection attempt is already in progress.")
        }
        val resolvedAutoConnect = autoConnect ?: configuration.defaultAutoConnect
        val resolvedTimeout = resolveTimeout(timeout, configuration.connectTimeout)
        manualDisconnectRequested.set(false)
        allowAutoReconnect.set(true)
        lastAutoConnect.set(resolvedAutoConnect)
        try {
            requireConnectPermission()
            val adapter = appContext.bluetoothManager().requireAdapter()
            adapter.requireEnabled()
            val device = adapter.getRemoteDevice(address)

            _connectionState.value = BluetoothConnectionStateConnecting

            return withTimeout(resolvedTimeout.inWholeMilliseconds) {
                suspendCancellableCoroutine { continuation ->
                    val gattCallback = GattCallback(device)
                    callback = gattCallback
                    gattCallback.setConnectContinuation(continuation)

                    val createdGatt = try {
                        device.connectGatt(appContext, resolvedAutoConnect, gattCallback, BluetoothDevice.TRANSPORT_LE)
                    } catch (error: SecurityException) {
                        val permissionError = MissingBluetoothPermissionException(
                            "Missing Bluetooth connect permission. Request BluetoothPermissions.requiredRuntimePermissionsForConnect().",
                            error,
                        )
                        _connectionState.value = BluetoothConnectionStateFailed(permissionError)
                        continuation.resumeWithException(permissionError)
                        return@suspendCancellableCoroutine
                    }

                    if (createdGatt == null) {
                        val error = BluetoothUnavailableException("Android returned a null BluetoothGatt.")
                        _connectionState.value = BluetoothConnectionStateFailed(error)
                        continuation.resumeWithException(error)
                        return@suspendCancellableCoroutine
                    }

                    gatt = createdGatt
                    continuation.invokeOnCancellation {
                        gattCallback.clearConnectContinuation()
                        closeGatt(createdGatt)
                        _connectionState.value = BluetoothConnectionStateDisconnected
                    }
                }
            }
        } finally {
            connectInProgress.set(false)
        }
    }

    override suspend fun discoverServices(
        timeout: Duration?,
    ): List<BluetoothGattService> = withGattOperation(
        resolveTimeout(timeout, configuration.discoverServicesTimeout),
    ) { activeGatt, activeCallback ->
        activeCallback.discoverServices(activeGatt)
    }

    override suspend fun read(
        characteristic: BluetoothGattCharacteristic,
        timeout: Duration?,
    ): ByteArray = withGattOperation(
        resolveTimeout(timeout, configuration.readTimeout),
    ) { activeGatt, activeCallback ->
        activeCallback.readCharacteristic(activeGatt, characteristic)
    }

    @Deprecated("Pass the discovered BluetoothGattCharacteristic object instead.")
    override suspend fun read(
        service: BluetoothGattService,
        characteristicUuid: UUID,
        timeout: Duration?,
    ): ByteArray = withGattOperation(
        resolveTimeout(timeout, configuration.readTimeout),
    ) { activeGatt, activeCallback ->
        val characteristic = service.requireCharacteristic(characteristicUuid)
        activeCallback.readCharacteristic(activeGatt, characteristic)
    }

    @Deprecated("Pass the discovered BluetoothGattCharacteristic object instead.")
    override suspend fun read(
        id: BleCharacteristicId,
        timeout: Duration?,
    ): ByteArray = withGattOperation(
        resolveTimeout(timeout, configuration.readTimeout),
    ) { activeGatt, activeCallback ->
        val characteristic = activeGatt.requireCharacteristic(id)
        activeCallback.readCharacteristic(activeGatt, characteristic)
    }

    override suspend fun readRssi(
        timeout: Duration?,
    ): Int = withGattOperation(
        resolveTimeout(timeout, configuration.rssiTimeout),
    ) { activeGatt, activeCallback ->
        activeCallback.readRemoteRssi(activeGatt)
    }

    override suspend fun write(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int,
        timeout: Duration?,
    ) = withCharacteristicWrite(characteristic) { activeGatt, activeCallback ->
        withTimeout(resolveTimeout(timeout, configuration.writeTimeout).inWholeMilliseconds) {
            activeCallback.writeCharacteristic(activeGatt, characteristic, value, writeType)
        }
    }

    override fun writeAndObserveNotifications(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int,
        operationTimeout: Duration?,
        responseTimeout: Duration?,
        disableNotificationsAfterResponse: Boolean,
    ): Flow<ByteArray> = writePacketsAndObserveNotifications(
        characteristic = characteristic,
        packets = listOf(value),
        writeType = writeType,
        operationTimeout = operationTimeout,
        responseTimeout = responseTimeout,
        disableNotificationsAfterResponse = disableNotificationsAfterResponse,
    )

    /**
     * Writes packets sequentially and emits notifications or indications after the final packet.
     * The notification flow is active before writes begin, but responseTimeout starts after the last write completes.
     */
    override fun writePacketsAndObserveNotifications(
        characteristic: BluetoothGattCharacteristic,
        packets: Collection<ByteArray>,
        writeType: Int,
        operationTimeout: Duration?,
        responseTimeout: Duration?,
        disableNotificationsAfterResponse: Boolean,
    ): Flow<ByteArray> {
        require(packets.isNotEmpty()) { "packets must contain at least one packet." }
        val resolvedOperationTimeout =
            resolveTimeout(operationTimeout, configuration.notificationOperationTimeout)
        val resolvedResponseTimeout =
            resolveTimeout(responseTimeout, configuration.notificationResponseTimeout)

        val listenerId = characteristic.toCharacteristicId()
        val packetCount = packets.size

        return callbackFlow {
            val activeCallback = callback
            if (activeCallback == null) {
                close(NotConnectedException())
                return@callbackFlow
            }

            val startedWrites = AtomicInteger(0)
            val completedWrites = AtomicInteger(0)
            val pendingFinalNotifications = ArrayDeque<ByteArray>()
            val pendingFinalNotificationsLock = Any()
            val responseTimeoutStarted = AtomicBoolean(false)
            var responseTimeoutJob: Job? = null

            fun startResponseTimeout() {
                if (responseTimeoutStarted.compareAndSet(false, true)) {
                    responseTimeoutJob = launch {
                        delay(resolvedResponseTimeout.inWholeMilliseconds)
                        close(BluetoothUnavailableException("Timed out waiting for a notification from characteristic ${characteristic.uuid}."))
                    }
                }
            }

            fun emitPendingFinalNotifications() {
                while (true) {
                    val pending = synchronized(pendingFinalNotificationsLock) {
                        if (pendingFinalNotifications.isEmpty()) null else pendingFinalNotifications.removeFirst()
                    } ?: break
                    trySend(pending)
                }
            }

            val listener: (ByteArray) -> Unit = { emittedValue ->
                val value = emittedValue.copyOf()
                when {
                    completedWrites.get() >= packetCount -> trySend(value)
                    startedWrites.get() >= packetCount && completedWrites.get() == packetCount - 1 -> {
                        synchronized(pendingFinalNotificationsLock) {
                            pendingFinalNotifications.addLast(value)
                        }
                    }
                }
            }

            activeCallback.addNotificationListener(listenerId, listener)
            val writer = launch {
                try {
                    withCharacteristicWrite(characteristic) { activeGatt, activeCallback ->
                        withTimeout(resolvedOperationTimeout.inWholeMilliseconds) {
                            activeCallback.setNotifications(activeGatt, characteristic, enabled = true)
                        }

                        packets.forEach { packet ->
                            startedWrites.incrementAndGet()
                            withTimeout(resolvedOperationTimeout.inWholeMilliseconds) {
                                activeCallback.writeCharacteristic(activeGatt, characteristic, packet, writeType)
                            }
                            if (completedWrites.incrementAndGet() == packetCount) {
                                emitPendingFinalNotifications()
                                startResponseTimeout()
                            }
                        }
                    }
                } catch (error: Throwable) {
                    close(error)
                }
            }

            awaitClose {
                writer.cancel()
                responseTimeoutJob?.cancel()
                activeCallback.removeNotificationListener(listenerId, listener)
                if (disableNotificationsAfterResponse && !activeCallback.hasNotificationListeners(listenerId)) {
                    cleanupScope.launch {
                        val activeGatt = gatt ?: return@launch
                        runCatching {
                            withTimeout(resolvedOperationTimeout.inWholeMilliseconds) {
                                activeCallback.setNotifications(activeGatt, characteristic, enabled = false)
                            }
                        }
                    }
                }
            }
        }
    }

    @Deprecated("Pass the discovered BluetoothGattCharacteristic object instead.")
    override suspend fun write(
        service: BluetoothGattService,
        characteristicUuid: UUID,
        value: ByteArray,
        writeType: Int,
        timeout: Duration?,
    ) = withCharacteristicWrite(service.requireCharacteristic(characteristicUuid)) { activeGatt, activeCallback ->
        val characteristic = service.requireCharacteristic(characteristicUuid)
        withTimeout(resolveTimeout(timeout, configuration.writeTimeout).inWholeMilliseconds) {
            activeCallback.writeCharacteristic(activeGatt, characteristic, value, writeType)
        }
    }

    @Deprecated("Pass the discovered BluetoothGattCharacteristic object instead.")
    override suspend fun write(
        id: BleCharacteristicId,
        value: ByteArray,
        writeType: Int,
        timeout: Duration?,
    ) = withCharacteristicWrite(id) { activeGatt, activeCallback ->
        val characteristic = activeGatt.requireCharacteristic(id)
        withTimeout(resolveTimeout(timeout, configuration.writeTimeout).inWholeMilliseconds) {
            activeCallback.writeCharacteristic(activeGatt, characteristic, value, writeType)
        }
    }

    override suspend fun enableNotifications(
        characteristic: BluetoothGattCharacteristic,
        enabled: Boolean,
        timeout: Duration?,
    ) = withGattOperation(resolveTimeout(timeout, configuration.notificationOperationTimeout)) { activeGatt, activeCallback ->
        activeCallback.setNotifications(activeGatt, characteristic, enabled)
    }

    @Deprecated("Pass the discovered BluetoothGattCharacteristic object instead.")
    override suspend fun enableNotifications(
        service: BluetoothGattService,
        characteristicUuid: UUID,
        enabled: Boolean,
        timeout: Duration?,
    ) = withGattOperation(resolveTimeout(timeout, configuration.notificationOperationTimeout)) { activeGatt, activeCallback ->
        val characteristic = service.requireCharacteristic(characteristicUuid)
        activeCallback.setNotifications(activeGatt, characteristic, enabled)
    }

    @Deprecated("Pass the discovered BluetoothGattCharacteristic object instead.")
    override suspend fun enableNotifications(
        id: BleCharacteristicId,
        enabled: Boolean,
        timeout: Duration?,
    ) = withGattOperation(resolveTimeout(timeout, configuration.notificationOperationTimeout)) { activeGatt, activeCallback ->
        val characteristic = activeGatt.requireCharacteristic(id)
        activeCallback.setNotifications(activeGatt, characteristic, enabled)
    }

    override fun notifications(
        characteristic: BluetoothGattCharacteristic,
    ): Flow<ByteArray> = notificationsForCharacteristic(characteristic.toCharacteristicId(), characteristic)

    @Suppress("DEPRECATION")
    @Deprecated("Pass the discovered BluetoothGattCharacteristic object instead.")
    override fun notifications(
        service: BluetoothGattService,
        characteristicUuid: UUID,
    ): Flow<ByteArray> {
        val characteristic = service.requireCharacteristic(characteristicUuid)
        return notificationsForCharacteristic(BleCharacteristicId(service.uuid, characteristicUuid), characteristic)
    }

    @Deprecated("Pass the discovered BluetoothGattCharacteristic object instead.")
    override fun notifications(
        id: BleCharacteristicId,
    ): Flow<ByteArray> = callbackFlow {
        val activeCallback = callback ?: throw NotConnectedException()
        val activeGatt = gatt ?: throw NotConnectedException()
        val characteristic = activeGatt.requireCharacteristic(id)
        val listener: (ByteArray) -> Unit = { value -> trySend(value.copyOf()) }

        activeCallback.addNotificationListener(id, listener)
        try {
            activeCallback.setNotifications(activeGatt, characteristic, enabled = true)
        } catch (error: Throwable) {
            activeCallback.removeNotificationListener(id, listener)
            close(error)
            return@callbackFlow
        }

        awaitClose {
            activeCallback.removeNotificationListener(id, listener)
            cleanupScope.launch {
                if (!activeCallback.hasNotificationListeners(id)) {
                    runCatching { activeCallback.setNotifications(activeGatt, characteristic, enabled = false) }
                }
            }
        }
    }

    private fun notificationsForCharacteristic(
        listenerId: BleCharacteristicId,
        characteristic: BluetoothGattCharacteristic,
    ): Flow<ByteArray> = callbackFlow {
        val activeCallback = callback ?: throw NotConnectedException()
        val activeGatt = gatt ?: throw NotConnectedException()
        val listener: (ByteArray) -> Unit = { value -> trySend(value.copyOf()) }

        activeCallback.addNotificationListener(listenerId, listener)
        try {
            activeCallback.setNotifications(activeGatt, characteristic, enabled = true)
        } catch (error: Throwable) {
            activeCallback.removeNotificationListener(listenerId, listener)
            close(error)
            return@callbackFlow
        }

        awaitClose {
            activeCallback.removeNotificationListener(listenerId, listener)
            cleanupScope.launch {
                if (!activeCallback.hasNotificationListeners(listenerId)) {
                    runCatching { activeCallback.setNotifications(activeGatt, characteristic, enabled = false) }
                }
            }
        }
    }

    override fun disconnect() {
        if (_connectionState.value == BluetoothConnectionStateDisconnecting) {
            return
        }
        if (_connectionState.value !is BluetoothConnectionStateConnected) {
            return
        }
        manualDisconnectRequested.set(true)
        allowAutoReconnect.set(false)
        reconnectInProgress.set(false)
        val activeGatt = gatt
        if (activeGatt == null) {
            return
        }

        _connectionState.value = BluetoothConnectionStateDisconnecting
        runCatching { activeGatt.disconnect() }
            .onFailure { closeGatt(activeGatt) }
    }

    override fun close() {
        manualDisconnectRequested.set(true)
        allowAutoReconnect.set(false)
        reconnectInProgress.set(false)
        gatt?.let(::closeGatt)
        callback?.clear()
        cleanupScope.coroutineContext.cancelChildren()
        gatt = null
        callback = null
        characteristicWriteMutexes.clear()
        _characteristicWriteStates.value = emptyMap()
        _connectionState.value = BluetoothConnectionStateDisconnected
    }

    private suspend fun <T> withGattOperation(
        timeout: Duration,
        block: suspend (BluetoothGatt, GattCallback) -> T,
    ): T = operationMutex.withLock {
        requireConnectPermission()
        val activeGatt = gatt ?: throw NotConnectedException()
        val activeCallback = callback ?: throw NotConnectedException()
        withTimeout(timeout.inWholeMilliseconds) { block(activeGatt, activeCallback) }
    }

    private suspend fun <T> withCharacteristicWrite(
        characteristic: BluetoothGattCharacteristic,
        block: suspend (BluetoothGatt, GattCallback) -> T,
    ): T = withCharacteristicWrite(characteristic.toCharacteristicId(), block)

    private suspend fun <T> withCharacteristicWrite(
        id: BleCharacteristicId,
        block: suspend (BluetoothGatt, GattCallback) -> T,
    ): T {
        val writeMutex = characteristicWriteMutexes.getOrPut(id) { Mutex() }
        return writeMutex.withLock {
            setCharacteristicWriting(id, writing = true)
            try {
                requireConnectPermission()
                val activeGatt = gatt ?: throw NotConnectedException()
                val activeCallback = callback ?: throw NotConnectedException()
                block(activeGatt, activeCallback)
            } finally {
                setCharacteristicWriting(id, writing = false)
            }
        }
    }

    private fun setCharacteristicWriting(id: BleCharacteristicId, writing: Boolean) {
        _characteristicWriteStates.update { current ->
            current.toMutableMap().apply {
                if (writing) {
                    put(id, true)
                } else {
                    remove(id)
                }
            }
        }
    }

    private fun requireConnectPermission() {
        val missingPermissions = BluetoothPermissions.missingRuntimePermissions(
            context = appContext,
            scan = false,
            connect = true,
        )
        if (missingPermissions.isNotEmpty()) {
            throw MissingBluetoothPermissionException(
                "Missing Bluetooth connect permission(s): ${missingPermissions.joinToString()}."
            )
        }
    }

    private fun resolveTimeout(
        timeout: Duration?,
        configuredTimeout: Duration,
    ): Duration = timeout ?: configuredTimeout

    private fun closeGatt(activeGatt: BluetoothGatt) {
        runCatching { activeGatt.disconnect() }
        runCatching { activeGatt.close() }
        if (gatt === activeGatt) gatt = null
    }

    private fun persistBondedConnection(device: BluetoothDevice) {
        val isBonded = runCatching { device.bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false)
        val address = runCatching { device.address }.getOrNull()
        val shouldPersist = configuration.storeBondedConnectionMetadata && isBonded && address != null
        if (shouldPersist) {
            runCatching {
                bondedConnectionStore.upsert(
                    BondedDeviceConnection(
                        address = address,
                        autoConnect = lastAutoConnect.get(),
                        updatedAt = System.currentTimeMillis().milliseconds,
                    ),
                )
            }
        }
    }

    private fun maybeReconnectFromBondedStore(address: String) {
        val reconnectEnabled = configuration.autoReconnectOnUnexpectedDisconnect
        val autoReconnectAllowed = if (reconnectEnabled) allowAutoReconnect.compareAndSet(true, false) else false
        val canStartReconnect = if (autoReconnectAllowed) reconnectInProgress.compareAndSet(false, true) else false

        if (canStartReconnect) {
            var shouldLaunch = true
            synchronized(this) {
                if (reconnectJob?.isActive == true) {
                    reconnectInProgress.set(false)
                    shouldLaunch = false
                }
            }

            if (shouldLaunch) {
                val job = cleanupScope.launch {
                    try {
                        var connectionEstablished = false
                        var shouldContinue = true
                        while (shouldContinue) {
                            if (!isActive || manualDisconnectRequested.get() || connectionEstablished) {
                                shouldContinue = false
                            } else {
                                delay(RECONNECT_RETRY_INTERVAL.inWholeMilliseconds)
                                if (!isActive || manualDisconnectRequested.get()) {
                                    shouldContinue = false
                                }
                            }
                            if (shouldContinue && !connectionEstablished) {
                                val storedConnection =
                                    runCatching { bondedConnectionStore.findByAddress(address) }.getOrNull()
                                if (storedConnection == null) {
                                    shouldContinue = false
                                } else {
                                    try {
                                        connect(
                                            address = storedConnection.address,
                                            autoConnect = storedConnection.autoConnect,
                                            timeout = configuration.connectTimeout,
                                        )
                                        connectionEstablished = true
                                    } catch (_: Throwable) {
                                        if (!isActive || manualDisconnectRequested.get()) {
                                            shouldContinue = false
                                        }
                                    }
                                }
                            }
                        }
                    } finally {
                        reconnectInProgress.set(false)
                        synchronized(this@BleClientImpl) {
                            reconnectJob = null
                        }
                    }
                }
                synchronized(this) {
                    reconnectJob = job
                }
            }
        }
    }

    private fun applyConfiguredLinkParameters(activeGatt: BluetoothGatt) {
        val mtu = configuration.preferredMtu
        if (mtu != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            runCatching { activeGatt.requestMtu(mtu) }
        }
        val priority = configuration.preferredConnectionPriority
        if (priority != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            runCatching { activeGatt.requestConnectionPriority(priority) }
        }
        val preferredPhy = configuration.preferredPhy
        if (preferredPhy != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                activeGatt.setPreferredPhy(
                    preferredPhy.txPhy,
                    preferredPhy.rxPhy,
                    preferredPhy.phyOptions,
                )
            }
        }
    }

    private fun BluetoothGatt.requireCharacteristic(id: BleCharacteristicId): BluetoothGattCharacteristic {
        val service = getService(id.serviceUuid)
            ?: throw BluetoothUnavailableException("GATT service ${id.serviceUuid} was not found. Call discoverServices() first.")
        return service.getCharacteristic(id.characteristicUuid)
            ?: throw BluetoothUnavailableException("GATT characteristic ${id.characteristicUuid} was not found.")
    }

    private fun BluetoothGattService.requireCharacteristic(characteristicUuid: UUID): BluetoothGattCharacteristic =
        getCharacteristic(characteristicUuid)
            ?: throw BluetoothUnavailableException("GATT characteristic $characteristicUuid was not found on service $uuid.")

    private fun BluetoothGattCharacteristic.toCharacteristicId(): BleCharacteristicId {
        val serviceUuid = service?.uuid
            ?: throw BluetoothUnavailableException("GATT characteristic ${uuid} is not attached to a service.")
        return BleCharacteristicId(serviceUuid, uuid)
    }

    private inner class GattCallback(
        private val device: BluetoothDevice,
    ) : BluetoothGattCallback() {
        private val lock = Any()
        private val notificationListeners = ConcurrentHashMap<BleCharacteristicId, CopyOnWriteArraySet<(ByteArray) -> Unit>>()

        private var connectContinuation: kotlinx.coroutines.CancellableContinuation<BluetoothDeviceInfo>? = null
        private var discoverContinuation: kotlinx.coroutines.CancellableContinuation<List<BluetoothGattService>>? = null
        private var readContinuation: kotlinx.coroutines.CancellableContinuation<ByteArray>? = null
        private var rssiContinuation: kotlinx.coroutines.CancellableContinuation<Int>? = null
        private val writeContinuations =
            ConcurrentHashMap<BleCharacteristicId, kotlinx.coroutines.CancellableContinuation<Unit>>()
        private val descriptorContinuations =
            ConcurrentHashMap<BleCharacteristicId, kotlinx.coroutines.CancellableContinuation<Unit>>()

        fun setConnectContinuation(continuation: kotlinx.coroutines.CancellableContinuation<BluetoothDeviceInfo>) {
            synchronized(lock) { connectContinuation = continuation }
        }

        fun clearConnectContinuation() {
            synchronized(lock) { connectContinuation = null }
        }

        suspend fun discoverServices(activeGatt: BluetoothGatt): List<BluetoothGattService> =
            suspendCancellableCoroutine { continuation ->
                synchronized(lock) { discoverContinuation = continuation }
                val started = runCatching { activeGatt.discoverServices() }
                    .getOrElse { error ->
                        synchronized(lock) { discoverContinuation = null }
                        continuation.resumeWithException(error)
                        return@suspendCancellableCoroutine
                    }
                if (!started) {
                    synchronized(lock) { discoverContinuation = null }
                    continuation.resumeWithException(BluetoothUnavailableException("discoverServices() returned false."))
                }
                continuation.invokeOnCancellation {
                    synchronized(lock) { discoverContinuation = null }
                }
            }

        suspend fun readCharacteristic(
            activeGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ): ByteArray = suspendCancellableCoroutine { continuation ->
            synchronized(lock) { readContinuation = continuation }
            val started = runCatching { activeGatt.readCharacteristic(characteristic) }
                .getOrElse { error ->
                    synchronized(lock) { readContinuation = null }
                    continuation.resumeWithException(error)
                    return@suspendCancellableCoroutine
                }
            if (!started) {
                synchronized(lock) { readContinuation = null }
                continuation.resumeWithException(BluetoothUnavailableException("readCharacteristic() returned false."))
            }
            continuation.invokeOnCancellation {
                synchronized(lock) { readContinuation = null }
            }
        }

        suspend fun readRemoteRssi(activeGatt: BluetoothGatt): Int =
            suspendCancellableCoroutine { continuation ->
                synchronized(lock) { rssiContinuation = continuation }
                val started = runCatching { activeGatt.readRemoteRssi() }
                    .getOrElse { error ->
                        synchronized(lock) { rssiContinuation = null }
                        continuation.resumeWithException(error)
                        return@suspendCancellableCoroutine
                    }
                if (!started) {
                    synchronized(lock) { rssiContinuation = null }
                    continuation.resumeWithException(BluetoothUnavailableException("readRemoteRssi() returned false."))
                }
                continuation.invokeOnCancellation {
                    synchronized(lock) { rssiContinuation = null }
                }
            }

        suspend fun writeCharacteristic(
            activeGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            writeType: Int,
        ): Unit = suspendCancellableCoroutine { continuation ->
            val id = characteristic.toCharacteristicId()
            writeContinuations[id] = continuation
            val started = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    activeGatt.writeCharacteristic(characteristic, value, writeType) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.value = value
                    characteristic.writeType = writeType
                    @Suppress("DEPRECATION")
                    activeGatt.writeCharacteristic(characteristic)
                }
            }.getOrElse { error ->
                writeContinuations.remove(id, continuation)
                continuation.resumeWithException(error)
                return@suspendCancellableCoroutine
            }
            if (!started) {
                writeContinuations.remove(id, continuation)
                continuation.resumeWithException(BluetoothUnavailableException("writeCharacteristic() returned false."))
            }
            continuation.invokeOnCancellation {
                writeContinuations.remove(id, continuation)
            }
        }

        suspend fun setNotifications(
            activeGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            enabled: Boolean,
        ): Unit = suspendCancellableCoroutine { continuation ->
            val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
            if (descriptor == null) {
                continuation.resumeWithException(
                    BluetoothUnavailableException("Client characteristic config descriptor 0x2902 was not found."),
                )
                return@suspendCancellableCoroutine
            }

            val localEnabled = runCatching { activeGatt.setCharacteristicNotification(characteristic, enabled) }
                .getOrElse { error ->
                    continuation.resumeWithException(error)
                    return@suspendCancellableCoroutine
                }
            if (!localEnabled) {
                continuation.resumeWithException(BluetoothUnavailableException("setCharacteristicNotification() returned false."))
                return@suspendCancellableCoroutine
            }

            val descriptorValue = when {
                !enabled -> BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                characteristic.supportsIndicate() && !characteristic.supportsNotify() -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                else -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            }

            val id = characteristic.toCharacteristicId()
            descriptorContinuations[id] = continuation
            val started = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    activeGatt.writeDescriptor(descriptor, descriptorValue) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = descriptorValue
                    @Suppress("DEPRECATION")
                    activeGatt.writeDescriptor(descriptor)
                }
            }.getOrElse { error ->
                descriptorContinuations.remove(id, continuation)
                continuation.resumeWithException(error)
                return@suspendCancellableCoroutine
            }
            if (!started) {
                descriptorContinuations.remove(id, continuation)
                continuation.resumeWithException(BluetoothUnavailableException("writeDescriptor() returned false."))
            }
            continuation.invokeOnCancellation {
                descriptorContinuations.remove(id, continuation)
            }
        }

        fun addNotificationListener(id: BleCharacteristicId, listener: (ByteArray) -> Unit) {
            notificationListeners.getOrPut(id) { CopyOnWriteArraySet() }.add(listener)
        }

        fun removeNotificationListener(id: BleCharacteristicId, listener: (ByteArray) -> Unit) {
            notificationListeners[id]?.let { listeners ->
                listeners.remove(listener)
                if (listeners.isEmpty()) {
                    notificationListeners.remove(id, listeners)
                }
            }
        }

        fun hasNotificationListeners(id: BleCharacteristicId): Boolean {
            return notificationListeners[id]?.isNotEmpty() == true
        }

        fun clear() {
            synchronized(lock) {
                connectContinuation = null
                discoverContinuation = null
                readContinuation = null
                rssiContinuation = null
            }
            writeContinuations.clear()
            descriptorContinuations.clear()
            notificationListeners.clear()
        }

        override fun onConnectionStateChange(activeGatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    val info = device.toDeviceInfo()
                    _connectionState.value = BluetoothConnectionStateConnected(info)
                    allowAutoReconnect.set(true)
                    reconnectInProgress.set(false)
                    persistBondedConnection(device)
                    applyConfiguredLinkParameters(activeGatt)
                    takeConnectContinuation()?.resume(info)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    val error = if (status == BluetoothGatt.GATT_SUCCESS) null else BluetoothGattException(status, "Connection")
                    val pendingConnect = takeConnectContinuation()
                    if (pendingConnect != null && error != null) {
                        _connectionState.value = BluetoothConnectionStateFailed(error)
                        pendingConnect.resumeWithException(error)
                    } else if (pendingConnect != null) {
                        _connectionState.value = BluetoothConnectionStateDisconnected
                        pendingConnect.resumeWithException(NotConnectedException("Disconnected before connection completed."))
                    } else if (error != null) {
                        _connectionState.value = BluetoothConnectionStateFailed(error)
                    } else {
                        _connectionState.value = BluetoothConnectionStateDisconnected
                    }
                    closeGatt(activeGatt)
                    clear()
                    if (this@BleClientImpl.callback === this) {
                        this@BleClientImpl.callback = null
                    }
                    this@BleClientImpl.characteristicWriteMutexes.clear()
                    this@BleClientImpl._characteristicWriteStates.value = emptyMap()
                    this@BleClientImpl.cleanupScope.coroutineContext.cancelChildren()
                    val address = runCatching { device.address }.getOrNull()
                    if (!manualDisconnectRequested.get() && address != null) {
                        maybeReconnectFromBondedStore(address)
                    }
                }
            }
        }

        override fun onServicesDiscovered(activeGatt: BluetoothGatt, status: Int) {
            val continuation = takeDiscoverContinuation() ?: return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                continuation.resume(activeGatt.services.orEmpty())
            } else {
                continuation.resumeWithException(BluetoothGattException(status, "Service discovery"))
            }
        }

        @Deprecated("Deprecated in Android 13, retained for older devices.")
        override fun onCharacteristicRead(
            activeGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            val value = runCatching {
                @Suppress("DEPRECATION")
                characteristic.value?.copyOf() ?: ByteArray(0)
            }.getOrDefault(ByteArray(0))
            handleCharacteristicRead(status, value)
        }

        override fun onCharacteristicRead(
            activeGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            handleCharacteristicRead(status, value.copyOf())
        }

        override fun onCharacteristicWrite(
            activeGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            val continuation = takeWriteContinuation(characteristic) ?: return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                continuation.resume(Unit)
            } else {
                continuation.resumeWithException(BluetoothGattException(status, "Characteristic write"))
            }
        }

        @Deprecated("Deprecated in Android 13, retained for older devices.")
        override fun onCharacteristicChanged(
            activeGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val value = runCatching {
                @Suppress("DEPRECATION")
                characteristic.value?.copyOf() ?: ByteArray(0)
            }.getOrDefault(ByteArray(0))
            emitNotification(characteristic, value)
        }

        override fun onCharacteristicChanged(
            activeGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            emitNotification(characteristic, value.copyOf())
        }

        override fun onDescriptorWrite(
            activeGatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            val continuation = takeDescriptorContinuation(descriptor) ?: return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                continuation.resume(Unit)
            } else {
                continuation.resumeWithException(BluetoothGattException(status, "Descriptor write"))
            }
        }

        override fun onReadRemoteRssi(
            activeGatt: BluetoothGatt,
            rssi: Int,
            status: Int,
        ) {
            val continuation = takeRssiContinuation() ?: return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                continuation.resume(rssi)
            } else {
                continuation.resumeWithException(BluetoothGattException(status, "Read remote RSSI"))
            }
        }

        private fun handleCharacteristicRead(status: Int, value: ByteArray) {
            val continuation = takeReadContinuation() ?: return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                continuation.resume(value)
            } else {
                continuation.resumeWithException(BluetoothGattException(status, "Characteristic read"))
            }
        }

        private fun emitNotification(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            val serviceUuid = characteristic.service?.uuid ?: return
            val id = BleCharacteristicId(serviceUuid, characteristic.uuid)
            notificationListeners[id]?.forEach { listener -> listener(value) }
        }

        private fun takeConnectContinuation(): kotlinx.coroutines.CancellableContinuation<BluetoothDeviceInfo>? =
            synchronized(lock) {
                connectContinuation.also { connectContinuation = null }
            }

        private fun takeDiscoverContinuation(): kotlinx.coroutines.CancellableContinuation<List<BluetoothGattService>>? =
            synchronized(lock) {
                discoverContinuation.also { discoverContinuation = null }
            }

        private fun takeReadContinuation(): kotlinx.coroutines.CancellableContinuation<ByteArray>? =
            synchronized(lock) {
                readContinuation.also { readContinuation = null }
            }

        private fun takeRssiContinuation(): kotlinx.coroutines.CancellableContinuation<Int>? =
            synchronized(lock) {
                rssiContinuation.also { rssiContinuation = null }
            }

        private fun takeWriteContinuation(
            characteristic: BluetoothGattCharacteristic,
        ): kotlinx.coroutines.CancellableContinuation<Unit>? =
            writeContinuations.remove(characteristic.toCharacteristicId())

        private fun takeDescriptorContinuation(
            descriptor: BluetoothGattDescriptor,
        ): kotlinx.coroutines.CancellableContinuation<Unit>? {
            val characteristic = runCatching { descriptor.characteristic }.getOrNull() ?: return null
            return descriptorContinuations.remove(characteristic.toCharacteristicId())
        }
    }

    companion object {
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val RECONNECT_RETRY_INTERVAL: Duration = 2.seconds
    }
}
