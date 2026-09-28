package dev.sendiko.fintrack.models

import kotlinx.serialization.Serializable

@Serializable
data class ReceiptExtractRequest(
    val receiptText: String? = null
)

@Serializable
data class ReceiptProductDto(
    val name: String,
    val price: Double
)

@Serializable
data class ReceiptDataDto(
    val receipt_name: String,
    val total_price: Double,
    val products: List<ReceiptProductDto>
)

@Serializable
data class ReceiptResponse(
    val status: Int,
    val message: String,
    val data: ReceiptDataDto
)
