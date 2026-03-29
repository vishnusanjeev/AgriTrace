// OnboardingActivity.kt
package com.simats.agritrace

import android.content.Intent
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

class OnboardingActivity : AppCompatActivity() {

    private lateinit var vp: ViewPager2
    private lateinit var btnAction: AppCompatButton
    private lateinit var tvSkipMain: AppCompatTextView

    private lateinit var d1: View
    private lateinit var d2: View
    private lateinit var d3: View

    private val pageLayouts = intArrayOf(
        R.layout.activity_onboarding_1,
        R.layout.activity_onboarding_2,
        R.layout.activity_onboarding_3
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_onboarding)

        vp = findViewById(R.id.vpOnboarding)
        btnAction = findViewById(R.id.btnAction)
        tvSkipMain = findViewById(R.id.tvSkipMain)

        d1 = findViewById(R.id.mainDot1)
        d2 = findViewById(R.id.mainDot2)
        d3 = findViewById(R.id.mainDot3)

        vp.adapter = OnboardingAdapter(pageLayouts)
        vp.offscreenPageLimit = 2
        vp.overScrollMode = View.OVER_SCROLL_NEVER

        btnAction.setOnClickListener { handleAction() }
        tvSkipMain.setOnClickListener { finishOnboardingToLogin() }

        vp.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateCta(position)
                updateDots(position)
            }
        })

        updateCta(0)
        updateDots(0)
        tintButtonEndDrawable(btnAction, android.R.color.white)
    }

    private fun handleAction() {
        val pos = vp.currentItem
        if (pos < pageLayouts.size - 1) {
            vp.currentItem = pos + 1
        } else {
            // Get Started (last page)
            finishOnboardingToLogin()
        }
    }

    private fun updateCta(pos: Int) {
        btnAction.text = if (pos == pageLayouts.size - 1) "Get Started" else "Next"
    }

    private fun updateDots(pos: Int) {
        setDot(d1, pos == 0)
        setDot(d2, pos == 1)
        setDot(d3, pos == 2)
    }

    private fun setDot(v: View, selected: Boolean) {
        val lp = v.layoutParams
        lp.width = dp(if (selected) 44 else 10)
        lp.height = dp(10)
        v.layoutParams = lp

        v.setBackgroundResource(if (selected) R.drawable.indicator_selected else R.drawable.indicator_unselected)

        v.animate().cancel()
        v.scaleX = if (selected) 1f else 0.92f
        v.scaleY = if (selected) 1f else 0.92f
        v.animate().scaleX(1f).scaleY(1f).setDuration(180).start()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun finishOnboardingToLogin() {
        // Mark first launch complete so next app open goes to Login
        AppPrefs.markFirstLaunchDone(this)

        startActivity(Intent(this, LoginActivity::class.java))
        overridePendingTransition(0, 0)
        finish()
    }

    private fun tintButtonEndDrawable(button: AppCompatButton, colorRes: Int) {
        val color = ContextCompat.getColor(this, colorRes)
        val d = button.compoundDrawablesRelative[2]
        d?.mutate()?.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(
            button.compoundDrawablesRelative[0],
            button.compoundDrawablesRelative[1],
            d,
            button.compoundDrawablesRelative[3]
        )
    }

    private inner class OnboardingAdapter(
        private val layouts: IntArray
    ) : RecyclerView.Adapter<OnboardingAdapter.VH>() {

        inner class VH(val root: View) : RecyclerView.ViewHolder(root)

        override fun getItemViewType(position: Int): Int = layouts[position]
        override fun getItemCount(): Int = layouts.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            // no-op
        }
    }
}
