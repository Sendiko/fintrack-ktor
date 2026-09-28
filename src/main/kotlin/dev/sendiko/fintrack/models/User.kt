package dev.sendiko.fintrack.models

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val name: String? = null,
    val email: String? = null,
    val password: String? = null
)

@Serializable
data class LoginRequest(
    val name: String? = null,
    val email: String? = null,
    val password: String? = null
)

@Serializable
data class UserUpdateRequest(
    val name: String? = null,
    val password: String? = null
)

@Serializable
data class ChangePasswordRequest(
    val password: String? = null
)

@Serializable
data class UserDto(
    val id: String,
    val name: String,
    val email: String,
    val token: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val wallets: List<WalletDto>? = null,
    val categories: List<CategoryDto>? = null
)

@Serializable
data class RegisterResponse(
    val status: Int,
    val message: String,
    val user: UserDto
)

@Serializable
data class LoginResponse(
    val status: Int,
    val message: String,
    val user: UserDto
)

@Serializable
data class UserResponse(
    val status: Int,
    val message: String,
    val user: UserDto
)

@Serializable
data class TransactionCountRows(
    val count: Long,
    val rows: List<TransactionDto>
)

@Serializable
data class UserStatisticsResponse(
    val status: Int,
    val message: String,
    val wallets: List<WalletDto>,
    val transactions: TransactionCountRows
)
