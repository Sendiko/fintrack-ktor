package dev.sendiko.fintrack.models

import kotlinx.serialization.Serializable

@Serializable
data class CreateTransactionRequest(
    val name: String,
    val amount: Double,
    val type: String,
    val categoryId: String,
    val walletId: String,
    val userId: String? = null,
    val date: String? = null
)

@Serializable
data class UpdateTransactionRequest(
    val name: String? = null,
    val amount: Double? = null,
    val type: String? = null,
    val categoryId: String? = null,
    val walletId: String? = null,
    val date: String? = null
)

@Serializable
data class WalletShortDto(
    val id: String,
    val name: String
)

@Serializable
data class CategoryShortDto(
    val id: String,
    val name: String,
    val color: String? = null,
    val icon: String? = null
)

@Serializable
data class TransactionDto(
    val id: String,
    val name: String,
    val amount: Double,
    val type: String,
    val categoryId: String,
    val walletId: String,
    val userId: String,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
    val wallet: WalletShortDto? = null,
    val category: CategoryShortDto? = null
)

@Serializable
data class TransactionResponse(
    val status: Int,
    val message: String,
    val transaction: TransactionDto
)

@Serializable
data class TransactionsResponse(
    val status: Int,
    val message: String,
    val transactions: List<TransactionDto>
)
