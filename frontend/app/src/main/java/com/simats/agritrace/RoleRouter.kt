package com.simats.agritrace

import android.content.Context
import android.content.Intent
import com.simats.agritrace.Farmer.distributor.DistributorDashboardActivity

object RoleRouter {

    fun dashboardIntent(ctx: Context, roleRaw: String?): Intent {
        val role = (roleRaw ?: "").trim().uppercase()

        return when (role) {
            "FARMER" -> Intent(ctx, FarmerDashboardActivity::class.java)
            "DISTRIBUTOR" -> Intent(ctx, DistributorDashboardActivity::class.java)
            "RETAILER" -> Intent(ctx, RetailerDashboardActivity::class.java)
            "CONSUMER", "CUSTOMER" -> Intent(ctx, CustomerDashboardActivity::class.java)
            else -> Intent(ctx, LoginActivity::class.java)
        }
    }
}
