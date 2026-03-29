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

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var btnBack: AppCompatImageButton
    private lateinit var etEmailPhone: AppCompatEditText
    private lateinit var btnSend: AppCompatButton
    private lateinit var tvBackToLogin: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_forgot_password)

        btnBack = findViewById(R.id.btnBack)
        etEmailPhone = findViewById(R.id.etEmailPhone)
        btnSend = findViewById(R.id.btnSend)
        tvBackToLogin = findViewById(R.id.tvBackToLogin)

        btnBack.setOnClickListener { finish() }
        tvBackToLogin.setOnClickListener { finish() }
        btnSend.setOnClickListener { sendOtp() }
    }

    private fun sendOtp() {
        val raw = etEmailPhone.text?.toString()?.trim().orEmpty()
        if (raw.isEmpty() || !raw.contains("@")) return toast("Enter a valid email address")

        btnSend.isEnabled = false
        btnSend.text = "Sending..."

        lifecycleScope.launch {
            try {
                val resp = ApiClient.authApi.requestResetOtp(
                    OtpRequestReq(email = raw.lowercase(), purpose = "RESET_PASSWORD")
                )
                if (resp.ok) {
                    toast(resp.message ?: "If an account exists, we sent a reset code.")
                    val i = Intent(this@ForgotPasswordActivity, ResetPasswordActivity::class.java)
                    i.putExtra("email", raw.lowercase())
                    startActivity(i)
                    finish()
                } else {
                    toast(resp.error ?: "Couldn't send reset code. Try again.")
                }
            } catch (e: Exception) {
                toast(extractNetworkMessage(e))
            } finally {
                btnSend.isEnabled = true
                btnSend.text = "Send Code"
            }
        }
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

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
