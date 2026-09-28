package id.my.sendiko.models

import kotlinx.serialization.Serializable

@Serializable
data class CategoryAnalysisDto(
    val id: String,
    val name: String,
    val color: String,
    val icon: String,
    val amount: Double,
    val budget: Double,
    val percentageOfTotal: Double,
    val percentageOfBudget: Double
)

@Serializable
data class HistoryMonthDto(
    val month: String,
    val amount: Double
)

@Serializable
data class HistoryDto(
    val momChangePercentage: Double,
    val months: List<HistoryMonthDto>
)

@Serializable
data class DateRangeDto(
    val type: String,
    val startDate: String,
    val endDate: String
)

@Serializable
data class SpendingAnalysisData(
    val totalSpending: Double,
    val range: DateRangeDto,
    val categories: List<CategoryAnalysisDto>,
    val history: HistoryDto
)

@Serializable
data class SpendingAnalysisResponse(
    val status: Int,
    val message: String,
    val data: SpendingAnalysisData
)
