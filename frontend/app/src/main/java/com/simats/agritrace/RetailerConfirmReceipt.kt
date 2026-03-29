package com.simats.agritrace

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.simats.agritrace.databinding.ActivityConfirmReceiptBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RetailerConfirmReceipt : AppCompatActivity() {

    private lateinit var binding: ActivityConfirmReceiptBinding
    private var currentBatchId: Int = -1
    private var currentBatchCode: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        binding = ActivityConfirmReceiptBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
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
        // Auto-fill current date & time (matches screenshot format)
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
        binding.etDateTime.setText(sdf.format(Date()))

        // Back
        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        currentBatchId = readBatchIdFromIntent()
        currentBatchCode = readBatchCodeFromIntent()
        bindBatchHeader()
        if (currentBatchId > 0 || currentBatchCode.isNotBlank()) {
            loadBatchHeaderIfMissing()
        } else {
            Toast.makeText(this, "Batch ID missing", Toast.LENGTH_SHORT).show()
        }

        // Confirm & Lock
        binding.btnConfirmLock.setOnClickListener {
            val batchId = currentBatchId
            if (batchId <= 0) {
                Toast.makeText(this, "Batch ID missing", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val dateTime = binding.etDateTime.text?.toString()?.trim().orEmpty()
            val storeLocation = binding.etStoreLocation.text?.toString()?.trim().orEmpty()

            if (dateTime.isEmpty() || storeLocation.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    val resp = withContext(Dispatchers.IO) {
                        ApiClient.retailerApi.confirmReceipt(
                            ConfirmReceiptReq(
                                batch_id = batchId,
                                store_location = storeLocation,
                                date_time = dateTime
                            )
                        )
                    }
                    if (!resp.ok) {
                        Toast.makeText(this@RetailerConfirmReceipt, resp.error ?: "Confirm receipt failed", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    Toast.makeText(this@RetailerConfirmReceipt, "Receipt confirmed", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@RetailerConfirmReceipt, RetailerProductStatusActivity::class.java).apply {
                        putExtra("batch_id", batchId)
                        if (currentBatchCode.isNotBlank()) {
                            putExtra("batch_code", currentBatchCode)
                        }
                        resp.qr_payload?.let { payload ->
                            if (payload.isNotBlank()) putExtra("qr_payload", payload)
                        }
                    }
                    startActivity(intent)
                    finish()
                } catch (_: Exception) {
                    Toast.makeText(this@RetailerConfirmReceipt, "Confirm receipt failed", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Cancel
        binding.btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun bindBatchHeader() {
        binding.tvBatch.text = when {
            currentBatchCode.isNotBlank() -> currentBatchCode
            currentBatchId > 0 -> "Batch #$currentBatchId"
            else -> "--"
        }
    }

    private fun loadBatchHeaderIfMissing() {
        lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.retailerApi.getBatchDetails(
                        batchId = currentBatchId.takeIf { it > 0 }?.toLong(),
                        batchCode = currentBatchCode.ifBlank { null }
                    )
                }
                val batch = resp.batch
                val resolvedId = batch?.id?.toInt() ?: -1
                if (resolvedId > 0 && currentBatchId <= 0) {
                    currentBatchId = resolvedId
                }

                val code = batch?.batch_code?.trim().orEmpty()
                if (code.isNotBlank()) {
                    currentBatchCode = code
                    bindBatchHeader()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun readBatchIdFromIntent(): Int {
        val direct = intent.getIntExtra("batch_id", -1)
        if (direct > 0) return direct
        val extras = intent.extras ?: return -1
        val raw = extras.get("batch_id") ?: extras.get("batch_id_str") ?: extras.get("BATCH_ID")
        return when (raw) {
            is Int -> raw
            is Long -> raw.toInt()
            is String -> raw.toIntOrNull() ?: -1
            else -> -1
        }
    }

    private fun readBatchCodeFromIntent(): String {
        return intent.getStringExtra("batch_code").orEmpty()
            .ifBlank { intent.getStringExtra("BATCH_CODE").orEmpty() }
            .trim()
    }
}
