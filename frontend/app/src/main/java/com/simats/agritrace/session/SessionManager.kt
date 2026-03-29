package com.simats.agritrace.session

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("AgriTraceSession", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "SessionManager"

        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_FARMER_ID = "farmer_id"
        private const val KEY_FARMER_NAME = "farmer_name"
        private const val KEY_EMAIL = "email"
    }

    /** Create login session */
    fun createLoginSession(farmerId: String, farmerName: String, email: String) {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_FARMER_ID, farmerId)
            putString(KEY_FARMER_NAME, farmerName)
            putString(KEY_EMAIL, email)
            apply()
        }
        Log.d(TAG, "Login session created → FarmerId=$farmerId, Name=$farmerName, Email=$email")
    }

    /** Check if user is logged in */
    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    /** Get stored farmer ID */
    fun getFarmerId(): String? = prefs.getString(KEY_FARMER_ID, null)

    /** Get stored farmer name */
    fun getFarmerName(): String? = prefs.getString(KEY_FARMER_NAME, null)

    /** Get stored email */
    fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)

    /** Logout user and clear all session data (explicit only). */
    fun logout(userInitiated: Boolean = false) {
        if (!userInitiated) return
        prefs.edit().clear().apply()
        Log.d(TAG, "Session cleared → User logged out")
    }

    /** Optional: Get all session info as a map */
    fun getSessionDetails(): Map<String, String?> {
        return mapOf(
            "farmerId" to getFarmerId(),
            "farmerName" to getFarmerName(),
            "email" to getEmail()
        )
    }
}
