package com.bledroid.companion

import android.companion.AssociationRequest
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.bledroid.core.BluetoothUnavailableException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration

internal class BleCompanionDeviceManagerImpl(
    private val appContext: Context,
) : BleCompanionDeviceManager {
    override fun isAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                appContext.getSystemService(CompanionDeviceManager::class.java) != null

    override suspend fun requestAssociation(
        request: AssociationRequest,
        timeout: Duration,
    ): IntentSender {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            throw BluetoothUnavailableException("Companion Device Manager requires Android 8.0 (API 26) or higher.")
        }
        val manager = appContext.getSystemService(CompanionDeviceManager::class.java)
            ?: throw BluetoothUnavailableException("Companion Device Manager is unavailable on this device.")

        return withTimeout(timeout.inWholeMilliseconds) {
            suspendCancellableCoroutine { continuation ->
                val completed = AtomicBoolean(false)

                fun resumeSuccess(intentSender: IntentSender) {
                    if (completed.compareAndSet(false, true)) {
                        continuation.resume(intentSender)
                    }
                }

                fun resumeFailure(message: CharSequence?) {
                    if (completed.compareAndSet(false, true)) {
                        continuation.resumeWithException(
                            BluetoothUnavailableException(
                                message?.toString()
                                    ?: "Companion device association failed.",
                            ),
                        )
                    }
                }

                val callback = object : CompanionDeviceManager.Callback() {
                    override fun onAssociationPending(intentSender: IntentSender) {
                        resumeSuccess(intentSender)
                    }

                    @Deprecated("Deprecated in API 33, retained for older devices.")
                    override fun onDeviceFound(intentSender: IntentSender) {
                        resumeSuccess(intentSender)
                    }

                    override fun onFailure(error: CharSequence?) {
                        resumeFailure(error)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    manager.associate(request, Executor { runnable -> runnable.run() }, callback)
                } else {
                    @Suppress("DEPRECATION")
                    manager.associate(request, callback, Handler(Looper.getMainLooper()))
                }
            }
        }
    }
}
