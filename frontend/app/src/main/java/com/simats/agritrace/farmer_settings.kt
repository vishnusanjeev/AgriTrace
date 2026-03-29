package com.simats.agritrace

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class farmer_settings : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("agritrace_prefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_farmer_settings)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootSettings)) { v, insets ->
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

        val ivBack = findViewById<ImageView>(R.id.ivBack)
        val layoutSimpleSetting = findViewById<View>(R.id.layoutSimpleSetting)
        val swSimpleSetting = findViewById<SwitchCompat>(R.id.swSimpleSetting)

        val layoutPrivacyPolicy = findViewById<View>(R.id.layoutPrivacyPolicy)
        val layoutTermsService = findViewById<View>(R.id.layoutTermsService)

        val tvAppVersionValue = findViewById<TextView>(R.id.tvAppVersionValue)

        ivBack.setOnClickListener { finish() }

        val saved = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        swSimpleSetting.isChecked = saved

        // ✅ Clicking the row opens NotificationsActivity
        layoutSimpleSetting.setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }

        // ✅ Switch still toggles + saves preference (same logic)
        swSimpleSetting.setOnClickListener {
            swSimpleSetting.isChecked = !swSimpleSetting.isChecked
        }

        swSimpleSetting.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_NOTIFICATIONS, isChecked).apply()
        }

        layoutPrivacyPolicy.setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }

        layoutTermsService.setOnClickListener {
            startActivity(Intent(this, terms_service::class.java))
        }

        tvAppVersionValue.text = "v1.0.0"
    }

    companion object {
        private const val KEY_NOTIFICATIONS = "settings_notifications"
    }
}
