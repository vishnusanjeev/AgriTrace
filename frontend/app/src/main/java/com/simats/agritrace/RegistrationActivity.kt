package com.simats.agritrace

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class RegistrationActivity : AppCompatActivity() {

    private lateinit var backButton: ImageView
    private lateinit var fullNameInput: TextInputEditText
    private lateinit var emailInput: TextInputEditText
    private lateinit var mobileInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText

    private lateinit var roleFarmer: MaterialButton
    private lateinit var roleDistributor: MaterialButton
    private lateinit var roleRetailer: MaterialButton
    private lateinit var roleConsumer: MaterialButton

    private lateinit var createAccountButton: MaterialButton
    private lateinit var loginAction: TextView

    private var selectedRole: String = "FARMER"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_registration)

        backButton = findViewById(R.id.backButton)
        fullNameInput = findViewById(R.id.fullNameInput)
        emailInput = findViewById(R.id.emailInput)
        mobileInput = findViewById(R.id.mobileInput)
        passwordInput = findViewById(R.id.passwordInput)

        roleFarmer = findViewById(R.id.roleFarmer)
        roleDistributor = findViewById(R.id.roleDistributor)
        roleRetailer = findViewById(R.id.roleRetailer)
        roleConsumer = findViewById(R.id.roleConsumer)

        createAccountButton = findViewById(R.id.createAccountButton)
        loginAction = findViewById(R.id.loginAction)

        backButton.setOnClickListener { finish() }
        loginAction.setOnClickListener { finish() }

        roleFarmer.setOnClickListener { selectRole("FARMER") }
        roleDistributor.setOnClickListener { selectRole("DISTRIBUTOR") }
        roleRetailer.setOnClickListener { selectRole("RETAILER") }
        roleConsumer.setOnClickListener { selectRole("CONSUMER") }

        createAccountButton.setOnClickListener { doRegister() }

        selectRole("FARMER")
    }

    private fun doRegister() {
        val name = fullNameInput.text?.toString()?.trim().orEmpty()
        val email = emailInput.text?.toString()?.trim().orEmpty().lowercase()
        val phoneRaw = mobileInput.text?.toString()?.trim().orEmpty()
        val pass = passwordInput.text?.toString()?.trim().orEmpty()

        if (name.isEmpty()) return toast("Enter full name")
        if (email.isBlank()) return toast("Email is required for OTP verification")
        if (!email.contains("@")) return toast("Enter valid email")
        if (pass.length < 6) return toast("Password must be at least 6 characters")

        val phone = if (phoneRaw.isNotEmpty()) normalizePhone(phoneRaw) else null

        val req = RegisterReq(
            full_name = name,
            email = email,
            phone_e164 = phone,
            password = pass,
            role = selectedRole
        )

        setLoading(true)

        lifecycleScope.launch {
            try {
                val resp = ApiClient.authApi.register(req)

                if (resp.ok) {
                    // ✅ STRICT: never keep any old session after register
                    Session.clear(this@RegistrationActivity)

                    // ✅ STRICT: set pending verification so Splash can’t open dashboards
                    AppPrefs.setPendingEmailVerification(
                        this@RegistrationActivity,
                        email = email,
                        purpose = "VERIFY_EMAIL"
                    )

                    // ✅ Send OTP (backend should create auth_otps row + email)
                    val otpResp = ApiClient.authApi.requestVerifyEmailOtp(
                        OtpRequestReq(email = email, purpose = "VERIFY_EMAIL")
                    )

                    if (otpResp.ok) {
                        val i = Intent(this@RegistrationActivity, EmailOtpActivity::class.java)
                        i.putExtra("email", email)
                        i.putExtra("purpose", "VERIFY_EMAIL")
                        startActivity(i)
                        finish()
                    } else {
                        // keep pending state but notify; user can tap resend from OTP screen
                        toast(otpResp.error ?: otpResp.message ?: "OTP send failed")
                        val i = Intent(this@RegistrationActivity, EmailOtpActivity::class.java)
                        i.putExtra("email", email)
                        i.putExtra("purpose", "VERIFY_EMAIL")
                        startActivity(i)
                        finish()
                    }

                } else {
                    toast(resp.error ?: "Registration failed")
                }

            } catch (e: Exception) {
                toast(extractNetworkMessage(e))
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        createAccountButton.isEnabled = !loading
        createAccountButton.text = if (loading) "Creating..." else "Create Account"
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


    private fun selectRole(role: String) {
        selectedRole = role
        styleRole(roleFarmer, role == "FARMER")
        styleRole(roleDistributor, role == "DISTRIBUTOR")
        styleRole(roleRetailer, role == "RETAILER")
        styleRole(roleConsumer, role == "CONSUMER")
    }

    private fun styleRole(btn: MaterialButton, selected: Boolean) {
        btn.strokeWidth = if (selected) dp(2) else 0
        btn.strokeColor =
            if (selected) android.content.res.ColorStateList.valueOf(Color.parseColor("#059669"))
            else null
    }



    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun normalizePhone(input: String): String {
        val raw = input.replace("""[\s\-\(\)\.]""".toRegex(), "")
        if (raw.startsWith("+")) return raw
        val digits = raw.replace("""[^\d]""".toRegex(), "")
        return if (digits.length == 10) "+91$digits" else digits
    }
}
