package com.simats.agritrace

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class dist_details : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_dist_details)

        // ✅ IMPORTANT: main ID must exist in XML
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

        // ================= BUTTON REFERENCES =================
        val btnConfirmPickup = findViewById<Button>(R.id.confirmPickupButton)
        val btnTransferRetailer = findViewById<Button>(R.id.transferButton)

        // ================= CONFIRM PICKUP =================
        btnConfirmPickup.setOnClickListener {
            // Go to Update Transfer / Success screen
            val intent = Intent(this, update_transport::class.java)
            startActivity(intent)
        }

        // ================= TRANSFER TO RETAILER =================
        btnTransferRetailer.setOnClickListener {
            // Go to Select Retailer screen
            val intent = Intent(this, SelectRetailerActivity::class.java)
            startActivity(intent)
        }
    }
}
