// LoginActivity.kt  ✅ routes by role
package com.simats.agritrace

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: AppCompatEditText
    private lateinit var etPassword: AppCompatEditText
    private lateinit var btnSignIn: AppCompatButton
    private lateinit var tvForgot: TextView
    private lateinit var tvRegister: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        ApiClient.init(applicationContext)
        setContentView(R.layout.activity_login)

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnSignIn = findViewById(R.id.btnSignIn)
        tvForgot = findViewById(R.id.tvForgot)
        tvRegister = findViewById(R.id.tvRegister)

        btnSignIn.setOnClickListener { doLogin() }
        tvRegister.setOnClickListener { startActivity(Intent(this, RegistrationActivity::class.java)) }
        tvForgot.setOnClickListener { startActivity(Intent(this, ForgotPasswordActivity::class.java)) }
    }

    private fun doLogin() {
        val identifierRaw = etEmail.text?.toString()?.trim().orEmpty()
        val password = etPassword.text?.toString()?.trim().orEmpty()

        if (identifierRaw.isEmpty()) return toast("Enter email or mobile number")
        if (password.isEmpty()) return toast("Enter password")

        val identifier = normalizeIdentifier(identifierRaw)

        setLoading(true)

        lifecycleScope.launch {
            try {
                val resp = ApiClient.authApi.login(
                    LoginReq(identifier = identifier, password = password)
                )

                if (resp.ok && !resp.token.isNullOrBlank() && (resp.user?.id ?: 0L) > 0L) {

                    // ✅ success login: clear pending states
                    AppPrefs.clearPendingEmailVerification(this@LoginActivity)

                    Session.save(this@LoginActivity, resp.token!!, resp.user)
                    AppPrefs.markFirstLaunchDone(this@LoginActivity)

                    val next = RoleRouter.dashboardIntent(this@LoginActivity, resp.user?.role)
                    next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(next)
                    finish()

                } else {
                    val msg = resp.error ?: "Invalid credentials"

                    // ✅ If backend indicates email not verified / inactive, send to OTP
                    val looksLikeNeedsVerify =
                        msg.contains("verify", true) ||
                                msg.contains("otp", true) ||
                                msg.contains("inactive", true) ||
                                msg.contains("activate", true) ||
                                msg.contains("disabled", true)

                    val emailGuess = identifierRaw.trim().lowercase()

                    if (looksLikeNeedsVerify && emailGuess.contains("@")) {
                        // strict: mark pending so Splash also redirects correctly
                        AppPrefs.setPendingEmailVerification(
                            this@LoginActivity,
                            email = emailGuess,
                            purpose = "VERIFY_EMAIL"
                        )

                        // optionally trigger resend OTP (safe best-effort)
                        try {
                            ApiClient.authApi.requestVerifyEmailOtp(
                                OtpRequestReq(email = emailGuess, purpose = "VERIFY_EMAIL")
                            )
                        } catch (_: Exception) { }

                        val i = Intent(this@LoginActivity, EmailOtpActivity::class.java)
                        i.putExtra("email", emailGuess)
                        i.putExtra("purpose", "VERIFY_EMAIL")
                        startActivity(i)
                        finish()
                    } else {
                        toast(msg)
                    }
                }

            } catch (e: Exception) {
                toast(extractNetworkMessage(e))
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        btnSignIn.isEnabled = !loading
        btnSignIn.text = if (loading) "Signing In..." else "Sign In"
    }


    private fun extractNetworkMessage(e: Exception): String {
        return when (e) {
            is retrofit2.HttpException -> {
                val code = e.code()
                val raw = try { e.response()?.errorBody()?.string().orEmpty() } catch (_: Exception) { "" }

                // try parse {"ok":false,"error":"..."}
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

    private fun normalizeIdentifier(input: String): String {
        val s = input.trim()
        return if (s.contains("@")) s.lowercase()
        else {
            val cleaned = s.replace("""[^\d+]""".toRegex(), "")
            if (!cleaned.startsWith("+") && cleaned.length == 10) "+91$cleaned" else cleaned
        }
    }
}
