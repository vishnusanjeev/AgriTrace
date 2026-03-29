package com.simats.agritrace.model

data class ProductResponse(
    val success: Boolean,
    val products: List<Product>
)
