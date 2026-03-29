package com.simats.agritrace

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageButton
import android.widget.TextView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var btnBack: AppCompatImageButton
    private lateinit var etOtp: AppCompatEditText
    private lateinit var etNewPassword: AppCompatEditText
    private lateinit var etConfirmPassword: AppCompatEditText
    private lateinit var btnReset: AppCompatButton
    private lateinit var tvResend: TextView

    private var email: String = ""
    private var resendTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_reset_password)

        btnBack = findViewById(R.id.btnBackReset)
        etOtp = findViewById(R.id.etOtp)
        etNewPassword = findViewById(R.id.etNewPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnReset = findViewById(R.id.btnReset)
        tvResend = findViewById(R.id.tvResendReset)

        email = intent.getStringExtra("email") ?: ""

        btnBack.setOnClickListener { finish() }
        btnReset.setOnClickListener { reset() }
        tvResend.setOnClickListener { resend() }
        startResendCooldown()
    }

    private fun reset() {
        val otp = etOtp.text?.toString()?.trim().orEmpty()
        val p1 = etNewPassword.text?.toString()?.trim().orEmpty()
        val p2 = etConfirmPassword.text?.toString()?.trim().orEmpty()

        if (otp.length != 6) return toast("Enter 6-digit OTP")
        if (p1.length < 6) return toast("Password must be at least 6 characters")
        if (p1 != p2) return toast("Passwords do not match")

        setLoading(true)
        lifecycleScope.launch {
            try {
                val resp = ApiClient.authApi.resetPassword(
                    ResetPasswordReq(email = email, otp = otp, new_password = p1)
                )
                if (resp.ok) {
                    toast(resp.message ?: "Password updated. Please log in.")
                    startActivity(Intent(this@ResetPasswordActivity, LoginActivity::class.java))
                    finishAffinity()
                } else {
                    toast(resp.error ?: "Reset failed. Try again.")
                }
            } catch (e: Exception) {
                toast(extractNetworkMessage(e))
            } finally {
                setLoading(false)
            }
        }
    }

    private fun resend() {
        if (email.isBlank()) return toast("Email missing. Please go back and try again.")

        setResendEnabled(false, "Sending...")
        lifecycleScope.launch {
            try {
                val resp = ApiClient.authApi.requestResetOtp(
                    OtpRequestReq(email = email, purpose = "RESET_PASSWORD")
                )
                if (resp.ok) {
                    toast(resp.message ?: "Reset code sent.")
                    startResendCooldown()
                } else {
                    toast(resp.error ?: "Couldn't send reset code. Try again.")
                    setResendEnabled(true, "Resend code")
                }
            } catch (e: Exception) {
                toast(extractNetworkMessage(e))
                setResendEnabled(true, "Resend code")
            }
        }
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
    }

    private fun setLoading(loading: Boolean) {
        btnReset.isEnabled = !loading
        btnReset.text = if (loading) "Updating..." else "Update Password"
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

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
}
