package com.khataagent.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Thin SharedPreferences wrapper for shop-level settings. No Room/DI involvement — any screen can
 * construct one with a [Context] and read/write synchronously. Values are exposed via plain
 * getters so other code (e.g. the validator's daily-max threshold) can read them later without
 * needing this module's Compose layer.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Shop/business name shown on Reports and shared summaries. Defaults to "My Kirana". */
    fun getShopName(): String =
        prefs.getString(KEY_SHOP_NAME, DEFAULT_SHOP_NAME)?.takeIf { it.isNotBlank() } ?: DEFAULT_SHOP_NAME

    fun setShopName(name: String) {
        prefs.edit().putString(KEY_SHOP_NAME, name.ifBlank { DEFAULT_SHOP_NAME }).apply()
    }

    /** Single-transaction deferral threshold in rupees (mirrors CONTRACTS.md's OVER_DAILY_MAX default). */
    fun getDailyLimit(): Double =
        prefs.getLong(KEY_DAILY_LIMIT, DEFAULT_DAILY_LIMIT.toLong()).toDouble()

    fun setDailyLimit(limit: Double) {
        val safe = if (limit.isFinite() && limit > 0.0) limit else DEFAULT_DAILY_LIMIT
        prefs.edit().putLong(KEY_DAILY_LIMIT, safe.toLong()).apply()
    }

    companion object {
        const val PREFS_NAME = "khata_settings"
        const val KEY_SHOP_NAME = "shop_name"
        const val KEY_DAILY_LIMIT = "daily_single_txn_limit"
        const val DEFAULT_SHOP_NAME = "My Kirana"
        const val DEFAULT_DAILY_LIMIT = 5000.0
    }
}
