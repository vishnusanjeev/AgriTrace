package com.simats.agritrace.session

import android.content.Context
import android.content.SharedPreferences

class DistributorSessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("DistributorSession", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_EMAIL = "email"
        private const val KEY_PHONE = "phone"
        private const val KEY_COMPANY = "company_name"
        private const val KEY_TYPE = "distributor_type"
    }

    fun saveLoginSession(
        name: String,
        email: String,
        id: Int,
        phone: String? = null,
        companyName: String? = null,
        distributorType: String? = null
    ) {
        val editor = prefs.edit()
        editor.putBoolean(KEY_IS_LOGGED_IN, true)
        editor.putInt(KEY_ID, id)
        editor.putString(KEY_NAME, name)
        editor.putString(KEY_EMAIL, email)
        editor.putString(KEY_PHONE, phone)
        editor.putString(KEY_COMPANY, companyName)
        editor.putString(KEY_TYPE, distributorType)
        editor.apply()
    }

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    fun getId(): Int = prefs.getInt(KEY_ID, -1)
    fun getName(): String? = prefs.getString(KEY_NAME, null)
    fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)
    fun getPhone(): String? = prefs.getString(KEY_PHONE, null)
    fun getCompany(): String? = prefs.getString(KEY_COMPANY, null)
    fun getDistributorType(): String? = prefs.getString(KEY_TYPE, null)
    fun clearSession() = prefs.edit().clear().apply()
}
