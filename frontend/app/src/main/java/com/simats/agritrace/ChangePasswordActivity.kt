package com.simats.agritrace

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.CountDownTimer
import android.view.HapticFeedbackConstants
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class ChangePasswordActivity : AppCompatActivity() {

    private lateinit var btnBack: AppCompatImageButton
    private lateinit var etOtp: AppCompatEditText
    private lateinit var etNewPassword: AppCompatEditText
    private lateinit var etConfirmPassword: AppCompatEditText
    private lateinit var btnSendOtp: AppCompatButton
    private lateinit var btnUpdate: AppCompatButton
    private lateinit var tvResend: TextView
    private lateinit var tvEmailHint: TextView

    private var email: String = ""
    private var otpSent: Boolean = false
    private var resendTimer: CountDownTimer? = null

    private lateinit var theme: RoleTheme

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_change_password)

        btnBack = findViewById(R.id.btnBackChangePass)
        etOtp = findViewById(R.id.etOtpChangePass)
        etNewPassword = findViewById(R.id.etNewPasswordChangePass)
        etConfirmPassword = findViewById(R.id.etConfirmPasswordChangePass)
        btnSendOtp = findViewById(R.id.btnSendOtpChangePass)
        btnUpdate = findViewById(R.id.btnUpdateChangePass)
        tvResend = findViewById(R.id.tvResendChangePass)
        tvEmailHint = findViewById(R.id.tvEmailHintChangePass)

        email = Session.email(this).orEmpty().trim()

        if (email.isBlank()) {
            toast("Login required. Please sign in again.")
            startActivity(Intent(this, LoginActivity::class.java))
            finishAffinity()
            return
        }

        theme = RoleTheme.fromRole(Session.role(this))
        applyRoleColors()

        tvEmailHint.text = maskEmail(email)

        setInputsEnabled(false)
        setResendEnabled(false, "Resend code")

        btnBack.setOnClickListener { finish() }

        btnSendOtp.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            sendOtp()
        }

        tvResend.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            resendOtp()
        }

        btnUpdate.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            updatePassword()
        }
    }

    private fun applyRoleColors() {
        btnSendOtp.backgroundTintList = ColorStateList.valueOf(theme.accent)
        btnSendOtp.setTextColor(0xFFFFFFFF.toInt())

        btnUpdate.backgroundTintList = ColorStateList.valueOf(theme.accent)
        btnUpdate.setTextColor(0xFFFFFFFF.toInt())

        tvResend.setTextColor(theme.accent)

        btnBack.drawable?.mutate()?.let { d ->
            val wrapped = DrawableCompat.wrap(d)
            DrawableCompat.setTint(wrapped, 0xFF111827.toInt())
            btnBack.setImageDrawable(wrapped)
        }
    }

    private fun sendOtp() {
        setSendLoading(true)

        lifecycleScope.launch {
            try {
                val resp = ApiClient.authApi.requestResetOtp(
                    OtpRequestReq(email = email, purpose = "RESET_PASSWORD")
                )
                if (resp.ok) {
                    otpSent = true
                    toast(resp.message ?: "OTP sent to your email")
                    setInputsEnabled(true)
                    startResendCooldown()
                } else {
                    toast(resp.error ?: "Failed to send OTP. Try again.")
                }
            } catch (e: Exception) {
                toast(extractNetworkMessage(e))
            } finally {
                setSendLoading(false)
            }
        }
    }

    private fun resendOtp() {
        if (!tvResend.isEnabled) return
        setResendEnabled(false, "Sending...")

        lifecycleScope.launch {
            try {
                val resp = ApiClient.authApi.requestResetOtp(
                    OtpRequestReq(email = email, purpose = "CHANGE_PASSWORD")
                )
                if (resp.ok) {
                    toast(resp.message ?: "OTP resent")
                    startResendCooldown()
                } else {
                    toast(resp.error ?: "Couldn't resend OTP")
                    setResendEnabled(true, "Resend code")
                }
            } catch (e: Exception) {
                toast(extractNetworkMessage(e))
                setResendEnabled(true, "Resend code")
            }
        }
    }

    private fun updatePassword() {
        if (!otpSent) return toast("Please send OTP first")

        val otp = etOtp.text?.toString()?.trim().orEmpty()
        val p1 = etNewPassword.text?.toString()?.trim().orEmpty()
        val p2 = etConfirmPassword.text?.toString()?.trim().orEmpty()

        if (otp.length != 6) return toast("Enter 6-digit OTP")
        if (p1.length < 6) return toast("Password must be at least 6 characters")
        if (p1 != p2) return toast("Passwords do not match")

        setUpdateLoading(true)

        lifecycleScope.launch {
            try {
                val resp = ApiClient.authApi.resetPassword(
                    ResetPasswordReq(email = email, otp = otp, new_password = p1)
                )
                if (resp.ok) {
                    // keep role for success theme before session clear
                    val role = Session.role(this@ChangePasswordActivity)

                    // Security best practice: clear session so user re-authenticates
                    Session.clear(this@ChangePasswordActivity)
                    AppPrefs.clearPendingEmailVerification(this@ChangePasswordActivity)

                    val i = Intent(this@ChangePasswordActivity, ResetPasswordSuccessActivity::class.java)
                    i.putExtra(ResetPasswordSuccessActivity.EXTRA_ROLE, role)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(i)
                    finish()
                } else {
                    toast(resp.error ?: "Update failed. Try again.")
                }
            } catch (e: Exception) {
                toast(extractNetworkMessage(e))
            } finally {
                setUpdateLoading(false)
            }
        }
    }

    private fun setInputsEnabled(enabled: Boolean) {
        etOtp.isEnabled = enabled
        etNewPassword.isEnabled = enabled
        etConfirmPassword.isEnabled = enabled
        btnUpdate.isEnabled = enabled

        val a = if (enabled) 1f else 0.6f
        etOtp.alpha = a
        etNewPassword.alpha = a
        etConfirmPassword.alpha = a
        btnUpdate.alpha = if (enabled) 1f else 0.75f

        tvResend.isEnabled = enabled
        tvResend.alpha = if (enabled) 1f else 0.6f
    }

    private fun startResendCooldown(seconds: Int = 30) {
        resendTimer?.cancel()
        resendTimer = object : CountDownTimer((seconds * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val s = (millisUntilFinished / 1000).toInt()
                setResendEnabled(false, "Resend in ${s}s")
            }

            override fun onFinish() {
                setResendEnabled(true, "Resend code")
            }
        }.start()
    }

    private fun setResendEnabled(enabled: Boolean, text: String) {
        tvResend.isEnabled = enabled
        tvResend.text = text
        tvResend.alpha = if (enabled) 1f else 0.6f
        tvResend.setTextColor(theme.accent)
    }

    private fun setSendLoading(loading: Boolean) {
        btnSendOtp.isEnabled = !loading
        btnSendOtp.text = if (loading) "Sending..." else "Send OTP"
        btnSendOtp.backgroundTintList = ColorStateList.valueOf(theme.accent)
    }

    private fun setUpdateLoading(loading: Boolean) {
        btnUpdate.isEnabled = !loading
        btnUpdate.text = if (loading) "Updating..." else "Update Password"
        btnUpdate.backgroundTintList = ColorStateList.valueOf(theme.accent)
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun maskEmail(email: String): String {
        val at = email.indexOf("@")
        if (at <= 1) return email
        val name = email.substring(0, at)
        val domain = email.substring(at)
        val masked = name.take(1) + "••••" + name.takeLast(1)
        return "$masked$domain"
    }

    private fun extractNetworkMessage(e: Exception): String {
        return when (e) {
            is retrofit2.HttpException -> {
                val code = e.code()
                val raw = try { e.response()?.errorBody()?.string().orEmpty() } catch (_: Exception) { "" }

                val serverMsg = try {
                    val obj = org.json.JSONObject(raw)
                    obj.optString("error").ifBlank { obj.optString("message") }
                } catch (_: Exception) { "" }

                when {
                    serverMsg.isNotBlank() -> serverMsg
                    raw.isNotBlank() -> "HTTP $code ${raw.take(140)}"
                    else -> "Request failed (HTTP $code)"
                }
            }
            is java.net.UnknownHostException -> "No internet / wrong server IP"
            is java.net.SocketTimeoutException -> "Server timeout. Try again."
            is javax.net.ssl.SSLHandshakeException -> "SSL error (https mismatch)"
            else -> (e.message ?: "Network/server error")
        }
    }

    override fun onDestroy() {
        resendTimer?.cancel()
        super.onDestroy()
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
