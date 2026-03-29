package com.simats.agritrace

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

class SplashActivity : AppCompatActivity() {

    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_AgriTrace)

        if (!isTaskRoot && intent?.hasCategory(Intent.CATEGORY_LAUNCHER) == true && intent?.action == Intent.ACTION_MAIN) {
            finish(); return
        }

        super.onCreate(savedInstanceState)
        ApiClient.init(applicationContext)
        enableEdgeToEdge()

        setContentView(R.layout.activity_splash)
        window.decorView.post { goNext() }
    }

    private fun goNext() {
        if (navigated) return
        navigated = true

        if (AppPrefs.isFirstLaunch(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        // ✅ STRICT: pending verification always wins
        val pendingEmail = AppPrefs.pendingEmail(this)
        if (!pendingEmail.isNullOrBlank()) {
            // clear any accidental saved session
            Session.clear(this)

            val i = Intent(this, EmailOtpActivity::class.java)
            i.putExtra("email", pendingEmail)
            i.putExtra("purpose", AppPrefs.pendingPurpose(this))
            startActivity(i)
            finish()
            return
        }

        // ✅ STRICT: only go dashboard if session is truly valid
        if (Session.ensureValidOrClear(this, allowClear = false)) {
            startActivity(RoleRouter.dashboardIntent(this, Session.role(this)))
        } else {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish()
    }


    private fun setStatusBar(colorHex: String, lightIcons: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = Color.parseColor(colorHex)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController ?: return
            val lightFlag = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            if (lightIcons) controller.setSystemBarsAppearance(0, lightFlag)
            else controller.setSystemBarsAppearance(lightFlag, lightFlag)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (lightIcons) {
                window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            } else {
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
    }
}
