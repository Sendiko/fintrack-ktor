package dev.sendiko.fintrack.services

import dev.sendiko.fintrack.database.CategoriesTable
import dev.sendiko.fintrack.database.TransactionsTable
import dev.sendiko.fintrack.models.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

class AnalysisService {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    suspend fun getSpendingAnalysis(
        userId: String,
        rangeParam: String?,
        startDateParam: String?,
        endDateParam: String?
    ): SpendingAnalysisResponse = dbQuery {
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        val rangeType = rangeParam?.lowercase() ?: "month"

        val (rangeStart, rangeEnd, effectiveRangeType) = when (rangeType) {
            "week" -> {
                val monday = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).truncatedTo(ChronoUnit.DAYS)
                val sunday = monday.plusDays(6).withHour(23).withMinute(59).withSecond(59).withNano(999_000_000)
                Triple(monday.toInstant(), sunday.toInstant(), "week")
            }
            "custom" -> {
                if (!startDateParam.isNullOrBlank() && !endDateParam.isNullOrBlank()) {
                    try {
                        val sDate = LocalDate.parse(startDateParam.substring(0, 10))
                        val eDate = LocalDate.parse(endDateParam.substring(0, 10))
                        val sInstant = sDate.atStartOfDay(ZoneOffset.UTC).toInstant()
                        val eInstant = eDate.atTime(23, 59, 59, 999_000_000).atZone(ZoneOffset.UTC).toInstant()
                        Triple(sInstant, eInstant, "custom")
                    } catch (e: Exception) {
                        val start = now.with(TemporalAdjusters.firstDayOfMonth()).truncatedTo(ChronoUnit.DAYS).toInstant()
                        val end = now.with(TemporalAdjusters.lastDayOfMonth()).withHour(23).withMinute(59).withSecond(59).withNano(999_000_000).toInstant()
                        Triple(start, end, "month")
                    }
                } else {
                    val start = now.with(TemporalAdjusters.firstDayOfMonth()).truncatedTo(ChronoUnit.DAYS).toInstant()
                    val end = now.with(TemporalAdjusters.lastDayOfMonth()).withHour(23).withMinute(59).withSecond(59).withNano(999_000_000).toInstant()
                    Triple(start, end, "month")
                }
            }
            else -> { // "month" default
                val start = now.with(TemporalAdjusters.firstDayOfMonth()).truncatedTo(ChronoUnit.DAYS).toInstant()
                val end = now.with(TemporalAdjusters.lastDayOfMonth()).withHour(23).withMinute(59).withSecond(59).withNano(999_000_000).toInstant()
                Triple(start, end, "month")
            }
        }

        // Fetch user categories
        val userCategories = CategoriesTable.selectAll().where { CategoriesTable.userId eq userId }.map {
            CategoryDto(
                id = it[CategoriesTable.id],
                name = it[CategoriesTable.name],
                budget = it[CategoriesTable.budget].toDouble(),
                color = it[CategoriesTable.color],
                icon = it[CategoriesTable.icon],
                userId = it[CategoriesTable.userId]
            )
        }

        // Fetch all expense transactions in range
        val transactionsInRange = TransactionsTable.selectAll().where {
            (TransactionsTable.userId eq userId) and
            (TransactionsTable.type eq "expense") and
            (TransactionsTable.deletedAt.isNull()) and
            (TransactionsTable.createdAt greaterEq rangeStart) and
            (TransactionsTable.createdAt lessEq rangeEnd)
        }.toList()

        val totalSpending = transactionsInRange.sumOf { it[TransactionsTable.amount].toDouble() }

        // Category metrics calculation
        val categoryExpenses = transactionsInRange.groupBy { it[TransactionsTable.categoryId] }
            .mapValues { (_, txs) -> txs.sumOf { it[TransactionsTable.amount].toDouble() } }

        val categoryAnalysisList = userCategories.map { cat ->
            val catAmount = categoryExpenses[cat.id] ?: 0.0
            val pctTotal = if (totalSpending > 0.0) roundToOneDecimal((catAmount / totalSpending) * 100.0) else 0.0
            val pctBudget = if (cat.budget > 0.0) roundToOneDecimal((catAmount / cat.budget) * 100.0) else 0.0
            CategoryAnalysisDto(
                id = cat.id,
                name = cat.name,
                color = cat.color,
                icon = cat.icon,
                amount = catAmount,
                budget = cat.budget,
                percentageOfTotal = pctTotal,
                percentageOfBudget = pctBudget
            )
        }.sortedByDescending { it.amount }

        // 6-Month rolling history
        val monthFormatter = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
        val historyMonths = (5 downTo 0).map { monthsAgo ->
            val targetMonth = now.minusMonths(monthsAgo.toLong())
            val mStart = targetMonth.with(TemporalAdjusters.firstDayOfMonth()).truncatedTo(ChronoUnit.DAYS).toInstant()
            val mEnd = targetMonth.with(TemporalAdjusters.lastDayOfMonth()).withHour(23).withMinute(59).withSecond(59).withNano(999_000_000).toInstant()

            val monthExpenses = TransactionsTable.selectAll().where {
                (TransactionsTable.userId eq userId) and
                (TransactionsTable.type eq "expense") and
                (TransactionsTable.deletedAt.isNull()) and
                (TransactionsTable.createdAt greaterEq mStart) and
                (TransactionsTable.createdAt lessEq mEnd)
            }.sumOf { it[TransactionsTable.amount].toDouble() }

            val monthName = targetMonth.format(monthFormatter).uppercase(Locale.ENGLISH)
            HistoryMonthDto(month = monthName, amount = monthExpenses)
        }

        val currentMonthAmount = historyMonths[5].amount
        val prevMonthAmount = historyMonths[4].amount

        val momChangePercentage = if (prevMonthAmount > 0.0) {
            roundToOneDecimal(((currentMonthAmount - prevMonthAmount) / prevMonthAmount) * 100.0)
        } else if (currentMonthAmount > 0.0) {
            100.0
        } else {
            0.0
        }

        SpendingAnalysisResponse(
            status = 200,
            message = "Spending analysis fetched successfully.",
            data = SpendingAnalysisData(
                totalSpending = totalSpending,
                range = DateRangeDto(
                    type = effectiveRangeType,
                    startDate = rangeStart.toString(),
                    endDate = rangeEnd.toString()
                ),
                categories = categoryAnalysisList,
                history = HistoryDto(
                    momChangePercentage = momChangePercentage,
                    months = historyMonths
                )
            )
        )
    }
}
