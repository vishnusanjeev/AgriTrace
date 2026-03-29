package com.simats.agritrace

import android.content.Context
import androidx.core.content.edit

object AppPrefs {
    private const val PREF = "app_prefs"
    private const val K_FIRST_LAUNCH = "first_launch"

    private const val K_PENDING_EMAIL = "pending_email"
    private const val K_PENDING_PURPOSE = "pending_purpose"

    private const val K_NOTIF_PUSH = "notif_push"
    private const val K_NOTIF_EMAIL = "notif_email"
    private const val K_NOTIF_SMS = "notif_sms"

    private const val K_NOTIF_BATCH = "notif_batch"
    private const val K_NOTIF_VERIFY = "notif_verify"
    private const val K_NOTIF_MARKETING = "notif_marketing"

    private const val K_PROFILE_NAME = "profile_name"
    private const val K_PROFILE_EMAIL = "profile_email"
    private const val K_PROFILE_PHONE = "profile_phone"
    private const val K_PROFILE_LOCATION = "profile_location"
    private const val K_PROFILE_IMAGE = "profile_image_url"

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun isFirstLaunch(ctx: Context): Boolean = sp(ctx).getBoolean(K_FIRST_LAUNCH, true)

    fun markFirstLaunchDone(ctx: Context) {
        sp(ctx).edit(commit = true) { putBoolean(K_FIRST_LAUNCH, false) }
    }

    fun setPendingEmailVerification(ctx: Context, email: String, purpose: String = "VERIFY_EMAIL") {
        sp(ctx).edit(commit = true) {
            putString(K_PENDING_EMAIL, email.trim())
            putString(K_PENDING_PURPOSE, purpose.trim())
        }
    }

    fun pendingEmail(ctx: Context): String? = sp(ctx).getString(K_PENDING_EMAIL, null)

    fun pendingPurpose(ctx: Context): String =
        sp(ctx).getString(K_PENDING_PURPOSE, "VERIFY_EMAIL") ?: "VERIFY_EMAIL"

    fun clearPendingEmailVerification(ctx: Context) {
        sp(ctx).edit(commit = true) {
            remove(K_PENDING_EMAIL)
            remove(K_PENDING_PURPOSE)
        }
    }

    fun notifPush(ctx: Context): Boolean = sp(ctx).getBoolean(K_NOTIF_PUSH, true)
    fun notifEmail(ctx: Context): Boolean = sp(ctx).getBoolean(K_NOTIF_EMAIL, true)
    fun notifSms(ctx: Context): Boolean = sp(ctx).getBoolean(K_NOTIF_SMS, true)

    fun notifBatchUpdates(ctx: Context): Boolean = sp(ctx).getBoolean(K_NOTIF_BATCH, true)
    fun notifVerificationAlerts(ctx: Context): Boolean = sp(ctx).getBoolean(K_NOTIF_VERIFY, true)
    fun notifMarketingEmails(ctx: Context): Boolean = sp(ctx).getBoolean(K_NOTIF_MARKETING, true)

    fun setNotifPush(ctx: Context, enabled: Boolean) {
        sp(ctx).edit(commit = true) { putBoolean(K_NOTIF_PUSH, enabled) }
    }

    fun setNotifEmail(ctx: Context, enabled: Boolean) {
        sp(ctx).edit(commit = true) { putBoolean(K_NOTIF_EMAIL, enabled) }
    }

    fun setNotifSms(ctx: Context, enabled: Boolean) {
        sp(ctx).edit(commit = true) { putBoolean(K_NOTIF_SMS, enabled) }
    }

    fun setNotifBatchUpdates(ctx: Context, enabled: Boolean) {
        sp(ctx).edit(commit = true) { putBoolean(K_NOTIF_BATCH, enabled) }
    }

    fun setNotifVerificationAlerts(ctx: Context, enabled: Boolean) {
        sp(ctx).edit(commit = true) { putBoolean(K_NOTIF_VERIFY, enabled) }
    }

    fun setNotifMarketingEmails(ctx: Context, enabled: Boolean) {
        sp(ctx).edit(commit = true) { putBoolean(K_NOTIF_MARKETING, enabled) }
    }

    fun profileName(ctx: Context): String = sp(ctx).getString(K_PROFILE_NAME, "") ?: ""
    fun profileEmail(ctx: Context): String = sp(ctx).getString(K_PROFILE_EMAIL, "") ?: ""
    fun profilePhone(ctx: Context): String = sp(ctx).getString(K_PROFILE_PHONE, "") ?: ""
    fun profileLocation(ctx: Context): String = sp(ctx).getString(K_PROFILE_LOCATION, "") ?: ""
    fun profileImageUrl(ctx: Context): String = sp(ctx).getString(K_PROFILE_IMAGE, "") ?: ""

    fun setProfileName(ctx: Context, v: String) {
        sp(ctx).edit(commit = true) { putString(K_PROFILE_NAME, v) }
    }

    fun setProfileEmail(ctx: Context, v: String) {
        sp(ctx).edit(commit = true) { putString(K_PROFILE_EMAIL, v) }
    }

    fun setProfilePhone(ctx: Context, v: String) {
        sp(ctx).edit(commit = true) { putString(K_PROFILE_PHONE, v) }
    }

    fun setProfileLocation(ctx: Context, v: String) {
        sp(ctx).edit(commit = true) { putString(K_PROFILE_LOCATION, v) }
    }

    fun setProfileImageUrl(ctx: Context, v: String) {
        sp(ctx).edit(commit = true) { putString(K_PROFILE_IMAGE, v) }
    }
}
