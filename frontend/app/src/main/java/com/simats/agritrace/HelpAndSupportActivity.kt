package com.simats.agritrace

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class HelpAndSupportActivity : AppCompatActivity() {

    private val supportEmail = "support@agritrace.app"
    private val supportPhone = "+15550100"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_help_support)

        // Apply window insets to root container, preserving original padding
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootHelpSupport)) { v, insets ->
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

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        applyRoleColors()

        val faq1 = bindFaq(R.id.faq1Root, R.id.faq1Answer, R.id.faq1Chevron)
        val faq2 = bindFaq(R.id.faq2Root, R.id.faq2Answer, R.id.faq2Chevron)
        val faq3 = bindFaq(R.id.faq3Root, R.id.faq3Answer, R.id.faq3Chevron)
        val faq4 = bindFaq(R.id.faq4Root, R.id.faq4Answer, R.id.faq4Chevron)

        setFaqToggle(faq1)
        setFaqToggle(faq2)
        setFaqToggle(faq3)
        setFaqToggle(faq4)

        val etSubject = findViewById<TextInputEditText>(R.id.etSubject)
        val etMessage = findViewById<TextInputEditText>(R.id.etMessage)

        findViewById<MaterialButton>(R.id.btnSendMessage).setOnClickListener {
            val subject = etSubject.text?.toString()?.trim().orEmpty()
            val message = etMessage.text?.toString()?.trim().orEmpty()

            if (subject.isEmpty()) {
                Toast.makeText(this, "Please enter a subject", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (message.isEmpty()) {
                Toast.makeText(this, "Please enter your message", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val uri = Uri.parse("mailto:$supportEmail")
                .buildUpon()
                .appendQueryParameter("subject", subject)
                .appendQueryParameter("body", message)
                .build()

            val intent = Intent(Intent.ACTION_SENDTO).apply { data = uri }
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<View>(R.id.cardCallUs).setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$supportPhone")
            }
            startActivity(intent)
        }

        findViewById<View>(R.id.cardEmailUs).setOnClickListener {
            val uri = Uri.parse("mailto:$supportEmail")
            val intent = Intent(Intent.ACTION_SENDTO).apply { data = uri }
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyRoleColors() {
        val theme = RoleTheme.fromRole(Session.role(this))

        val btn = findViewById<MaterialButton>(R.id.btnSendMessage)
        btn.backgroundTintList = ColorStateList.valueOf(theme.accent)

        tintImage(R.id.cardCallUs, R.id.ic_phone, theme.accent)
        tintImage(R.id.cardEmailUs, R.id.ic_mail, theme.accent)

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        btnBack.drawable?.mutate()?.let { d ->
            val wrapped = DrawableCompat.wrap(d)
            DrawableCompat.setTint(wrapped, 0xFF111827.toInt())
            btnBack.setImageDrawable(wrapped)
        }
    }

    private fun tintImage(containerId: Int, imageId: Int, color: Int) {
        val container = findViewById<View>(containerId)
        val iv = container.findViewById<ImageView>(imageId)
        iv.drawable?.mutate()?.let { d ->
            val wrapped = DrawableCompat.wrap(d)
            DrawableCompat.setTint(wrapped, color)
            iv.setImageDrawable(wrapped)
        }
    }

    private data class FaqBinding(
        val root: LinearLayout,
        val answer: View,
        val chevron: ImageView
    )

    private fun bindFaq(rootId: Int, answerId: Int, chevronId: Int): FaqBinding {
        return FaqBinding(
            findViewById(rootId),
            findViewById(answerId),
            findViewById(chevronId)
        )
    }

    private fun setFaqToggle(faq: FaqBinding) {
        faq.root.setOnClickListener {
            val parent = faq.root.parent as View
            TransitionManager.beginDelayedTransition(parent as ViewGroup, AutoTransition().apply { duration = 180 })
            val showing = faq.answer.visibility == View.VISIBLE
            faq.answer.visibility = if (showing) View.GONE else View.VISIBLE
            faq.chevron.setImageResource(if (showing) R.drawable.ic_chevron_down else R.drawable.ic_chevron_up)
        }
    }

    private data class RoleTheme(val accent: Int, val accentSoft: Int) {
        companion object {
            fun fromRole(roleRaw: String?): RoleTheme {
                val role = roleRaw?.trim()?.lowercase().orEmpty()
                return when {
                    role.contains("farmer") -> RoleTheme(
                        accent = 0xFF16A34A.toInt(),
                        accentSoft = 0xFFECFDF3.toInt()
                    )
                    role.contains("distributor") -> RoleTheme(
                        accent = 0xFF2563EB.toInt(),
                        accentSoft = 0xFFEFF6FF.toInt()
                    )
                    role.contains("retailer") -> RoleTheme(
                        accent = 0xFFF97316.toInt(),
                        accentSoft = 0xFFFFF7ED.toInt()
                    )
                    role.contains("consumer") || role.contains("customer") -> RoleTheme(
                        accent = 0xFF7C3AED.toInt(),
                        accentSoft = 0xFFF5F3FF.toInt()
                    )
                    else -> RoleTheme(
                        accent = 0xFF16A34A.toInt(),
                        accentSoft = 0xFFECFDF3.toInt()
                    )
                }
            }
        }
    }
}
