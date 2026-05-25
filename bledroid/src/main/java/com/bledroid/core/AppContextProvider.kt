package com.bledroid.core

import android.app.Application
import android.content.Context

internal object AppContextProvider {
    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun get(): Context {
        val existing = appContext
        if (existing != null) return existing

        val resolved = resolveFromAndroidRuntime()
            ?: throw IllegalStateException(
                "Application context is not available yet. " +
                    "Call Bledroid.initialize(context) once during app startup before creating clients.",
            )
        appContext = resolved
        return resolved
    }

    private fun resolveFromAndroidRuntime(): Context? {
        val appFromActivityThread = runCatching {
            val clazz = Class.forName("android.app.ActivityThread")
            val method = clazz.getDeclaredMethod("currentApplication")
            method.invoke(null) as? Application
        }.getOrNull()
        if (appFromActivityThread != null) return appFromActivityThread.applicationContext

        val appFromAppGlobals = runCatching {
            val clazz = Class.forName("android.app.AppGlobals")
            val method = clazz.getDeclaredMethod("getInitialApplication")
            method.invoke(null) as? Application
        }.getOrNull()
        return appFromAppGlobals?.applicationContext
    }
}
