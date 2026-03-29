package com.simats.agritrace

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.simats.agritrace.databinding.ActivityRetailerMainBinding

class RetailerDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRetailerMainBinding
    private lateinit var navController: NavController

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

        binding = ActivityRetailerMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply window insets to root container, preserving original padding
        ViewCompat.setOnApplyWindowInsetsListener(binding.retailerContainer) { v, insets ->
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
        ViewCompat.setOnApplyWindowInsetsListener(binding.retailerBottomNav) { v, insets ->
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

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_retailer_main) as NavHostFragment
        navController = navHost.navController

        // IMPORTANT: let NavigationUI wire menu -> destinations
        NavigationUI.setupWithNavController(binding.retailerBottomNav, navController)

        // Hard-fix: when user taps Dashboard tab, always go to start destination cleanly
        binding.retailerBottomNav.setOnItemSelectedListener { item ->
            val startDest = navController.graph.startDestinationId
            val current = navController.currentDestination?.id

            // If same tab tapped, do nothing
            if (current == item.itemId) return@setOnItemSelectedListener true

            return@setOnItemSelectedListener try {
                if (item.itemId == startDest) {
                    // Pop back to dashboard no matter where you are (scan/details/etc)
                    navController.popBackStack(startDest, false)
                    true
                } else {
                    navController.navigate(item.itemId)
                    true
                }
            } catch (_: Exception) {
                false
            }
        }

        // Back button: if not on Dashboard, go Dashboard. else finish.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val startDest = navController.graph.startDestinationId
                val current = navController.currentDestination?.id
                if (current != null && current != startDest) {
                    binding.retailerBottomNav.selectedItemId = startDest
                    return
                }
                finish()
            }
        })
    }

}
