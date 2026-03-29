package com.simats.agritrace

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.SwitchCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.Locale

class PrivacyAndSecurityActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("agritrace_privacy_security", MODE_PRIVATE) }

    private lateinit var tvProfileVisibilityValue: TextView
    private lateinit var swTwoFactor: SwitchCompat
    private lateinit var swBiometric: SwitchCompat
    private lateinit var swActivityAlerts: SwitchCompat

    private lateinit var roleTheme: RoleTheme

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_privacy_and_security)

        roleTheme = RoleTheme.fromRole(Session.role(this))

        window.statusBarColor = android.graphics.Color.WHITE
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootPrivacySecurity)) { v, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val originalPaddingTop = v.paddingTop
            val originalPaddingBottom = v.paddingBottom
            val originalPaddingLeft = v.paddingLeft
            val originalPaddingRight = v.paddingRight
            
            v.setPadding(
                maxOf(originalPaddingLeft, sb.left),
                maxOf(originalPaddingTop, sb.top),
                maxOf(originalPaddingRight, sb.right),
                maxOf(originalPaddingBottom, sb.bottom)
            )
            insets
        }

        val ivBack = findViewById<ImageView>(R.id.ivBack)
        val btnBackToProfile = findViewById<AppCompatButton>(R.id.btnBackToProfile)

        val rowChangePassword = findViewById<View>(R.id.rowChangePassword)
        val rowTwoFactor = findViewById<View>(R.id.rowTwoFactor)
        val rowBiometric = findViewById<View>(R.id.rowBiometric)

        val rowProfileVisibility = findViewById<View>(R.id.rowProfileVisibility)
        val rowActivityAlerts = findViewById<View>(R.id.rowActivityAlerts)

        val rowDownloadData = findViewById<View>(R.id.rowDownloadData)
        val rowPrivacyPolicy = findViewById<View>(R.id.rowPrivacyPolicy)
        val rowDeleteAccount = findViewById<View>(R.id.rowDeleteAccount)

        tvProfileVisibilityValue = findViewById(R.id.tvProfileVisibilityValue)

        swTwoFactor = findViewById(R.id.swTwoFactor)
        swBiometric = findViewById(R.id.swBiometric)
        swActivityAlerts = findViewById(R.id.swActivityAlerts)

        swTwoFactor.isChecked = prefs.getBoolean("two_factor", true)
        swBiometric.isChecked = prefs.getBoolean("biometric", true)
        swActivityAlerts.isChecked = prefs.getBoolean("activity_alerts", true)

        tvProfileVisibilityValue.text = prefs.getString("profile_visibility", "Public") ?: "Public"

        // Apply role theme once after layout
        swTwoFactor.post { applyRoleSwitch(swTwoFactor) }
        swBiometric.post { applyRoleSwitch(swBiometric) }
        swActivityAlerts.post { applyRoleSwitch(swActivityAlerts) }

        ivBack.setOnClickListener { finish() }
        btnBackToProfile.setOnClickListener { finish() }

        rowChangePassword.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            startActivity(Intent(this, ChangePasswordActivity::class.java))
        }

        rowTwoFactor.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            swTwoFactor.isChecked = !swTwoFactor.isChecked
        }

        rowBiometric.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            swBiometric.isChecked = !swBiometric.isChecked
        }

        rowActivityAlerts.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            swActivityAlerts.isChecked = !swActivityAlerts.isChecked
        }

        swTwoFactor.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("two_factor", checked).apply()
            swTwoFactor.post { applyRoleSwitch(swTwoFactor) }
        }

        swBiometric.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("biometric", checked).apply()
            swBiometric.post { applyRoleSwitch(swBiometric) }
        }

        swActivityAlerts.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("activity_alerts", checked).apply()
            swActivityAlerts.post { applyRoleSwitch(swActivityAlerts) }
        }

        rowProfileVisibility.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showProfileVisibilityDialog()
        }

        rowDownloadData.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showDownloadDataDialog()
        }

        rowPrivacyPolicy.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }

        rowDeleteAccount.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showDeleteAccountDialog()
        }
    }

    // Role-based override so ON is a solid color (no mint wash)
    private fun applyRoleSwitch(sw: SwitchCompat) {
        sw.trackTintList = null
        sw.thumbTintList = null
        sw.splitTrack = false

        val track: Drawable? =
            AppCompatResources.getDrawable(this, R.drawable.switch_track_selector)?.mutate()
        val thumb: Drawable? =
            AppCompatResources.getDrawable(this, R.drawable.switch_thumb_selector)?.mutate()

        if (track != null) {
            track.alpha = 255
            tintDrawable(track, roleTheme.trackOnColor)
            sw.trackDrawable = track
        }

        if (thumb != null) {
            thumb.alpha = 255
            // keep thumb white (professional). If you want thumb to also be role color, change below.
            tintDrawable(thumb, roleTheme.thumbColor)
            sw.thumbDrawable = thumb
        }

        sw.refreshDrawableState()
        sw.invalidate()
    }

    private fun tintDrawable(drawable: Drawable, color: Int) {
        val wrapped = DrawableCompat.wrap(drawable)
        DrawableCompat.setTint(wrapped, color)
    }

    private data class RoleTheme(
        val trackOnColor: Int,
        val thumbColor: Int
    ) {
        companion object {
            fun fromRole(roleRaw: String?): RoleTheme {
                val role = roleRaw?.trim()?.lowercase().orEmpty()
                return when {
                    role.contains("farmer") -> RoleTheme(
                        trackOnColor = 0xFF16A34A.toInt(), // green
                        thumbColor = 0xFFFFFFFF.toInt()
                    )
                    role.contains("distributor") -> RoleTheme(
                        trackOnColor = 0xFF2563EB.toInt(), // blue
                        thumbColor = 0xFFFFFFFF.toInt()
                    )
                    role.contains("retailer") -> RoleTheme(
                        trackOnColor = 0xFFF97316.toInt(), // orange
                        thumbColor = 0xFFFFFFFF.toInt()
                    )
                    role.contains("consumer") || role.contains("customer") -> RoleTheme(
                        trackOnColor = 0xFF7C3AED.toInt(), // purple
                        thumbColor = 0xFFFFFFFF.toInt()
                    )
                    else -> RoleTheme(
                        trackOnColor = 0xFF16A34A.toInt(), // default green
                        thumbColor = 0xFFFFFFFF.toInt()
                    )
                }
            }
        }
    }

    private fun showProfileVisibilityDialog() {
        val options = arrayOf("Public", "Private")
        val current = (prefs.getString("profile_visibility", "Public") ?: "Public").trim()
        val checkedIndex = options.indexOfFirst { it.equals(current, ignoreCase = true) }
            .let { if (it >= 0) it else 0 }

        AlertDialog.Builder(this)
            .setTitle("Profile Visibility")
            .setSingleChoiceItems(options, checkedIndex) { dialog, which ->
                val selected = options[which]
                prefs.edit().putString("profile_visibility", selected).apply()
                tvProfileVisibilityValue.text = selected
                dialog.dismiss()
                Toast.makeText(
                    this,
                    "Visibility set to ${selected.lowercase(Locale.getDefault())}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDownloadDataDialog() {
        AlertDialog.Builder(this)
            .setTitle("Download My Data")
            .setMessage("We’ll prepare your data export and notify you when it’s ready.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Request") { _, _ ->
                prefs.edit().putLong("last_data_export_request_at", System.currentTimeMillis()).apply()
                Toast.makeText(this, "Request submitted", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showDeleteAccountDialog() {
        AlertDialog.Builder(this)
            .setTitle("Delete Account")
            .setMessage("This action is permanent. Do you want to continue?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                prefs.edit().putBoolean("delete_requested", true).apply()
                Toast.makeText(this, "Delete request submitted", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}
