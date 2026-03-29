package com.simats.agritrace.dist

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.simats.agritrace.ApiClient
import com.simats.agritrace.R

import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DistributorUpdateTransportActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BATCH_ID = "extra_batch_id"

        const val RESULT_DATE_TIME = "result_date_time"
        const val RESULT_VEHICLE_ID = "result_vehicle_id"
        const val RESULT_LOCATION = "result_location"
        const val RESULT_TEMPERATURE = "result_temperature"
        const val RESULT_STORAGE = "result_storage"
    }

    private lateinit var btnBack: ImageView
    private lateinit var tvCardSub: TextView

    private lateinit var etDateTime: EditText
    private lateinit var etVehicleId: EditText
    private lateinit var etLocation: EditText
    private lateinit var etTemp: EditText
    private lateinit var etStorage: EditText
    private lateinit var btnSubmit: MaterialButton

    private val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_distributor_update_transport)

        btnBack = findViewById(R.id.btnBack)
        tvCardSub = findViewById(R.id.tvCardSub)

        etDateTime = findViewById(R.id.etDateTime)
        etVehicleId = findViewById(R.id.etVehicleId)
        etLocation = findViewById(R.id.etLocation)
        etTemp = findViewById(R.id.etTemp)
        etStorage = findViewById(R.id.etStorage)
        btnSubmit = findViewById(R.id.btnSubmit)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootTransport)) { v, insets ->
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
        val batchId = intent.getStringExtra(EXTRA_BATCH_ID)?.trim().orEmpty()
        val shown = if (batchId.isNotEmpty()) batchId else "B-2023-001"
        tvCardSub.text = "Updating status for Batch #$shown"

        etDateTime.setText(isoFmt.format(Date()))
        etDateTime.setOnClickListener { pickDateTime() }

        btnBack.setOnClickListener { finish() }

        btnSubmit.setOnClickListener { submit(batchId) }
    }

    private fun pickDateTime() {
        val cal = Calendar.getInstance()
        val current = etDateTime.text?.toString()?.trim().orEmpty()
        runCatching { isoFmt.parse(current) }.getOrNull()?.let { cal.time = it }

        DatePickerDialog(
            this,
            { _, year, month, day ->
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, month)
                cal.set(Calendar.DAY_OF_MONTH, day)

                TimePickerDialog(
                    this,
                    { _, hour, minute ->
                        cal.set(Calendar.HOUR_OF_DAY, hour)
                        cal.set(Calendar.MINUTE, minute)
                        cal.set(Calendar.SECOND, 0)
                        etDateTime.setText(isoFmt.format(cal.time))
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    true
                ).show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun submit(batchIdRaw: String) {
        val dateTime = etDateTime.text?.toString()?.trim().orEmpty()
        val vehicleId = etVehicleId.text?.toString()?.trim().orEmpty()
        val location = etLocation.text?.toString()?.trim().orEmpty()
        val tempStr = etTemp.text?.toString()?.trim().orEmpty()
        val storage = etStorage.text?.toString()?.trim().orEmpty()

        if (vehicleId.isEmpty()) {
            toast("Please enter Vehicle ID")
            return
        }
        if (location.isEmpty()) {
            toast("Please enter Current Location")
            return
        }

        val tempVal: Double? = if (tempStr.isEmpty()) null else tempStr.toDoubleOrNull()
        if (tempStr.isNotEmpty() && tempVal == null) {
            toast("Temperature must be a number")
            return
        }

        val batchId = batchIdRaw.toLongOrNull()
        if (batchId == null || batchId <= 0) {
            toast("Batch ID missing")
            return
        }

        btnSubmit.isEnabled = false
        lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.distributorApi.updateTransport(
                        com.simats.agritrace.UpdateTransportReq(
                            batch_id = batchId,
                            event_time = dateTime,
                            vehicle_id = vehicleId,
                            location_text = location,
                            temperature_c = tempVal,
                            storage_conditions = storage.ifBlank { null }
                        )
                    )
                }
                if (resp.ok) {
                    toast("Transport updated")
                    finish()
                } else {
                    toast(resp.error ?: "Update failed")
                    btnSubmit.isEnabled = true
                }
            } catch (_: Exception) {
                toast("Update failed")
                btnSubmit.isEnabled = true
            }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
