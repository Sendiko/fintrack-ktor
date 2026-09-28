package dev.sendiko.fintrack.services

import dev.sendiko.fintrack.database.CategoriesTable
import dev.sendiko.fintrack.database.TransactionsTable
import dev.sendiko.fintrack.database.WalletsTable
import dev.sendiko.fintrack.models.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class TransactionService {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    private fun parseDateOrNow(dateStr: String?): Instant {
        if (dateStr.isNullOrBlank()) return Instant.now()
        return try {
            Instant.parse(dateStr)
        } catch (e: Exception) {
            try {
                LocalDate.parse(dateStr).atStartOfDay(ZoneOffset.UTC).toInstant()
            } catch (e2: Exception) {
                Instant.now()
            }
        }
    }

    suspend fun listTransactions(userId: String): List<TransactionDto> = dbQuery {
        (TransactionsTable innerJoin WalletsTable innerJoin CategoriesTable)
            .selectAll()
            .where { (TransactionsTable.userId eq userId) and (TransactionsTable.deletedAt.isNull()) }
            .orderBy(TransactionsTable.createdAt to SortOrder.DESC)
            .map { r ->
                TransactionDto(
                    id = r[TransactionsTable.id],
                    name = r[TransactionsTable.name],
                    amount = r[TransactionsTable.amount].toDouble(),
                    type = r[TransactionsTable.type],
                    categoryId = r[TransactionsTable.categoryId],
                    walletId = r[TransactionsTable.walletId],
                    userId = r[TransactionsTable.userId],
                    createdAt = r[TransactionsTable.createdAt].toString(),
                    updatedAt = r[TransactionsTable.updatedAt].toString(),
                    deletedAt = r[TransactionsTable.deletedAt]?.toString(),
                    wallet = WalletShortDto(
                        id = r[WalletsTable.id],
                        name = r[WalletsTable.name]
                    ),
                    category = CategoryShortDto(
                        id = r[CategoriesTable.id],
                        name = r[CategoriesTable.name],
                        color = r[CategoriesTable.color],
                        icon = r[CategoriesTable.icon]
                    )
                )
            }
    }

    suspend fun getTransaction(id: String, userId: String): TransactionDto? = dbQuery {
        (TransactionsTable innerJoin WalletsTable innerJoin CategoriesTable)
            .selectAll()
            .where {
                (TransactionsTable.id eq id) and
                (TransactionsTable.userId eq userId) and
                (TransactionsTable.deletedAt.isNull())
            }
            .singleOrNull()
            ?.let { r ->
                TransactionDto(
                    id = r[TransactionsTable.id],
                    name = r[TransactionsTable.name],
                    amount = r[TransactionsTable.amount].toDouble(),
                    type = r[TransactionsTable.type],
                    categoryId = r[TransactionsTable.categoryId],
                    walletId = r[TransactionsTable.walletId],
                    userId = r[TransactionsTable.userId],
                    createdAt = r[TransactionsTable.createdAt].toString(),
                    updatedAt = r[TransactionsTable.updatedAt].toString(),
                    deletedAt = r[TransactionsTable.deletedAt]?.toString(),
                    wallet = WalletShortDto(
                        id = r[WalletsTable.id],
                        name = r[WalletsTable.name]
                    ),
                    category = CategoryShortDto(
                        id = r[CategoriesTable.id],
                        name = r[CategoriesTable.name],
                        color = r[CategoriesTable.color],
                        icon = r[CategoriesTable.icon]
                    )
                )
            }
    }

    suspend fun createTransaction(ownerId: String, request: CreateTransactionRequest): Result<TransactionDto> = dbQuery {
        val targetUserId = request.userId ?: ownerId
        val walletRow = WalletsTable.selectAll()
            .where { WalletsTable.id eq request.walletId }
            .singleOrNull() ?: return@dbQuery Result.failure(NoSuchElementException("Wallet not found."))

        val categoryRow = CategoriesTable.selectAll()
            .where { CategoriesTable.id eq request.categoryId }
            .singleOrNull() ?: return@dbQuery Result.failure(NoSuchElementException("Category not found."))

        val amountBd = BigDecimal.valueOf(request.amount).setScale(2, RoundingMode.HALF_UP)
        val currentBalance = walletRow[WalletsTable.balance]
        val newBalance = when (request.type.lowercase()) {
            "expense" -> currentBalance.subtract(amountBd)
            "income" -> currentBalance.add(amountBd)
            else -> return@dbQuery Result.failure(IllegalArgumentException("Invalid transaction type. Must be income or expense."))
        }

        val txId = UUID.randomUUID().toString()
        val now = Instant.now()
        val effectiveCreatedAt = parseDateOrNow(request.date)

        // 1. Update wallet balance
        WalletsTable.update({ WalletsTable.id eq request.walletId }) {
            it[balance] = newBalance
            it[updatedAt] = now
        }

        // 2. Insert transaction
        TransactionsTable.insert {
            it[id] = txId
            it[userId] = targetUserId
            it[walletId] = request.walletId
            it[categoryId] = request.categoryId
            it[name] = request.name
            it[amount] = amountBd
            it[type] = request.type.lowercase()
            it[createdAt] = effectiveCreatedAt
            it[updatedAt] = now
            it[deletedAt] = null
        }

        Result.success(
            TransactionDto(
                id = txId,
                name = request.name,
                amount = amountBd.toDouble(),
                type = request.type.lowercase(),
                categoryId = request.categoryId,
                walletId = request.walletId,
                userId = targetUserId,
                createdAt = effectiveCreatedAt.toString(),
                updatedAt = now.toString(),
                deletedAt = null,
                wallet = WalletShortDto(
                    id = walletRow[WalletsTable.id],
                    name = walletRow[WalletsTable.name]
                ),
                category = CategoryShortDto(
                    id = categoryRow[CategoriesTable.id],
                    name = categoryRow[CategoriesTable.name],
                    color = categoryRow[CategoriesTable.color],
                    icon = categoryRow[CategoriesTable.icon]
                )
            )
        )
    }

    suspend fun updateTransaction(
        id: String,
        userId: String,
        request: UpdateTransactionRequest
    ): Result<TransactionDto> = dbQuery {
        val oldTx = TransactionsTable.selectAll().where {
            (TransactionsTable.id eq id) and
            (TransactionsTable.userId eq userId) and
            (TransactionsTable.deletedAt.isNull())
        }.singleOrNull() ?: return@dbQuery Result.failure(NoSuchElementException("Transaction not found."))

        val oldWalletId = oldTx[TransactionsTable.walletId]
        val oldAmount = oldTx[TransactionsTable.amount]
        val oldType = oldTx[TransactionsTable.type].lowercase()

        val newWalletId = request.walletId ?: oldWalletId
        val newAmount = request.amount?.let { BigDecimal.valueOf(it).setScale(2, RoundingMode.HALF_UP) } ?: oldAmount
        val newType = request.type?.lowercase() ?: oldType
        val newCategoryId = request.categoryId ?: oldTx[TransactionsTable.categoryId]
        val newName = request.name ?: oldTx[TransactionsTable.name]
        val effectiveCreatedAt = if (request.date != null) parseDateOrNow(request.date) else oldTx[TransactionsTable.createdAt]
        val now = Instant.now()

        // Step 1: Revert Old Transaction on Old Wallet
        val oldWalletRow = WalletsTable.selectAll().where { WalletsTable.id eq oldWalletId }.singleOrNull()
            ?: return@dbQuery Result.failure(NoSuchElementException("Associated old wallet not found."))

        var oldWalletBalance = oldWalletRow[WalletsTable.balance]
        oldWalletBalance = when (oldType) {
            "expense" -> oldWalletBalance.add(oldAmount)
            "income" -> oldWalletBalance.subtract(oldAmount)
            else -> oldWalletBalance
        }

        // Step 2: Apply New Transaction on New Wallet
        if (newWalletId == oldWalletId) {
            val finalBalance = when (newType) {
                "expense" -> oldWalletBalance.subtract(newAmount)
                "income" -> oldWalletBalance.add(newAmount)
                else -> return@dbQuery Result.failure(IllegalArgumentException("Invalid transaction type: $newType"))
            }
            WalletsTable.update({ WalletsTable.id eq newWalletId }) {
                it[balance] = finalBalance
                it[updatedAt] = now
            }
        } else {
            // Two different wallets
            val newWalletRow = WalletsTable.selectAll().where { WalletsTable.id eq newWalletId }.singleOrNull()
                ?: return@dbQuery Result.failure(NoSuchElementException("Target wallet not found."))

            var newWalletBalance = newWalletRow[WalletsTable.balance]
            newWalletBalance = when (newType) {
                "expense" -> newWalletBalance.subtract(newAmount)
                "income" -> newWalletBalance.add(newAmount)
                else -> return@dbQuery Result.failure(IllegalArgumentException("Invalid transaction type: $newType"))
            }

            WalletsTable.update({ WalletsTable.id eq oldWalletId }) {
                it[balance] = oldWalletBalance
                it[updatedAt] = now
            }
            WalletsTable.update({ WalletsTable.id eq newWalletId }) {
                it[balance] = newWalletBalance
                it[updatedAt] = now
            }
        }

        // Step 3: Update Transaction
        TransactionsTable.update({ TransactionsTable.id eq id }) {
            it[name] = newName
            it[amount] = newAmount
            it[type] = newType
            it[walletId] = newWalletId
            it[categoryId] = newCategoryId
            it[createdAt] = effectiveCreatedAt
            it[updatedAt] = now
        }

        // Fetch wallet and category for response
        val finalWallet = WalletsTable.selectAll().where { WalletsTable.id eq newWalletId }.single()
        val finalCategory = CategoriesTable.selectAll().where { CategoriesTable.id eq newCategoryId }.single()

        Result.success(
            TransactionDto(
                id = id,
                name = newName,
                amount = newAmount.toDouble(),
                type = newType,
                categoryId = newCategoryId,
                walletId = newWalletId,
                userId = userId,
                createdAt = effectiveCreatedAt.toString(),
                updatedAt = now.toString(),
                deletedAt = null,
                wallet = WalletShortDto(
                    id = finalWallet[WalletsTable.id],
                    name = finalWallet[WalletsTable.name]
                ),
                category = CategoryShortDto(
                    id = finalCategory[CategoriesTable.id],
                    name = finalCategory[CategoriesTable.name],
                    color = finalCategory[CategoriesTable.color],
                    icon = finalCategory[CategoriesTable.icon]
                )
            )
        )
    }

    suspend fun deleteTransaction(id: String, userId: String): Boolean = dbQuery {
        val tx = TransactionsTable.selectAll().where {
            (TransactionsTable.id eq id) and
            (TransactionsTable.userId eq userId) and
            (TransactionsTable.deletedAt.isNull())
        }.singleOrNull() ?: return@dbQuery false

        val walletId = tx[TransactionsTable.walletId]
        val amount = tx[TransactionsTable.amount]
        val type = tx[TransactionsTable.type].lowercase()
        val now = Instant.now()

        // Revert wallet balance
        val wallet = WalletsTable.selectAll().where { WalletsTable.id eq walletId }.singleOrNull()
        if (wallet != null) {
            val currentBalance = wallet[WalletsTable.balance]
            val newBalance = when (type) {
                "expense" -> currentBalance.add(amount) // restore spent money
                "income" -> currentBalance.subtract(amount) // deduct credited income
                else -> currentBalance
            }
            WalletsTable.update({ WalletsTable.id eq walletId }) {
                it[balance] = newBalance
                it[updatedAt] = now
            }
        }

        // Soft-delete transaction
        TransactionsTable.update({ TransactionsTable.id eq id }) {
            it[deletedAt] = now
            it[updatedAt] = now
        }

        true
    }
}
