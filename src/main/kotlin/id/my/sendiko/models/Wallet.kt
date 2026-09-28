package id.my.sendiko.models

import kotlinx.serialization.Serializable

@Serializable
data class CreateWalletRequest(
    val name: String,
    val purpose: String,
    val type: String,
    val balance: Double,
    val walletNumber: String? = null,
    val userId: String? = null
)

@Serializable
data class UpdateWalletRequest(
    val name: String? = null,
    val purpose: String? = null,
    val type: String? = null,
    val balance: Double? = null,
    val walletNumber: String? = null
)

@Serializable
data class WalletDto(
    val id: String,
    val name: String,
    val purpose: String,
    val type: String,
    val balance: Double,
    val walletNumber: String? = null,
    val userId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val transactions: List<TransactionDto>? = null
)

@Serializable
data class WalletResponse(
    val status: Int,
    val message: String,
    val wallet: WalletDto
)

@Serializable
data class WalletsResponse(
    val status: Int,
    val message: String,
    val wallets: List<WalletDto>
)
