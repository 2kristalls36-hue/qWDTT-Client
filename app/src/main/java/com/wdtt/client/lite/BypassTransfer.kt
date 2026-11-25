package com.wdtt.client.lite

import android.content.Context
import com.wdtt.client.BuildConfig
import com.wdtt.client.BypassRoutes
import com.wdtt.client.BypassShare
import com.wdtt.client.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Export and import of the bypass lists, compatible with qWDTT (format «qwdtt-bypass»).
 *
 * qWDTT keeps one app list plus a mode flag; qWDTT Client keeps a list per mode. So an
 * export writes the qWDTT fields (the current mode's list as `excludedApps`, `isWhitelist`,
 * `bypassRoutes`) and adds both lists in extra fields qWDTT ignores. An import of a qWDTT
 * file puts its single list into the list of the mode it names and leaves the other one.
 * A plain text list of sites (also accepted by qWDTT) is imported as sites.
 */
object BypassTransfer {
    private const val KEY_BYPASS_APPS = "clientBypassApps"
    private const val KEY_ALLOWED_APPS = "clientAllowedApps"

    /** What an import would change; null fields are left as they are. */
    data class Preview(
        val sites: List<String>?,
        val sitesTruncated: Boolean,
        val bypassApps: List<String>?,
        val allowedApps: List<String>?,
        /** Mode to switch to: true — «Только выбранные»; null — unchanged. */
        val onlySelected: Boolean?,
        val fromQwdtt: Boolean,
    ) {
        val isEmpty get() = sites == null && bypassApps == null && allowedApps == null
    }

    suspend fun export(context: Context): String = withContext(Dispatchers.IO) {
        val s = SettingsStore(context)
        s.migrateSplitAppLists()
        val onlySelected = s.isWhitelist.first()
        val bypass = s.excludedApps.first()
        val allowed = s.allowedApps.first()
        JSONObject()
            .put("format", BypassShare.FORMAT)
            .put("formatVersion", BypassShare.FORMAT_VERSION)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("bypassRoutes", s.bypassRoutes.first())
            // What qWDTT reads: one list, the one of the current mode.
            .put("excludedApps", if (onlySelected) allowed else bypass)
            .put("isWhitelist", onlySelected)
            // qWDTT Client: both lists.
            .put(KEY_BYPASS_APPS, bypass)
            .put(KEY_ALLOWED_APPS, allowed)
            .toString(2)
    }

    /** Reads a file or text; throws IllegalArgumentException with a user-facing reason. */
    fun preview(raw: String): Preview {
        val text = raw.trim().removePrefix("﻿")
        if (text.isEmpty()) throw IllegalArgumentException("Файл пуст.")
        val root = runCatching { JSONObject(text) }.getOrNull()
        if (root == null) {
            // Plain list of sites, one per line.
            val parsed = BypassRoutes.parseRules(text).filter { BypassRoutes.isValidRule(it) }
            if (parsed.isEmpty()) throw IllegalArgumentException("Файл не содержит списков обхода.")
            val rules = BypassRoutes.limitRules(parsed)
            return Preview(rules, parsed.size > rules.size, null, null, null, fromQwdtt = true)
        }
        val format = root.optString("format", "")
        if (format.isNotEmpty() && format != BypassShare.FORMAT) {
            throw IllegalArgumentException("Файл не является файлом обхода qWDTT.")
        }
        val hasSites = root.has("bypassRoutes")
        val hasOwn = root.has(KEY_BYPASS_APPS) || root.has(KEY_ALLOWED_APPS)
        val hasQwdttApps = root.has("excludedApps") || root.has("isWhitelist")
        if (!hasSites && !hasOwn && !hasQwdttApps) throw IllegalArgumentException("Файл не содержит списков обхода.")

        // The same check as for a plain list: entries that are not a site or an address are dropped.
        val parsedSites = if (hasSites) {
            BypassRoutes.parseRules(root.optString("bypassRoutes", "")).filter { BypassRoutes.isValidRule(it) }
        } else null
        val sites = parsedSites?.let { BypassRoutes.limitRules(it) }
        fun apps(key: String) = root.optString(key, "").split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val mode = if (root.has("isWhitelist")) root.optBoolean("isWhitelist", false) else null

        return if (hasOwn) {
            Preview(
                sites = sites,
                sitesTruncated = parsedSites != null && parsedSites.size > (sites?.size ?: 0),
                bypassApps = if (root.has(KEY_BYPASS_APPS)) apps(KEY_BYPASS_APPS) else null,
                allowedApps = if (root.has(KEY_ALLOWED_APPS)) apps(KEY_ALLOWED_APPS) else null,
                onlySelected = mode,
                fromQwdtt = false,
            )
        } else {
            // qWDTT: its one list belongs to the mode it names.
            val list = if (root.has("excludedApps")) apps("excludedApps") else null
            val onlySelected = mode ?: false
            Preview(
                sites = sites,
                sitesTruncated = parsedSites != null && parsedSites.size > (sites?.size ?: 0),
                bypassApps = if (!onlySelected) list else null,
                allowedApps = if (onlySelected) list else null,
                onlySelected = mode,
                fromQwdtt = true,
            )
        }
    }

    suspend fun apply(context: Context, preview: Preview) = withContext(Dispatchers.IO) {
        val s = SettingsStore(context)
        s.migrateSplitAppLists()
        preview.sites?.let {
            s.saveBypassRoutes(it.joinToString("\n"))
            BypassRoutes.clearResolveCache()
        }
        preview.bypassApps?.let { s.saveExcludedApps(it.joinToString(",")) }
        preview.allowedApps?.let { s.saveAllowedApps(it.joinToString(",")) }
        preview.onlySelected?.let { s.saveExceptionsMode(it) }
    }
}
