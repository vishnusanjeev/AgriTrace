package com.simats.agritrace

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton

class ResetPasswordSuccessActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ROLE = "extra_role"
    }

    private lateinit var theme: RoleTheme

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_password_reset_success)

        val role = intent.getStringExtra(EXTRA_ROLE)
        theme = RoleTheme.fromRole(role)

        applyRoleColors()

        findViewById<MaterialButton>(R.id.btnContinue).setOnClickListener {
            val i = Intent(this, LoginActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(i)
            finish()
        }
    }

    private fun applyRoleColors() {
        // Continue button
        findViewById<MaterialButton>(R.id.btnContinue).apply {
            backgroundTintList = ColorStateList.valueOf(theme.accent)
            setTextColor(0xFFFFFFFF.toInt())
        }

        // Outer circle bg + check icon tint
        val outer = findViewById<ImageView>(R.id.ivSuccessOuter)
        val check = findViewById<ImageView>(R.id.ivSuccess)

        // outer: soft background with accent border-like feel (uses alpha)
        outer.background?.mutate()?.let { d ->
            val wrapped = DrawableCompat.wrap(d)
            DrawableCompat.setTint(wrapped, theme.accentSoft)
            outer.background = wrapped
        }

        check.drawable?.mutate()?.let { d ->
            val wrapped = DrawableCompat.wrap(d)
            DrawableCompat.setTint(wrapped, theme.accent)
            check.setImageDrawable(wrapped)
        }
    }

    private data class RoleTheme(val accent: Int, val accentSoft: Int) {
        companion object {
            fun fromRole(roleRaw: String?): RoleTheme {
                val role = roleRaw?.trim()?.lowercase().orEmpty()
                return when {
                    role.contains("farmer") -> RoleTheme(0xFF16A34A.toInt(), 0xFFECFDF3.toInt())
                    role.contains("distributor") -> RoleTheme(0xFF2563EB.toInt(), 0xFFEFF6FF.toInt())
                    role.contains("retailer") -> RoleTheme(0xFFF97316.toInt(), 0xFFFFF7ED.toInt())
                    role.contains("consumer") || role.contains("customer") ->
                        RoleTheme(0xFF7C3AED.toInt(), 0xFFF5F3FF.toInt())
                    else -> RoleTheme(0xFF16A34A.toInt(), 0xFFECFDF3.toInt())
                }
            }
        }
    }
}
