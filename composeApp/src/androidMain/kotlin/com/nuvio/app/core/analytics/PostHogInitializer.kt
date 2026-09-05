package com.nuvio.app.core.analytics

import android.app.Application
import android.content.res.Configuration
import android.os.Build
import com.nuvio.app.core.build.AppVersionConfig
import com.nuvio.app.features.settings.SentrySettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object PostHogInitializer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var uncaughtHandlerInstalled = false

    fun start(application: Application) {
        val context = application.applicationContext
        val screenLayout = context.resources.configuration.screenLayout
        val isTablet = (screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
        val deviceType = if (isTablet) "tablet" else "mobile"
        val platform = if (isTablet) "Android Tablet" else "Android"
        val lastKnownKey = com.nuvio.app.features.license.LicenseStorage.loadLastKnownKey()?.takeIf { it.isNotBlank() }

        PostHogAnalytics.initialize(
            platform = platform,
            version = AppVersionConfig.VERSION_NAME,
            distinctId = lastKnownKey,
            deviceType = deviceType,
            osName = "Android",
            osVersion = Build.VERSION.RELEASE ?: "unknown",
            deviceModel = Build.MODEL ?: "unknown",
            deviceBrand = Build.BRAND ?: "unknown",
            serviceName = "khayin-mobile"
        )

        SentrySettingsRepository.ensureLoaded()
        PostHogLogger.isEnabled = SentrySettingsRepository.enabled.value
        scope.launch {
            SentrySettingsRepository.enabled.collect { enabled ->
                PostHogLogger.isEnabled = enabled
            }
        }

        installUncaughtExceptionHandler()
    }

    private fun installUncaughtExceptionHandler() {
        if (uncaughtHandlerInstalled) return
        uncaughtHandlerInstalled = true
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                PostHogAnalytics.captureException(
                    throwable = throwable,
                    tag = "UncaughtCrash",
                    isUnhandled = true,
                    properties = mapOf("thread_name" to thread.name)
                )
                PostHogLogger.flush()
            } catch (_: Throwable) {
            } finally {
                originalHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
