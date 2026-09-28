package id.my.sendiko.models

import kotlinx.serialization.Serializable

@Serializable
data class CreateCategoryRequest(
    val name: String,
    val budget: Double? = null,
    val color: String? = null,
    val icon: String? = null,
    val userId: String? = null
)

@Serializable
data class UpdateCategoryRequest(
    val name: String? = null,
    val budget: Double? = null,
    val color: String? = null,
    val icon: String? = null
)

@Serializable
data class CategoryDto(
    val id: String,
    val name: String,
    val budget: Double,
    val color: String,
    val icon: String,
    val userId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val transactions: List<TransactionDto>? = null
)

@Serializable
data class CategoryResponse(
    val status: Int,
    val message: String,
    val category: CategoryDto
)

@Serializable
data class CategoriesResponse(
    val status: Int,
    val message: String,
    val categories: List<CategoryDto>
)
