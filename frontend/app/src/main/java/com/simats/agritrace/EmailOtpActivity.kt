package com.simats.agritrace

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import retrofit2.HttpException

class EmailOtpActivity : AppCompatActivity() {

    private lateinit var btnBack: AppCompatImageButton
    private lateinit var etOtp: AppCompatEditText
    private lateinit var btnVerify: AppCompatButton
    private lateinit var tvResend: TextView

    private var email: String = ""
    private var purpose: String = "VERIFY_EMAIL"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_email_otp)

        btnBack = findViewById(R.id.btnBackOtp)
        etOtp = findViewById(R.id.etOtp)
        btnVerify = findViewById(R.id.btnVerify)
        tvResend = findViewById(R.id.tvResend)

        email = intent.getStringExtra("email")?.trim().orEmpty()
        purpose = intent.getStringExtra("purpose")?.trim()?.ifBlank { "VERIFY_EMAIL" } ?: "VERIFY_EMAIL"

        btnBack.setOnClickListener { finish() }
        btnVerify.setOnClickListener { verify() }
        tvResend.setOnClickListener { resend() }
    }

    private fun verify() {
        val otp = etOtp.text?.toString()?.trim().orEmpty()

        if (email.isBlank()) return toast("Email missing. Please go back and try again.")
        if (otp.length != 6) return toast("Enter 6-digit OTP")

        setLoading(true)

        lifecycleScope.launch {
            try {
                val resp = ApiClient.authApi.verifyOtp(
                    OtpVerifyReq(email = email, purpose = purpose, otp = otp)
                )

                val token = resp.token
                val user = resp.user

                if (resp.ok && !token.isNullOrBlank() && user != null && user.id > 0L) {

                    // ✅ STRICT: session becomes valid only after verify
                    Session.save(this@EmailOtpActivity, token, user)

                    // ✅ clear pending verification so Splash won’t keep redirecting
                    AppPrefs.clearPendingEmailVerification(this@EmailOtpActivity)

                    AppPrefs.markFirstLaunchDone(this@EmailOtpActivity)

                    val next = RoleRouter.dashboardIntent(this@EmailOtpActivity, user.role)
                    next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(next)
                    finish()

                } else {
                    toast(resp.error ?: resp.message ?: "Invalid OTP")
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

        setLoading(true)

        lifecycleScope.launch {
            try {
                val resp = ApiClient.authApi.requestVerifyEmailOtp(
                    OtpRequestReq(email = email, purpose = purpose)
                )
                if (resp.ok) toast(resp.message ?: "OTP sent")
                else toast(resp.error ?: "Failed to resend OTP")
            } catch (e: Exception) {
                toast(extractNetworkMessage(e))
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        btnVerify.isEnabled = !loading
        btnVerify.text = if (loading) "Verifying..." else "Verify"
        tvResend.isEnabled = !loading
        etOtp.isEnabled = !loading
        btnBack.isEnabled = !loading
    }




    private fun extractNetworkMessage(e: Exception): String {
        return when (e) {
            is HttpException -> {
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



    private fun setResendLoading(loading: Boolean) {
        tvResend.isEnabled = !loading
        btnVerify.isEnabled = !loading
        etOtp.isEnabled = !loading
        btnBack.isEnabled = !loading
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
