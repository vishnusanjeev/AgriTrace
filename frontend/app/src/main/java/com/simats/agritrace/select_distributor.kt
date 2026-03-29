package com.simats.agritrace

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.simats.agritrace.model.Distributor
import com.simats.agritrace.model.BatchTransferRequest

import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class select_distributor : AppCompatActivity() {

    private val TAG = "SelectDistributor"
    private var selectedDistributor: Distributor? = null
    private var batchId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_select_distributor)

        batchId = intent.getIntExtra("batchId", -1)
        Log.d(TAG, "Received batchId = $batchId")

        if (batchId == -1) {
            Toast.makeText(this, "Batch ID missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadDistributors()

        // Updated to MaterialButton IDs
        findViewById<MaterialButton>(R.id.btnConfirmTransfer).setOnClickListener {
            confirmTransfer()
        }

        findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener {
            finish()
        }

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }

    private fun loadDistributors() {
        val container = findViewById<LinearLayout>(R.id.distributorListContainer)
        Log.d(TAG, "Fetching distributors...")


    }

    // Updated Highlight Logic for MaterialCardView
    private fun highlightSelection(container: LinearLayout, selectedPadding: MaterialCardView) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i) as MaterialCardView
            child.strokeWidth = 0 // Remove border from others
            child.findViewById<ImageView>(R.id.imgSelected).visibility = View.GONE
        }

        // Highlight the selected one
        selectedPadding.strokeWidth = 4
        selectedPadding.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#3CB371")))
        selectedPadding.findViewById<ImageView>(R.id.imgSelected).visibility = View.VISIBLE
    }

    private fun confirmTransfer() {
        val distributor = selectedDistributor
        if (distributor == null) {
            Toast.makeText(this, "Please select a distributor", Toast.LENGTH_SHORT).show()
            return
        }

        val request = BatchTransferRequest(
            batchId = batchId.toString(),
            distributorId = distributor.id
        )


    }
}