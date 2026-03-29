package com.simats.agritrace.retailer

data class SaleRow(
    val title: String,
    val batchCode: String,
    val saleId: String,
    val whenText: String,
    val amountText: String,
    val soldAtMillis: Long

)
