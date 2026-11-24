package com.javasultra.app.core.security

import android.content.Context
import java.util.UUID

/**
 * Creates a random app-scoped identifier. It is not a hardware fingerprint or an authentication
 * factor; the trusted backend must bind it to a verified device registration.
 */
class AndroidCreatorDeviceIdentityProvider(
    context: Context
) : CreatorDeviceIdentityProvider {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    @Synchronized
    override fun getOrCreateDeviceId(): DeviceId {
        val existing = preferences.getString(DEVICE_ID_KEY, null)
        if (!existing.isNullOrBlank() && isUuid(existing)) return DeviceId(existing)

        val generated = UUID.randomUUID().toString()
        check(preferences.edit().putString(DEVICE_ID_KEY, generated).commit()) {
            "Unable to persist app-scoped device identity"
        }
        return DeviceId(generated)
    }

    private fun isUuid(value: String): Boolean =
        try {
            UUID.fromString(value).toString() == value
        } catch (_: IllegalArgumentException) {
            false
        }

    private companion object {
        const val PREFERENCES_NAME = "javas_device_identity_v1"
        const val DEVICE_ID_KEY = "app_scoped_device_id"
    }
}
