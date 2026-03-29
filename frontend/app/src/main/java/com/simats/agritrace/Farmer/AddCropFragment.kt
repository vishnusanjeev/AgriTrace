package com.simats.agritrace.Farmer

import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewFlipper
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.simats.agritrace.ApiClient
import com.simats.agritrace.CreateCropBatchReq
import com.simats.agritrace.QrGenerateReq
import com.simats.agritrace.R
import com.simats.agritrace.Session
import com.simats.agritrace.qr_code
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddCropFragment : Fragment() {

    companion object {
        private val CATEGORY_OPTIONS = listOf(
            "Vegetables", "Fruits", "Grains", "Pulses", "Spices", "Oilseeds", "Flowers", "Other"
        )
    }

    private val displayDateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.US)
    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private lateinit var topBar: View
    private lateinit var ivBack: ImageView
    private lateinit var tvTopTitle: TextView
    private lateinit var topDivider: View
    private lateinit var stepperBar: View

    private lateinit var step1CircleWrap: View
    private lateinit var step2CircleWrap: View
    private lateinit var step3CircleWrap: View
    private lateinit var tvStep1Num: TextView
    private lateinit var tvStep2Num: TextView
    private lateinit var tvStep3Num: TextView
    private lateinit var ivStep1Check: ImageView
    private lateinit var ivStep2Check: ImageView
    private lateinit var ivStep3Check: ImageView
    private lateinit var tvStep1Label: TextView
    private lateinit var tvStep2Label: TextView
    private lateinit var tvStep3Label: TextView
    private lateinit var line12: View
    private lateinit var line23: View

    private lateinit var flipper: ViewFlipper

    private lateinit var etCropName: AppCompatEditText
    private lateinit var actCategory: AutoCompleteTextView
    private lateinit var ivCategoryDrop: ImageView
    private lateinit var etQuantity: AppCompatEditText
    private lateinit var tvHarvestDate: TextView
    private lateinit var ivCalendar: ImageView
    private lateinit var btnNextStep: MaterialButton

    private lateinit var stubStep2: ViewStub
    private var step2Inflated = false

    private lateinit var etSeedVariety: AppCompatEditText
    private lateinit var etFertilizers: AppCompatEditText
    private lateinit var optDrip: MaterialCardView
    private lateinit var optSprinkler: MaterialCardView
    private lateinit var optFlood: MaterialCardView
    private lateinit var optRainfed: MaterialCardView
    private lateinit var tvOptDrip: TextView
    private lateinit var tvOptSprinkler: TextView
    private lateinit var tvOptFlood: TextView
    private lateinit var tvOptRainfed: TextView
    private lateinit var swOrganic: SwitchMaterial
    private lateinit var btnReviewCreate: MaterialButton

    private lateinit var stubStep3: ViewStub
    private var step3Inflated = false

    private lateinit var chipConfirmed: TextView
    private lateinit var tvTxHash: TextView
    private lateinit var tvBlock: TextView
    private lateinit var tvWhen: TextView
    private lateinit var ivCopy: ImageView
    private lateinit var btnGenerateQr: MaterialButton
    private lateinit var tvReturnDashboard: TextView

    private val cal = Calendar.getInstance()
    private var selectedHarvestIso: String? = null
    private var selectedIrrigation: String? = null
    private var isOrganic = false

    private var createdBatchId: Long? = null
    private var createdBatchCode: String? = null
    private var chainTxHash: String? = null
    private var chainBlockNo: Long? = null
    private var chainStatus: String? = null

    private var step = 1
    private var createJob: Job? = null
    private var resetOnResume = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.fragment_add_crop, container, false)
        bindViews(v)

        flipper.inAnimation = null
        flipper.outAnimation = null
        flipper.displayedChild = 0

        topBar.isVisible = true
        topDivider.isVisible = true
        stepperBar.isVisible = true
        tvTopTitle.text = "Register New Crop"
        ivBack.visibility = View.GONE

        updateStepperUi(1)

        v.post {
            if (!isAdded) return@post
            setupTopBar()
            setupCategoryDropdown()
            setupHarvestPicker()
            setupStep1Actions()
        }

        return v
    }

    override fun onResume() {
        super.onResume()
        if (resetOnResume && isAdded) {
            resetOnResume = false
            view?.post { if (isAdded) resetForm() }
        }
    }

    override fun onStop() {
        super.onStop()
        createJob?.cancel()
        createJob = null
    }

    override fun onDestroyView() {
        createJob?.cancel()
        createJob = null
        super.onDestroyView()
    }

    private fun bindViews(v: View) {
        topBar = v.findViewById(R.id.topBar)
        ivBack = v.findViewById(R.id.ivBack)
        tvTopTitle = v.findViewById(R.id.tvTopTitle)
        topDivider = v.findViewById(R.id.topDivider)
        stepperBar = v.findViewById(R.id.stepperBar)

        step1CircleWrap = v.findViewById(R.id.step1CircleWrap)
        step2CircleWrap = v.findViewById(R.id.step2CircleWrap)
        step3CircleWrap = v.findViewById(R.id.step3CircleWrap)
        tvStep1Num = v.findViewById(R.id.tvStep1Num)
        tvStep2Num = v.findViewById(R.id.tvStep2Num)
        tvStep3Num = v.findViewById(R.id.tvStep3Num)
        ivStep1Check = v.findViewById(R.id.ivStep1Check)
        ivStep2Check = v.findViewById(R.id.ivStep2Check)
        ivStep3Check = v.findViewById(R.id.ivStep3Check)
        tvStep1Label = v.findViewById(R.id.tvStep1Label)
        tvStep2Label = v.findViewById(R.id.tvStep2Label)
        tvStep3Label = v.findViewById(R.id.tvStep3Label)
        line12 = v.findViewById(R.id.line12)
        line23 = v.findViewById(R.id.line23)

        flipper = v.findViewById(R.id.flipperSteps)

        etCropName = v.findViewById(R.id.etCropName)
        actCategory = v.findViewById(R.id.actCategory)
        ivCategoryDrop = v.findViewById(R.id.ivCategoryDrop)
        etQuantity = v.findViewById(R.id.etQuantity)
        tvHarvestDate = v.findViewById(R.id.tvHarvestDate)
        ivCalendar = v.findViewById(R.id.ivCalendar)
        btnNextStep = v.findViewById(R.id.btnNextStep)

        stubStep2 = v.findViewById(R.id.stubStep2)
        stubStep3 = v.findViewById(R.id.stubStep3)
    }

    private fun setupTopBar() {
        ivBack.setOnClickListener {
            if (step == 2) goToStep(1, animate = true)
            else requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupCategoryDropdown() {
        actCategory.keyListener = null
        actCategory.isFocusable = false
        actCategory.isFocusableInTouchMode = false

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, CATEGORY_OPTIONS)
        actCategory.setAdapter(adapter)

        if (actCategory.text.isNullOrBlank()) actCategory.setText("Vegetables", false)

        actCategory.setOnClickListener { actCategory.showDropDown() }
        ivCategoryDrop.setOnClickListener { actCategory.showDropDown() }
    }

    private fun setupHarvestPicker() {
        val open = {
            val now = Calendar.getInstance()
            DatePickerDialog(
                requireContext(),
                { _, y, m, d ->
                    cal.set(Calendar.YEAR, y)
                    cal.set(Calendar.MONTH, m)
                    cal.set(Calendar.DAY_OF_MONTH, d)
                    tvHarvestDate.text = displayDateFormat.format(cal.time)
                    selectedHarvestIso = isoDateFormat.format(cal.time)
                },
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
        tvHarvestDate.setOnClickListener { open() }
        ivCalendar.setOnClickListener { open() }
        if (tvHarvestDate.text.isNullOrBlank()) tvHarvestDate.text = "mm/dd/yyyy"
    }

    private fun setupStep1Actions() {
        btnNextStep.setOnClickListener {
            if (!validateStep1()) return@setOnClickListener
            goToStep(2, animate = true)
        }
    }

    private fun ensureStep2Inflated() {
        if (step2Inflated) return
        val root = stubStep2.inflate()
        step2Inflated = true

        etSeedVariety = root.findViewById(R.id.etSeedVariety)
        etFertilizers = root.findViewById(R.id.etFertilizers)
        optDrip = root.findViewById(R.id.optDrip)
        optSprinkler = root.findViewById(R.id.optSprinkler)
        optFlood = root.findViewById(R.id.optFlood)
        optRainfed = root.findViewById(R.id.optRainfed)
        tvOptDrip = root.findViewById(R.id.tvOptDrip)
        tvOptSprinkler = root.findViewById(R.id.tvOptSprinkler)
        tvOptFlood = root.findViewById(R.id.tvOptFlood)
        tvOptRainfed = root.findViewById(R.id.tvOptRainfed)
        swOrganic = root.findViewById(R.id.swOrganic)
        btnReviewCreate = root.findViewById(R.id.btnReviewCreate)

        setupIrrigationGrid()

        btnReviewCreate.setOnClickListener {
            if (!validateStep2()) return@setOnClickListener
            createCropAndBatch()
        }
    }

    private fun setupIrrigationGrid() {
        fun applyOrganicUi(checked: Boolean) {
            val track = if (checked) 0xFFBFE9D6.toInt() else 0xFFE5E7EB.toInt()
            val thumb = if (checked) 0xFF0B8B5B.toInt() else 0xFF9CA3AF.toInt()
            swOrganic.trackTintList = android.content.res.ColorStateList.valueOf(track)
            swOrganic.thumbTintList = android.content.res.ColorStateList.valueOf(thumb)
        }

        swOrganic.setOnCheckedChangeListener { _, checked ->
            isOrganic = checked
            applyOrganicUi(checked)
        }
        applyOrganicUi(swOrganic.isChecked)

        fun setSelected(key: String) {
            selectedIrrigation = key
            applyIrrigationUi()
        }

        optDrip.setOnClickListener { setSelected("DRIP") }
        optSprinkler.setOnClickListener { setSelected("SPRINKLER") }
        optFlood.setOnClickListener { setSelected("FLOOD") }
        optRainfed.setOnClickListener { setSelected("RAINFED") }

        applyIrrigationUi()
    }

    private fun applyIrrigationUi() {
        if (!step2Inflated) return

        val strokeNormal = 0xFFD7DEE7.toInt()
        val bgNormal = 0xFFFFFFFF.toInt()
        val textNormal = 0xFF334155.toInt()

        val strokeSelected = 0xFF0B8B5B.toInt()
        val bgSelected = 0xFFE8FBF2.toInt()
        val textSelected = 0xFF0B8B5B.toInt()

        fun style(card: MaterialCardView, tv: TextView, selected: Boolean) {
            card.strokeColor = if (selected) strokeSelected else strokeNormal
            card.setCardBackgroundColor(if (selected) bgSelected else bgNormal)
            tv.setTextColor(if (selected) textSelected else textNormal)
        }

        style(optDrip, tvOptDrip, selectedIrrigation == "DRIP")
        style(optSprinkler, tvOptSprinkler, selectedIrrigation == "SPRINKLER")
        style(optFlood, tvOptFlood, selectedIrrigation == "FLOOD")
        style(optRainfed, tvOptRainfed, selectedIrrigation == "RAINFED")
    }

    private fun validateStep1(): Boolean {
        val cropName = etCropName.text?.toString()?.trim().orEmpty()
        val category = actCategory.text?.toString()?.trim().orEmpty()
        val qtyStr = etQuantity.text?.toString()?.trim().orEmpty()
        val harvestIso = selectedHarvestIso

        if (cropName.isEmpty()) {
            etCropName.error = "Required"
            return false
        }
        if (category.isEmpty()) {
            Toast.makeText(requireContext(), "Select category", Toast.LENGTH_SHORT).show()
            return false
        }
        val qty = qtyStr.toDoubleOrNull()
        if (qty == null || qty <= 0.0) {
            etQuantity.error = "Enter valid quantity"
            return false
        }
        if (harvestIso == null) {
            Toast.makeText(requireContext(), "Select harvest date", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun validateStep2(): Boolean {
        return true
    }

    private fun createCropAndBatch() {
        val ctx = requireContext()
        if (!Session.isLoggedIn(ctx)) {
            Toast.makeText(ctx, "Session expired. Please login again.", Toast.LENGTH_LONG).show()
            return
        }
        if (!Session.role(ctx).equals("FARMER", true)) {
            Toast.makeText(ctx, "Only farmers can create batches.", Toast.LENGTH_LONG).show()
            return
        }

        val cropName = etCropName.text?.toString()?.trim().orEmpty()
        val category = actCategory.text?.toString()?.trim().orEmpty()
        val qtyKg = etQuantity.text?.toString()?.trim()?.toDoubleOrNull() ?: 0.0
        val harvestDateIso = selectedHarvestIso ?: return

        val seedVariety = if (step2Inflated) etSeedVariety.text?.toString()?.trim()?.ifEmpty { null } else null
        val fertilizersUsed = if (step2Inflated) etFertilizers.text?.toString()?.trim()?.ifEmpty { null } else null
        val irrigationMethod = selectedIrrigation
        val organic = if (isOrganic) 1 else 0

        val req = CreateCropBatchReq(
            crop_name = cropName,
            category = category,
            quantity_kg = qtyKg,
            harvest_date = harvestDateIso,
            seed_variety = seedVariety,
            fertilizers_used = fertilizersUsed,
            irrigation_method = irrigationMethod,
            is_organic = organic
        )

        createJob?.cancel()
        setLoading(true)

        createJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.farmerApi.createCropBatch(req)
                }
                if (!isAdded || view == null) return@launch

                val hasBatch = (resp.batch?.id ?: 0L) > 0L
                if (resp.ok || hasBatch) {
                    ensureStep3Inflated()

                    createdBatchId = resp.batch?.id
                    createdBatchCode = resp.batch?.batch_code
                    chainTxHash = resp.blockchain?.tx_hash
                    chainBlockNo = resp.blockchain?.block_number
                    chainStatus = resp.blockchain?.status

                    val statusText = when ((chainStatus ?: "").uppercase(Locale.US)) {
                        "CONFIRMED" -> "confirmed"
                        "FAILED" -> "pending"
                        else -> "submitted"
                    }
                    chipConfirmed.text = statusText

                    tvTxHash.text = if (!chainTxHash.isNullOrBlank()) chainTxHash else "-"
                    tvBlock.text = if (chainBlockNo != null) "Block # $chainBlockNo" else "Block pending"
                    tvWhen.text = "Just now"

                    if (!resp.ok) {
                        Toast.makeText(requireContext(), resp.error ?: "Created with pending blockchain status", Toast.LENGTH_LONG).show()
                    }

                    goToStep(3, animate = true)
                } else {
                    Toast.makeText(requireContext(), resp.error ?: "Failed to create batch", Toast.LENGTH_LONG).show()
                }
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                if (!isAdded || view == null) return@launch
                val msg = withContext(Dispatchers.IO) { extractNetworkMessage(e) }
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            } finally {
                if (isAdded && view != null) setLoading(false)
            }
        }
    }

    private fun goToStep(target: Int, animate: Boolean) {
        step = target

        if (!animate) {
            flipper.inAnimation = null
            flipper.outAnimation = null
        }

        if (target == 2) ensureStep2Inflated()
        if (target == 3) ensureStep3Inflated()

        flipper.displayedChild = when (target) {
            1 -> 0
            2 -> 1
            else -> 2
        }

        val showTop = target != 3
        topBar.isVisible = showTop
        topDivider.isVisible = showTop
        stepperBar.isVisible = showTop

        tvTopTitle.text = when (target) {
            1 -> "Register New Crop"
            2 -> "Farming Details"
            else -> ""
        }

        if (target == 1) {
            ivBack.visibility = View.GONE
        } else {
            ivBack.visibility = View.VISIBLE
        }

        updateStepperUi(target)
    }

    private fun updateStepperUi(step: Int) {
        val green = 0xFF0B8B5B.toInt()
        val grayText = 0xFF64748B.toInt()
        val lineGray = 0xFFCBD5E1.toInt()

        fun setActiveCircle(wrap: View) { wrap.setBackgroundResource(R.drawable.bg_step_circle_active) }
        fun setInactiveCircle(wrap: View) { wrap.setBackgroundResource(R.drawable.bg_step_circle_inactive) }

        fun showNum(tv: TextView, iv: ImageView, num: String, active: Boolean) {
            iv.visibility = View.GONE
            tv.visibility = View.VISIBLE
            tv.text = num
            tv.setTextColor(if (active) 0xFFFFFFFF.toInt() else 0xFF4B5563.toInt())
        }

        fun showCheck(tv: TextView, iv: ImageView) {
            tv.visibility = View.GONE
            iv.visibility = View.VISIBLE
        }

        when (step) {
            1 -> {
                setActiveCircle(step1CircleWrap)
                setInactiveCircle(step2CircleWrap)
                setInactiveCircle(step3CircleWrap)

                showNum(tvStep1Num, ivStep1Check, "1", true)
                showNum(tvStep2Num, ivStep2Check, "2", false)
                showNum(tvStep3Num, ivStep3Check, "3", false)

                tvStep1Label.setTextColor(green)
                tvStep2Label.setTextColor(grayText)
                tvStep3Label.setTextColor(grayText)

                line12.setBackgroundColor(lineGray)
                line23.setBackgroundColor(lineGray)
            }
            2 -> {
                setActiveCircle(step1CircleWrap)
                setActiveCircle(step2CircleWrap)
                setInactiveCircle(step3CircleWrap)

                showCheck(tvStep1Num, ivStep1Check)
                showNum(tvStep2Num, ivStep2Check, "2", true)
                showNum(tvStep3Num, ivStep3Check, "3", false)

                tvStep1Label.setTextColor(green)
                tvStep2Label.setTextColor(green)
                tvStep3Label.setTextColor(grayText)

                line12.setBackgroundColor(green)
                line23.setBackgroundColor(lineGray)
            }
            else -> {
                setActiveCircle(step1CircleWrap)
                setActiveCircle(step2CircleWrap)
                setActiveCircle(step3CircleWrap)

                showCheck(tvStep1Num, ivStep1Check)
                showCheck(tvStep2Num, ivStep2Check)
                showNum(tvStep3Num, ivStep3Check, "3", true)

                tvStep1Label.setTextColor(green)
                tvStep2Label.setTextColor(green)
                tvStep3Label.setTextColor(green)

                line12.setBackgroundColor(green)
                line23.setBackgroundColor(green)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        if (!step2Inflated) return
        btnReviewCreate.isEnabled = !loading
        btnReviewCreate.text = if (loading) "Creating..." else "Review & Create Batch"
    }

    private fun bindStep3Views(root: View) {
        chipConfirmed = root.findViewById(R.id.chipConfirmed)
        tvTxHash = root.findViewById(R.id.tvTxHash)
        tvBlock = root.findViewById(R.id.tvBlock)
        tvWhen = root.findViewById(R.id.tvWhen)
        ivCopy = root.findViewById(R.id.ivCopy)
        btnGenerateQr = root.findViewById(R.id.btnGenerateQr)
        tvReturnDashboard = root.findViewById(R.id.tvReturnDashboard)
    }

    private fun openOrCreateQrAndLaunch() {
        val bid = createdBatchId ?: run {
            Toast.makeText(requireContext(), "Batch not available yet.", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val payload = withContext(Dispatchers.IO) {
                    // 1) Try GET existing QR first
                    val getResp = ApiClient.qrApi.getQr(bid.toInt())
                    if (getResp.ok && getResp.qr?.qr_payload?.isNotBlank() == true) {
                        return@withContext getResp.qr!!.qr_payload!!.trim()
                    }

                    // 2) If not exists, generate ONCE from backend
                    val genResp = ApiClient.qrApi.generateQr(QrGenerateReq(batch_id = bid.toInt()))
                    if (genResp.ok && genResp.qr?.qr_payload?.isNotBlank() == true) {
                        return@withContext genResp.qr!!.qr_payload!!.trim()
                    }

                    ""
                }

                if (!isAdded) return@launch

                if (payload.isBlank()) {
                    Toast.makeText(requireContext(), "QR not available yet", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val i = Intent(requireContext(), qr_code::class.java).apply {
                    putExtra("batch_id", bid.toInt())
                    putExtra("batch_code", createdBatchCode ?: "")
                    putExtra("qr_payload", payload) // ✅ THE ONLY SOURCE OF TRUTH
                }
                startActivity(i)

            } catch (_: Exception) {
                if (!isAdded) return@launch
                Toast.makeText(requireContext(), "Unable to load QR. Try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupStep3Actions() {
        ivCopy.setOnClickListener {
            val tx = tvTxHash.text?.toString()?.trim().orEmpty()
            if (tx.isEmpty() || tx == "-") return@setOnClickListener
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("tx_hash", tx))
            Toast.makeText(requireContext(), "Copied", Toast.LENGTH_SHORT).show()
        }

        btnGenerateQr.setOnClickListener {
            openOrCreateQrAndLaunch() // ✅ changed: no local QR generation anymore
        }

        tvReturnDashboard.setOnClickListener {
            resetOnResume = true
            parentFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
            val bottom = activity?.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.farmerBottomNav)
            if (bottom != null) {
                bottom.selectedItemId = R.id.navigation_home
            } else {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
    }


    private fun ensureStep3Inflated() {
        if (step3Inflated) return
        val root = stubStep3.inflate()
        step3Inflated = true
        bindStep3Views(root)
        setupStep3Actions()
    }

    private fun resetForm() {
        createJob?.cancel()
        createJob = null

        etCropName.setText("")
        actCategory.setText("Vegetables", false)
        etQuantity.setText("")
        selectedHarvestIso = null
        tvHarvestDate.text = "mm/dd/yyyy"

        if (step2Inflated) {
            etSeedVariety.setText("")
            etFertilizers.setText("")
            selectedIrrigation = null
            isOrganic = false
            swOrganic.isChecked = false
            applyIrrigationUi()
            setLoading(false)
        } else {
            selectedIrrigation = null
            isOrganic = false
        }

        createdBatchId = null
        createdBatchCode = null
        chainTxHash = null
        chainBlockNo = null
        chainStatus = null

        if (step3Inflated) {
            chipConfirmed.text = "submitted"
            tvTxHash.text = "-"
            tvBlock.text = "Block pending"
            tvWhen.text = ""
        }

        goToStep(1, animate = false)
    }

    private fun extractNetworkMessage(e: Exception): String {
        return when (e) {
            is HttpException -> {
                val code = e.code()
                val raw = try { e.response()?.errorBody()?.string().orEmpty() } catch (_: Exception) { "" }
                val serverMsg = try {
                    val obj = org.json.JSONObject(raw)
                    obj.optString("error").ifBlank { obj.optString("message") }
                } catch (_: Exception) { "" }
                when {
                    serverMsg.isNotBlank() -> serverMsg
                    raw.isNotBlank() -> "HTTP $code ${raw.take(160)}"
                    else -> "Request failed (HTTP $code)"
                }
            }
            is java.net.UnknownHostException -> "No internet / wrong server IP"
            is java.net.SocketTimeoutException -> "Server timeout. Try again."
            is javax.net.ssl.SSLHandshakeException -> "SSL error (https mismatch)"
            else -> (e.message ?: "Network/server error")
        }
    }
}
