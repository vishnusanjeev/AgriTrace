package com.simats.agritrace.Farmer

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.simats.agritrace.databinding.ActivityUpdateLocationBinding

class UpdateLocationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUpdateLocationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        binding = ActivityUpdateLocationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.btnCancel.setOnClickListener {
            finish()
        }

        binding.layoutAutoDetect.setOnClickListener {
            // TODO: Auto-detect GPS logic
        }

        binding.btnSubmit.setOnClickListener {
            val location = binding.etLocation.text.toString()
            val remarks = binding.etRemarks.text.toString()

            // TODO: Submit API call
        }
    }
}
