package com.simats.agritrace.model

// Batch.kt
data class Batch(
    val id: String,
    val cropName: String,
    val variety: String,
    val batchCode: String,
    val status: String
)
