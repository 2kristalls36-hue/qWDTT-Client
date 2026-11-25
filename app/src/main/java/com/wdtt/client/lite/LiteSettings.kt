package com.wdtt.client.lite

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.liteStore: DataStore<Preferences> by preferencesDataStore("lite")

/** Subscription facts for one profile, as last reported by the server. */
data class SubscriptionInfo(
    /** Unix seconds; 0 — the subscription has no end date. */
    val expiresAt: Long,
    val maxDevices: Int,
    val boundDevices: Int,
    /** Unix ms of the last successful server answer. */
    val checkedAt: Long,
)

/** Settings that exist only in qWDTT Client: power level, expiry warnings, cached subscription data. */
class LiteSettings(context: Context) {
    private val store = context.applicationContext.liteStore

    companion object {
        private val POWER = intPreferencesKey("power_level")
        private val WARN_ENABLED = booleanPreferencesKey("expiry_warn_enabled")
        private val WARN_DAYS = intPreferencesKey("expiry_warn_days")
        private val CONNECT_ON_BOOT = booleanPreferencesKey("connect_on_boot")
        private val TRUSTED_WIFI = booleanPreferencesKey("trusted_wifi")
        private val TRUSTED_SSIDS = androidx.datastore.preferences.core.stringSetPreferencesKey("trusted_wifi_ssids")
        private val LIQUID_GLASS = booleanPreferencesKey("liquid_glass")
        private val HAPTICS = booleanPreferencesKey("haptics")
        private val MAGNET = booleanPreferencesKey("magnet")
        private val SCREEN_FIT = booleanPreferencesKey("screen_fit")
        private val GLASS_TRANSPARENCY = androidx.datastore.preferences.core.floatPreferencesKey("glass_transparency")
        /** Set whenever the glass is turned on. */
        const val DEFAULT_GLASS_TRANSPARENCY = 0.9f
        const val DEFAULT_WARN_DAYS = 3
        val WARN_DAY_CHOICES = listOf(1, 3, 7)

        private fun expiresKey(id: String) = longPreferencesKey("sub_expires_$id")
        private fun maxDevKey(id: String) = intPreferencesKey("sub_max_dev_$id")
        private fun boundDevKey(id: String) = intPreferencesKey("sub_bound_dev_$id")
        private fun checkedKey(id: String) = longPreferencesKey("sub_checked_$id")
        /** Expiry date the last warning was shown for, so each date warns once. */
        private fun warnedKey(id: String) = longPreferencesKey("sub_warned_for_$id")
    }

    val powerLevel: Flow<PowerLevel> = store.data.map { PowerLevel.fromIndex(it[POWER] ?: PowerLevel.Default.ordinal) }
    suspend fun savePowerLevel(level: PowerLevel) = store.edit { it[POWER] = level.ordinal }

    val warnEnabled: Flow<Boolean> = store.data.map { it[WARN_ENABLED] ?: true }
    val warnDays: Flow<Int> = store.data.map { it[WARN_DAYS] ?: DEFAULT_WARN_DAYS }
    suspend fun saveWarnEnabled(enabled: Boolean) = store.edit { it[WARN_ENABLED] = enabled }
    suspend fun saveWarnDays(days: Int) = store.edit { it[WARN_DAYS] = days }

    /** Beta: liquid glass tab bar (Android 13+). */
    val liquidGlass: Flow<Boolean> = store.data.map { it[LIQUID_GLASS] ?: false }
    suspend fun saveLiquidGlass(enabled: Boolean) = store.edit { it[LIQUID_GLASS] = enabled }
    /** 0 — frosted, 1 — almost clear. */
    val glassTransparency: Flow<Float> = store.data.map { it[GLASS_TRANSPARENCY] ?: DEFAULT_GLASS_TRANSPARENCY }
    suspend fun saveGlassTransparency(value: Float) = store.edit { it[GLASS_TRANSPARENCY] = value.coerceIn(0f, 1f) }

    /** Beta: magnet motion (accelerating pull to stops, snapping lists); on by default. */
    val magnet: Flow<Boolean> = store.data.map { it[MAGNET] ?: true }
    suspend fun saveMagnet(enabled: Boolean) = store.edit { it[MAGNET] = enabled }

    /** Beta: the tab bar and the corners of cards and fields follow the screen corners; on by default. */
    val screenFit: Flow<Boolean> = store.data.map { it[SCREEN_FIT] ?: true }
    suspend fun saveScreenFit(enabled: Boolean) = store.edit { it[SCREEN_FIT] = enabled }

    /** Vibration on presses, switches and results; on by default. */
    val haptics: Flow<Boolean> = store.data.map { it[HAPTICS] ?: true }
    suspend fun saveHaptics(enabled: Boolean) = store.edit { it[HAPTICS] = enabled }

    val connectOnBoot: Flow<Boolean> = store.data.map { it[CONNECT_ON_BOOT] ?: false }
    suspend fun saveConnectOnBoot(enabled: Boolean) = store.edit { it[CONNECT_ON_BOOT] = enabled }

    /** Pause on trusted Wi‑Fi networks, and the networks (names). */
    val trustedWifi: Flow<Boolean> = store.data.map { it[TRUSTED_WIFI] ?: false }
    val trustedSsids: Flow<Set<String>> = store.data.map { it[TRUSTED_SSIDS] ?: emptySet() }
    suspend fun saveTrustedWifi(enabled: Boolean) = store.edit { it[TRUSTED_WIFI] = enabled }
    suspend fun addTrustedSsid(ssid: String) = store.edit {
        val clean = TrustedWifi.sanitize(ssid)
        if (clean.isEmpty()) return@edit
        val now = it[TRUSTED_SSIDS] ?: emptySet()
        if (now.none { s -> s.equals(clean, ignoreCase = true) }) it[TRUSTED_SSIDS] = now + clean
    }
    suspend fun removeTrustedSsid(ssid: String) = store.edit {
        it[TRUSTED_SSIDS] = (it[TRUSTED_SSIDS] ?: emptySet()) - ssid
    }

    fun subscription(profileId: String): Flow<SubscriptionInfo?> = store.data.map { p ->
        val checked = p[checkedKey(profileId)] ?: return@map null
        SubscriptionInfo(
            expiresAt = p[expiresKey(profileId)] ?: 0L,
            maxDevices = p[maxDevKey(profileId)] ?: 1,
            boundDevices = p[boundDevKey(profileId)] ?: 0,
            checkedAt = checked,
        )
    }

    suspend fun saveSubscription(profileId: String, info: SubscriptionInfo) = store.edit { p ->
        p[expiresKey(profileId)] = info.expiresAt
        p[maxDevKey(profileId)] = info.maxDevices
        p[boundDevKey(profileId)] = info.boundDevices
        p[checkedKey(profileId)] = info.checkedAt
    }

    suspend fun warnedFor(profileId: String): Long = store.data.first()[warnedKey(profileId)] ?: 0L
    suspend fun saveWarnedFor(profileId: String, expiresAt: Long) = store.edit { it[warnedKey(profileId)] = expiresAt }
}
