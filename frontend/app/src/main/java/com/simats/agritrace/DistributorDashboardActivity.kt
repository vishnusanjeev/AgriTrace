package com.simats.agritrace.Farmer.distributor

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import com.simats.agritrace.LoginActivity
import com.simats.agritrace.R
import com.simats.agritrace.Session
import com.simats.agritrace.databinding.ActivityDistributorDashboardBinding
import com.simats.agritrace.dist.DistributorBatchesFragment
import com.simats.agritrace.dist.DistributorHomeFragment
import com.simats.agritrace.dist.DistributorProfileFragment
import com.simats.agritrace.dist.DistributorScanFragment

class DistributorDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDistributorDashboardBinding

    private val TAG_HOME = "tab_home"
    private val TAG_SCAN = "tab_scan"
    private val TAG_PRODUCTS = "tab_products"
    private val TAG_PROFILE = "tab_profile"
    private val KEY_ACTIVE = "active_tab"

    private var activeTag: String = TAG_HOME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        if (!Session.ensureValidOrClear(this)) {
            startActivity(Intent(this, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
            finish()
            return
        }

        binding = ActivityDistributorDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply window insets to root container, preserving original padding
        ViewCompat.setOnApplyWindowInsetsListener(binding.distributorContainer) { v, insets ->
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

        // Apply window insets to bottom navigation bar to prevent text clipping
        ViewCompat.setOnApplyWindowInsetsListener(binding.distBottomNav) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val originalPaddingBottom = v.paddingBottom
            v.setPadding(
                v.paddingLeft,
                v.paddingTop,
                v.paddingRight,
                maxOf(originalPaddingBottom, systemBars.bottom)
            )
            insets
        }

        activeTag = savedInstanceState?.getString(KEY_ACTIVE) ?: TAG_HOME

        ensureHomeCreated()
        showTab(activeTag)
        syncBottomNavSelection(activeTag)

        binding.distBottomNav.setOnItemSelectedListener { item ->
            val target = when (item.itemId) {
                R.id.dist_dashboard -> TAG_HOME
                R.id.dist_scan_qr -> TAG_SCAN
                R.id.dist_products -> TAG_PRODUCTS
                R.id.dist_profile -> TAG_PROFILE
                else -> TAG_HOME
            }
            if (target != activeTag) showTab(target)
            true
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (activeTag != TAG_HOME) {
                    binding.distBottomNav.selectedItemId = R.id.dist_dashboard
                    return
                }
                finish()
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_ACTIVE, activeTag)
        super.onSaveInstanceState(outState)
    }

    fun openScanTab() {
        if (activeTag != TAG_SCAN) {
            binding.distBottomNav.selectedItemId = R.id.dist_scan_qr
        }
    }

    fun openProductsTab() {
        if (activeTag != TAG_PRODUCTS) {
            binding.distBottomNav.selectedItemId = R.id.dist_products
        }
    }

    private fun syncBottomNavSelection(tag: String) {
        binding.distBottomNav.selectedItemId = when (tag) {
            TAG_HOME -> R.id.dist_dashboard
            TAG_SCAN -> R.id.dist_scan_qr
            TAG_PRODUCTS -> R.id.dist_products
            TAG_PROFILE -> R.id.dist_profile
            else -> R.id.dist_dashboard
        }
    }

    private fun ensureHomeCreated() {
        val fm = supportFragmentManager
        if (fm.findFragmentByTag(TAG_HOME) != null) return

        fm.commitNow {
            setReorderingAllowed(true)
            val home = DistributorHomeFragment()
            add(R.id.tab_container, home, TAG_HOME)
            setMaxLifecycle(home, Lifecycle.State.RESUMED)
        }
    }

    private fun createIfNeeded(tag: String): Fragment {
        val fm = supportFragmentManager
        fm.findFragmentByTag(tag)?.let { return it }

        val fragment: Fragment = when (tag) {
            TAG_HOME -> DistributorHomeFragment()
            TAG_SCAN -> DistributorScanFragment()
            TAG_PRODUCTS -> DistributorBatchesFragment()
            TAG_PROFILE -> DistributorProfileFragment()
            else -> DistributorHomeFragment()
        }

        fm.commitNow {
            setReorderingAllowed(true)
            add(R.id.tab_container, fragment, tag)
            hide(fragment)
            setMaxLifecycle(fragment, Lifecycle.State.STARTED)
        }

        return fragment
    }

    private fun showTab(tag: String) {
        val fm = supportFragmentManager
        val current = fm.findFragmentByTag(activeTag)
        val target = createIfNeeded(tag)

        fm.commit {
            setReorderingAllowed(true)
            setCustomAnimations(0, 0, 0, 0)

            if (current != null && current != target) {
                hide(current)
                setMaxLifecycle(current, Lifecycle.State.STARTED)
            }

            show(target)
            setMaxLifecycle(target, Lifecycle.State.RESUMED)
        }

        activeTag = tag
        syncBottomNavSelection(tag)
    }
}
