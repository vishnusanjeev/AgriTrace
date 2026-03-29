package com.simats.agritrace

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import org.json.JSONObject

object Session {
    private const val PREF = "session"
    private const val K_TOKEN = "token"
    private const val K_ROLE = "role"
    private const val K_USER_ID = "user_id"
    private const val K_NAME = "name"
    private const val K_EMAIL = "email"
    private const val K_TOKEN_EXP = "token_exp"

    private const val EXP_SKEW_SECONDS = 30L
    private const val EXP_GRACE_PAST_SECONDS = 5L

    private var appCtx: Context? = null

    fun ensureContext(ctx: Context) {
        attachContext(ctx)
    }

    private fun attachContext(ctx: Context?) {
        ctx?.applicationContext?.let {
            if (appCtx == null) appCtx = it
        }
    }

    private fun sp(ctx: Context): SharedPreferences {
        attachContext(ctx)
        val context = appCtx ?: ctx.applicationContext
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    }

    private fun sp(): SharedPreferences? = appCtx?.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun save(ctx: Context, token: String, user: UserDto?) {
        val uid = user?.id ?: 0L
        val role = user?.role
        val exp = parseJwtExp(token)

        sp(ctx).edit(commit = true) {
            putString(K_TOKEN, token)
            putString(K_ROLE, role)
            putLong(K_USER_ID, uid)
            putString(K_NAME, user?.full_name)
            putString(K_EMAIL, user?.email)
            if (exp != null) putLong(K_TOKEN_EXP, exp) else remove(K_TOKEN_EXP)
        }
    }

    fun token(ctx: Context): String? = sp(ctx).getString(K_TOKEN, null)
    fun token(): String? = sp()?.getString(K_TOKEN, null)
    fun role(ctx: Context): String? = sp(ctx).getString(K_ROLE, null)
    fun email(ctx: Context): String? = sp(ctx).getString(K_EMAIL, null)
    fun name(ctx: Context): String? = sp(ctx).getString(K_NAME, null)
    fun userId(ctx: Context): Long = sp(ctx).getLong(K_USER_ID, 0L)

    fun updateProfile(ctx: Context, fullName: String?, email: String?, profileImageUrl: String? = null) {
        sp(ctx).edit(commit = true) {
            if (fullName != null) putString(K_NAME, fullName)
            if (email != null) putString(K_EMAIL, email)
            if (profileImageUrl != null) putString("profile_image_url", profileImageUrl)
        }
    }

    fun isLoggedIn(ctx: Context): Boolean {
        val t = token(ctx)
        if (t.isNullOrBlank()) return false
        if (userId(ctx) <= 0L) return false

        val expStored = sp(ctx).getLong(K_TOKEN_EXP, 0L).takeIf { it > 0L }
        val exp = expStored ?: parseJwtExp(t)

        if (exp != null) {
            val nowSec = System.currentTimeMillis() / 1000L

            if (exp <= (nowSec - EXP_GRACE_PAST_SECONDS)) return false

            if (exp <= (nowSec + EXP_SKEW_SECONDS)) {
                return true
            }
        }

        return true
    }

    fun ensureValidOrClear(ctx: Context, allowClear: Boolean = true): Boolean {
        val t = token(ctx)
        if (t.isNullOrBlank()) return false
        val ok = isLoggedIn(ctx)
        if (!ok && allowClear) clear(ctx)
        return ok
    }

    fun clear(ctx: Context) {
        sp(ctx).edit(commit = true) { clear() }
    }

    private fun parseJwtExp(token: String): Long? {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return null
            val payload = parts[1]
            val json = String(Base64.decode(base64UrlToBase64(payload), Base64.DEFAULT))
            val obj = JSONObject(json)
            if (!obj.has("exp")) null else obj.getLong("exp")
        } catch (_: Exception) {
            null
        }
    }

    private fun base64UrlToBase64(s: String): String {
        var out = s.replace('-', '+').replace('_', '/')
        val pad = out.length % 4
        if (pad != 0) out += "=".repeat(4 - pad)
        return out
    }
}
