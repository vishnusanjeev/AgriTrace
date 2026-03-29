package com.simats.agritrace.customer

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.simats.agritrace.ApiClient
import com.simats.agritrace.ConsumerJourneyItemDto
import com.simats.agritrace.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CustomerFullJourneyActivity : AppCompatActivity() {

    private var batchId: Long = 0L
    private var batchCode: String? = null
    private var shareTitle: String = "Full Journey"
    private var shareItems: List<ConsumerJourneyItemDto> = emptyList()
    private lateinit var scroll: NestedScrollView
    private lateinit var tvEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_customer_full_journey)

        batchId = intent.getLongExtra("batch_id", 0L)
        batchCode = intent.getStringExtra("batch_code")

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.btnShare).setOnClickListener { shareJourney() }
        scroll = findViewById(R.id.scroll)
        tvEmpty = findViewById(R.id.tvEmpty)

        loadJourney()
    }

    private fun loadJourney() {
        if (batchId <= 0 && batchCode.isNullOrBlank()) {
            showEmpty("Batch not found")
            return
        }

        lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.consumerApi.fullJourney(
                        batchId.takeIf { it > 0 },
                        batchCode
                    )
                }
                if (!resp.ok || resp.items == null) {
                    showEmpty(resp.error ?: "Unable to load full journey")
                    return@launch
                }

                val batch = resp.batch
                findViewById<TextView>(R.id.tvProduct).text = batch?.crop_name ?: "Product"
                findViewById<TextView>(R.id.tvBatch).text = "Batch #${batch?.batch_code ?: ""}"

                shareTitle = "${batch?.crop_name ?: "Product"} - ${batch?.batch_code ?: ""}"
                shareItems = resp.items ?: emptyList()

                showContent()
                bindItems(resp.items)
            } catch (_: Exception) {
                showEmpty("Unable to load full journey")
            }
        }
    }

    private fun bindItems(items: List<ConsumerJourneyItemDto>?) {
        val slots = listOf(
            ItemSlot(
                findViewById(R.id.icon1),
                findViewById(R.id.time1),
                findViewById(R.id.title1),
                findViewById(R.id.desc1),
                findViewById(R.id.chips1)
            ),
            ItemSlot(
                findViewById(R.id.icon2),
                findViewById(R.id.time2),
                findViewById(R.id.title2),
                findViewById(R.id.desc2),
                findViewById(R.id.chips2)
            ),
            ItemSlot(
                findViewById(R.id.icon3),
                findViewById(R.id.time3),
                findViewById(R.id.title3),
                findViewById(R.id.desc3),
                findViewById(R.id.chips3)
            ),
            ItemSlot(
                findViewById(R.id.icon4),
                findViewById(R.id.time4),
                findViewById(R.id.title4),
                findViewById(R.id.desc4),
                findViewById(R.id.chips4)
            )
        )

        val data = items ?: emptyList()
        if (data.isEmpty()) {
            showEmpty("No journey updates yet")
        }
        for (i in slots.indices) {
            val slot = slots[i]
            val item = data.getOrNull(i)
            if (item == null) {
                slot.setVisible(false)
                continue
            }
            slot.setVisible(true)
            slot.time.text = item.time ?: ""
            slot.title.text = item.title ?: ""
            slot.desc.text = item.description ?: ""
            slot.bindTags(item.tag1, item.tag2)
        }
    }

    private fun shareJourney() {
        val shareText = buildString {
            append("Full Journey\n")
            append(shareTitle).append('\n').append('\n')
            shareItems.take(6).forEach { item ->
                append("• ").append(item.title ?: "Event")
                if (!item.time.isNullOrBlank()) {
                    append(" (").append(item.time).append(")")
                }
                append('\n')
                if (!item.description.isNullOrBlank()) {
                    append("  ").append(item.description).append('\n')
                }
                val tags = listOfNotNull(item.tag1, item.tag2).filter { it.isNotBlank() }
                if (tags.isNotEmpty()) {
                    append("  ").append(tags.joinToString(" • ")).append('\n')
                }
                append('\n')
            }
        }

        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(i, "Share journey"))
    }

    private fun showEmpty(message: String) {
        tvEmpty.text = message
        tvEmpty.visibility = android.view.View.VISIBLE
        scroll.visibility = android.view.View.GONE
    }

    private fun showContent() {
        tvEmpty.visibility = android.view.View.GONE
        scroll.visibility = android.view.View.VISIBLE
    }

    private data class ItemSlot(
        val icon: ImageView,
        val time: TextView,
        val title: TextView,
        val desc: TextView,
        val chips: LinearLayout
    ) {
        fun setVisible(visible: Boolean) {
            val v = if (visible) android.view.View.VISIBLE else android.view.View.GONE
            icon.visibility = v
            time.visibility = v
            title.visibility = v
            desc.visibility = v
            chips.visibility = v
        }

        fun bindTags(tag1: String?, tag2: String?) {
            val first = chips.getChildAt(0) as? TextView
            val second = chips.getChildAt(1) as? TextView
            if (tag1.isNullOrBlank()) {
                first?.visibility = TextView.GONE
            } else {
                first?.visibility = TextView.VISIBLE
                first?.text = tag1
            }
            if (tag2.isNullOrBlank()) {
                second?.visibility = TextView.GONE
            } else {
                second?.visibility = TextView.VISIBLE
                second?.text = tag2
            }
        }
    }
}
