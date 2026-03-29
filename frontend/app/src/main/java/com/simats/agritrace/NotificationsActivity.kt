package com.simats.agritrace

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SwitchCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class NotificationsActivity : AppCompatActivity() {

    private lateinit var roleTheme: RoleTheme

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_notifications)

        // Apply window insets to root container, preserving original padding
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootNotifications)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val originalPaddingTop = v.paddingTop
            val originalPaddingBottom = v.paddingBottom
            val originalPaddingLeft = v.paddingLeft
            val originalPaddingRight = v.paddingRight
            
            v.setPadding(
                maxOf(originalPaddingLeft, systemBars.left),
                maxOf(originalPaddingTop, systemBars.top),
                maxOf(originalPaddingRight, systemBars.right),
                maxOf(originalPaddingBottom, systemBars.bottom)
            )
            insets
        }

        findViewById<ImageView>(R.id.ivBack).setOnClickListener { finish() }

        roleTheme = RoleTheme.fromRole(Session.role(this))

        val swPush = findViewById<SwitchCompat>(R.id.swPush)
        val swEmail = findViewById<SwitchCompat>(R.id.swEmail)
        val swSms = findViewById<SwitchCompat>(R.id.swSms)

        val swBatch = findViewById<SwitchCompat>(R.id.swBatch)
        val swVerify = findViewById<SwitchCompat>(R.id.swVerify)
        val swMarketing = findViewById<SwitchCompat>(R.id.swMarketing)

        // Apply theme once after layout
        val all = listOf(swPush, swEmail, swSms, swBatch, swVerify, swMarketing)
        all.forEach { sw ->
            sw.post { applyRoleSwitch(sw) }
        }

        bindSwitch(swPush, AppPrefs.notifPush(this)) {
            AppPrefs.setNotifPush(this, it)
            swPush.post { applyRoleSwitch(swPush) }
        }
        bindSwitch(swEmail, AppPrefs.notifEmail(this)) {
            AppPrefs.setNotifEmail(this, it)
            swEmail.post { applyRoleSwitch(swEmail) }
        }
        bindSwitch(swSms, AppPrefs.notifSms(this)) {
            AppPrefs.setNotifSms(this, it)
            swSms.post { applyRoleSwitch(swSms) }
        }

        bindSwitch(swBatch, AppPrefs.notifBatchUpdates(this)) {
            AppPrefs.setNotifBatchUpdates(this, it)
            swBatch.post { applyRoleSwitch(swBatch) }
        }
        bindSwitch(swVerify, AppPrefs.notifVerificationAlerts(this)) {
            AppPrefs.setNotifVerificationAlerts(this, it)
            swVerify.post { applyRoleSwitch(swVerify) }
        }
        bindSwitch(swMarketing, AppPrefs.notifMarketingEmails(this)) {
            AppPrefs.setNotifMarketingEmails(this, it)
            swMarketing.post { applyRoleSwitch(swMarketing) }
        }
    }

    private fun bindSwitch(sw: SwitchCompat, initial: Boolean, onChange: (Boolean) -> Unit) {
        sw.setOnCheckedChangeListener(null)
        sw.isChecked = initial
        sw.setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
    }

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
            tintAllStates(track, roleTheme.trackOnColor)
            sw.trackDrawable = track
        }

        if (thumb != null) {
            thumb.alpha = 255
            tintAllStates(thumb, roleTheme.thumbOnColor)
            sw.thumbDrawable = thumb
        }

        sw.refreshDrawableState()
        sw.invalidate()
    }

    private fun tintAllStates(drawable: Drawable, color: Int) {
        // Works for StateListDrawable too; it will tint contained drawables uniformly.
        val wrapped = DrawableCompat.wrap(drawable)
        DrawableCompat.setTint(wrapped, color)
    }

    private data class RoleTheme(
        val trackOnColor: Int,
        val thumbOnColor: Int
    ) {
        companion object {
            fun fromRole(roleRaw: String?): RoleTheme {
                val role = roleRaw?.trim()?.lowercase().orEmpty()

                return when {
                    role.contains("farmer") -> RoleTheme(
                        trackOnColor = 0xFF16A34A.toInt(), // green
                        thumbOnColor = 0xFFFFFFFF.toInt()
                    )

                    role.contains("distributor") -> RoleTheme(
                        trackOnColor = 0xFF2563EB.toInt(), // blue
                        thumbOnColor = 0xFFFFFFFF.toInt()
                    )

                    role.contains("retailer") -> RoleTheme(
                        trackOnColor = 0xFFF97316.toInt(), // orange
                        thumbOnColor = 0xFFFFFFFF.toInt()
                    )

                    role.contains("consumer") || role.contains("customer") -> RoleTheme(
                        trackOnColor = 0xFF7C3AED.toInt(), // purple
                        thumbOnColor = 0xFFFFFFFF.toInt()
                    )

                    else -> RoleTheme(
                        trackOnColor = 0xFF16A34A.toInt(), // default green
                        thumbOnColor = 0xFFFFFFFF.toInt()
                    )
                }
            }
        }
    }
}
