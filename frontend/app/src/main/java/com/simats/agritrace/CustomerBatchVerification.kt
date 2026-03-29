package com.simats.agritrace

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CustomerBatchVerification : AppCompatActivity() {

    private val gson = Gson()
    private var batchId: Long = 0L
    private var batchCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_customer_verification)

        window.statusBarColor = android.graphics.Color.WHITE
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
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

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        batchId = intent.getLongExtra("batch_id", 0L)
        batchCode = intent.getStringExtra("batch_code")

        val verifyJson = intent.getStringExtra("verify_json")
        if (!verifyJson.isNullOrBlank()) {
            val data = gson.fromJson(verifyJson, ConsumerVerifyDataDto::class.java)
            bindData(data)
        } else {
            loadDetails()
        }

        findViewById<TextView>(R.id.tvViewTimeline).setOnClickListener {
            val intent = Intent(this, com.simats.agritrace.customer.CustomerFullJourneyActivity::class.java).apply {
                if (batchId > 0) putExtra("batch_id", batchId)
                if (!batchCode.isNullOrBlank()) putExtra("batch_code", batchCode)
            }
            startActivity(intent)
        }
    }

    private fun loadDetails() {
        if (batchId <= 0 && batchCode.isNullOrBlank()) return
        lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.consumerApi.batchDetails(
                        batchId.takeIf { it > 0 },
                        batchCode
                    )
                }
                if (!resp.ok || resp.data == null) {
                    toast(resp.error ?: "Failed to load details")
                    return@launch
                }
                bindData(resp.data)
            } catch (_: Exception) {
                toast("Failed to load details")
            }
        }
    }

    private fun bindData(data: ConsumerVerifyDataDto) {
        batchId = data.batch_id
        batchCode = data.batch_code

        findViewById<TextView>(R.id.tvProductName).text = data.crop_name ?: "Unknown"
        findViewById<TextView>(R.id.tvBatch).text = "Batch #${data.batch_code ?: ""}"
        findViewById<TextView>(R.id.tvTagCategory).text = data.category ?: "Produce"

        val method = if (data.is_organic == 1) "Organic" else "Conventional"
        findViewById<TextView>(R.id.tvTagMethod).text = method
        findViewById<TextView>(R.id.tvMethodValue).text = method

        findViewById<TextView>(R.id.tvFarmName).text = data.farmer_name ?: "Farmer"
        findViewById<TextView>(R.id.tvLocation).text = data.farmer_location ?: "Location not available"
        findViewById<TextView>(R.id.tvHarvestValue).text = data.harvest_date ?: "—"

        val statusText = if (data.verified) "Verified Authentic" else "Data Tampered"
        val statusColor = if (data.verified) R.color.greenDark else R.color.orangeDark
        findViewById<TextView>(R.id.tvBlockchainStatus).apply {
            text = statusText
            setTextColor(ContextCompat.getColor(this@CustomerBatchVerification, statusColor))
        }
        findViewById<ImageView>(R.id.ivBlockchainIcon)
            .setColorFilter(ContextCompat.getColor(this, statusColor))

        val summary = data.journey_summary ?: emptyList()
        bindJourney(summary)
    }

    private fun bindJourney(summary: List<ConsumerJourneySummaryDto>) {
        val rows = listOf(
            findViewById<LinearLayout>(R.id.journeyRow1),
            findViewById<LinearLayout>(R.id.journeyRow2),
            findViewById<LinearLayout>(R.id.journeyRow3)
        )

        for (i in 0..2) {
            val item = summary.getOrNull(i)
            val row = rows[i]
            if (item == null) {
                row.visibility = LinearLayout.GONE
                continue
            }
            row.visibility = LinearLayout.VISIBLE
            val subText = listOfNotNull(item.subtitle, item.time).joinToString(" • ")
            val textColumn = row.getChildAt(1) as? LinearLayout
            val titleView = textColumn?.getChildAt(0) as? TextView
            val subView = textColumn?.getChildAt(1) as? TextView
            titleView?.text = item.title ?: ""
            subView?.text = subText
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
