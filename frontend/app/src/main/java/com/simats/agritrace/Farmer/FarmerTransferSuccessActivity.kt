package com.simats.agritrace.Farmer

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.simats.agritrace.R
import com.google.android.material.button.MaterialButton

class FarmerTransferSuccessActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var didNavigate = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_farmer_transfer_success)

        val tvBatchValue = findViewById<TextView>(R.id.tvBatchValue)
        val tvProductValue = findViewById<TextView>(R.id.tvProductValue)
        val tvToValue = findViewById<TextView>(R.id.tvToValue)
        val tvRedirect = findViewById<TextView>(R.id.tvRedirect)
        val btnBackDashboard = findViewById<MaterialButton>(R.id.btnBackDashboard)

        val batchCode = intent.getStringExtra("batch_code")
            ?: intent.getStringExtra("batchCode")
            ?: intent.getStringExtra("batch_id_str")
            ?: "B-2023-001"

        val productName = intent.getStringExtra("product_name")
            ?: intent.getStringExtra("productName")
            ?: "Organic Tomatoes"

        val distributorId = intent.getStringExtra("distributor_id")
            ?: intent.getStringExtra("distributorId")
            ?: "D-001"
        val distributorName = intent.getStringExtra("distributor_name")
            ?: intent.getStringExtra("distributorName")

        tvBatchValue.text = batchCode
        tvProductValue.text = productName
        tvToValue.text = if (!distributorName.isNullOrBlank()) {
            "$distributorName (ID: $distributorId)"
        } else {
            distributorId
        }

        btnBackDashboard.setOnClickListener { goDashboard() }

        tvRedirect.text = "Redirecting automatically in 1 seconds..."
        handler.postDelayed({ goDashboard() }, 1000L)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    private fun goDashboard() {
        if (didNavigate) return
        didNavigate = true

        val i = Intent(this, com.simats.agritrace.FarmerDashboardActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(i)
        finish()
    }
}
