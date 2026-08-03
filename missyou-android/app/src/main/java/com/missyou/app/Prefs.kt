package com.missyou.app

import android.content.Context
import java.util.UUID

object Prefs {
    private const val FILE = "missyou_prefs"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_NAME = "name"
    private const val KEY_PARTNER_ID = "partner_id"
    private const val KEY_PARTNER_NAME = "partner_name"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getOrCreateUserId(context: Context): String {
        val p = prefs(context)
        var id = p.getString(KEY_USER_ID, null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            p.edit().putString(KEY_USER_ID, id).apply()
        }
        return id
    }

    fun getName(context: Context): String? = prefs(context).getString(KEY_NAME, null)

    fun setName(context: Context, name: String) {
        prefs(context).edit().putString(KEY_NAME, name).apply()
    }

    fun getPartnerId(context: Context): String? = prefs(context).getString(KEY_PARTNER_ID, null)

    fun setPartnerId(context: Context, id: String?) {
        prefs(context).edit().putString(KEY_PARTNER_ID, id).apply()
    }

    fun getPartnerName(context: Context): String? = prefs(context).getString(KEY_PARTNER_NAME, null)

    fun setPartnerName(context: Context, name: String?) {
        prefs(context).edit().putString(KEY_PARTNER_NAME, name).apply()
    }
}
