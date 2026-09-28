package id.my.sendiko.models

import kotlinx.serialization.Serializable
import kotlin.math.round

@Serializable
data class SimpleResponse(
    val status: Int,
    val message: String
)

@Serializable
data class ErrorResponse(
    val status: Int,
    val message: String,
    val error: String? = null
)

fun roundToOneDecimal(value: Double): Double {
    return round(value * 10.0) / 10.0
}
