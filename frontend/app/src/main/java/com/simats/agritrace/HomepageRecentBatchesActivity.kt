package com.simats.agritrace.Farmer

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.simats.agritrace.R

import com.simats.agritrace.session.SessionManager
import com.google.android.material.button.MaterialButton

class HomepageRecentBatchesActivity : AppCompatActivity() {

    private lateinit var tvBatchCropName: TextView
    private lateinit var tvBatchCode: TextView
    private lateinit var tvTransferStatus: TextView
    private lateinit var tvBlockchainTx: TextView
    private lateinit var tvDistributorInfo: TextView
    private lateinit var tvQuantity: TextView // We will use this for Variety/Soil
    private lateinit var tvHarvestDate: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_homepage_recent_batches)

        bindViews()

        val batchId = intent.getStringExtra("batch_id")
        if (batchId == null) {
            Toast.makeText(this, "Invalid batch ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 1. SET INITIAL DATA FROM INTENT EXTRAS (Speed)
        // This makes the UI feel fast by loading passed data immediately
        displayIntentData()

        // 2. FETCH FULL DETAILS FROM API (Accuracy)
        loadBatchDetails(batchId)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun bindViews() {
        tvBatchCropName = findViewById(R.id.tvCropName)
        tvBatchCode = findViewById(R.id.tvBatchCode)
        tvTransferStatus = findViewById(R.id.tvTransferStatus)
        tvBlockchainTx = findViewById(R.id.tvBlockchainTx)
        tvDistributorInfo = findViewById(R.id.tvDistributorInfo)
        tvQuantity = findViewById(R.id.tvQuantity)
        tvHarvestDate = findViewById(R.id.tvHarvestDate)

        findViewById<MaterialButton>(R.id.btnDownloadQR).setOnClickListener {
            Toast.makeText(this, "Downloading QR...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun displayIntentData() {
        // Read data passed from HomeFragment
        val crop = intent.getStringExtra("crop_name") ?: "Crop"
        val variety = intent.getStringExtra("variety") ?: ""
        val soil = intent.getStringExtra("soil") ?: "N/A"
        val district = intent.getStringExtra("district") ?: ""
        val state = intent.getStringExtra("state") ?: ""

        tvBatchCropName.text = "$crop ($variety)"
        tvQuantity.text = soil // Displaying Soil type in the 'Quantity' box
        tvDistributorInfo.text = "Origin: $district, $state"
    }

    private fun loadBatchDetails(batchId: String) {
        val farmerId = SessionManager(this).getFarmerId() ?: return

        // We use your existing API call to get all farmer batches
    }
}