package com.simats.agritrace

import android.os.Bundle
import android.content.Intent

import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.simats.agritrace.databinding.ActivityCustomerBinding

class CustomerDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomerBinding
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

        binding = ActivityCustomerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply window insets to root container, preserving original padding
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
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
        ViewCompat.setOnApplyWindowInsetsListener(binding.customerBottomNav) { v, insets ->
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
            .findFragmentById(R.id.customer_nav_host) as NavHostFragment
        navController = navHost.navController

        binding.customerBottomNav.setupWithNavController(navController)
        binding.customerBottomNav.setOnItemSelectedListener { item ->
            if (navController.currentDestination?.id == item.itemId) {
                return@setOnItemSelectedListener true
            }

            val options = NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .setPopUpTo(navController.graph.startDestinationId, false, true)
                .build()

            return@setOnItemSelectedListener try {
                val popped = navController.popBackStack(item.itemId, false)
                if (!popped) {
                    navController.navigate(item.itemId, null, options)
                }
                true
            } catch (_: IllegalArgumentException) {
                false
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val startDest = navController.graph.startDestinationId
                val current = navController.currentDestination?.id
                if (current != null && current != startDest) {
                    binding.customerBottomNav.selectedItemId = startDest
                    return
                }
                finish()
            }
        })
    }
}
