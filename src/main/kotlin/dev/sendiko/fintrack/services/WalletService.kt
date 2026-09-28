package dev.sendiko.fintrack.services

import dev.sendiko.fintrack.database.TransactionsTable
import dev.sendiko.fintrack.database.WalletsTable
import dev.sendiko.fintrack.models.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

class WalletService {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    // ─── Private helpers ────────────────────────────────────────────────────

    private fun ResultRow.toTransactionDto() = TransactionDto(
        id = this[TransactionsTable.id],
        name = this[TransactionsTable.name],
        amount = this[TransactionsTable.amount].toDouble(),
        type = this[TransactionsTable.type],
        categoryId = this[TransactionsTable.categoryId],
        walletId = this[TransactionsTable.walletId],
        userId = this[TransactionsTable.userId],
        createdAt = this[TransactionsTable.createdAt].toString(),
        updatedAt = this[TransactionsTable.updatedAt].toString(),
        deletedAt = this[TransactionsTable.deletedAt]?.toString()
    )

    private fun ResultRow.toWalletDto(transactions: List<TransactionDto>? = null) = WalletDto(
        id = this[WalletsTable.id],
        name = this[WalletsTable.name],
        purpose = this[WalletsTable.purpose],
        type = this[WalletsTable.type],
        balance = this[WalletsTable.balance].toDouble(),
        walletNumber = this[WalletsTable.walletNumber],
        userId = this[WalletsTable.userId],
        createdAt = this[WalletsTable.createdAt].toString(),
        updatedAt = this[WalletsTable.updatedAt].toString(),
        transactions = transactions
    )

    // ─── Public API ─────────────────────────────────────────────────────────

    suspend fun listWallets(userId: String): List<WalletDto> = dbQuery {
        val walletRows = WalletsTable.selectAll()
            .where { WalletsTable.userId eq userId }
            .toList()

        if (walletRows.isEmpty()) return@dbQuery emptyList()

        val walletIds = walletRows.map { it[WalletsTable.id] }

        // Single batched query for all transactions across all wallets
        val txsByWallet = TransactionsTable.selectAll()
            .where { (TransactionsTable.walletId inList walletIds) and (TransactionsTable.deletedAt.isNull()) }
            .orderBy(TransactionsTable.createdAt to SortOrder.DESC)
            .groupBy { it[TransactionsTable.walletId] }
            .mapValues { (_, rows) -> rows.map { it.toTransactionDto() } }

        walletRows.map { w ->
            w.toWalletDto(txsByWallet[w[WalletsTable.id]] ?: emptyList())
        }
    }

    suspend fun getWallet(id: String, userId: String): WalletDto? = dbQuery {
        val w = WalletsTable.selectAll()
            .where { (WalletsTable.id eq id) and (WalletsTable.userId eq userId) }
            .singleOrNull() ?: return@dbQuery null

        val transactions = TransactionsTable.selectAll()
            .where { (TransactionsTable.walletId eq id) and (TransactionsTable.deletedAt.isNull()) }
            .orderBy(TransactionsTable.createdAt to SortOrder.DESC)
            .map { it.toTransactionDto() }

        w.toWalletDto(transactions)
    }

    suspend fun createWallet(ownerId: String, request: CreateWalletRequest): WalletDto = dbQuery {
        val targetUserId = request.userId ?: ownerId
        val walletId = UUID.randomUUID().toString()
        val now = Instant.now()
        val initialBalance = BigDecimal.valueOf(request.balance).setScale(2, RoundingMode.HALF_UP)

        WalletsTable.insert {
            it[id] = walletId
            it[userId] = targetUserId
            it[name] = request.name
            it[purpose] = request.purpose
            it[type] = request.type
            it[balance] = initialBalance
            it[walletNumber] = request.walletNumber
            it[createdAt] = now
            it[updatedAt] = now
        }

        WalletDto(
            id = walletId,
            name = request.name,
            purpose = request.purpose,
            type = request.type,
            balance = initialBalance.toDouble(),
            walletNumber = request.walletNumber,
            userId = targetUserId,
            createdAt = now.toString(),
            updatedAt = now.toString()
        )
    }

    suspend fun updateWallet(id: String, userId: String, request: UpdateWalletRequest): WalletDto? = dbQuery {
        WalletsTable.selectAll()
            .where { (WalletsTable.id eq id) and (WalletsTable.userId eq userId) }
            .singleOrNull() ?: return@dbQuery null

        val now = Instant.now()
        WalletsTable.update({ (WalletsTable.id eq id) and (WalletsTable.userId eq userId) }) {
            request.name?.let { n -> it[name] = n }
            request.purpose?.let { p -> it[purpose] = p }
            request.type?.let { t -> it[type] = t }
            request.balance?.let { b -> it[balance] = BigDecimal.valueOf(b).setScale(2, RoundingMode.HALF_UP) }
            request.walletNumber?.let { wn -> it[walletNumber] = wn }
            it[updatedAt] = now
        }

        WalletsTable.selectAll().where { WalletsTable.id eq id }.single().toWalletDto()
    }

    suspend fun deleteWallet(id: String, userId: String): Boolean = dbQuery {
        WalletsTable.selectAll()
            .where { (WalletsTable.id eq id) and (WalletsTable.userId eq userId) }
            .singleOrNull() ?: return@dbQuery false

        // Soft-delete all transactions belonging to this wallet before deleting the wallet
        val now = Instant.now()
        TransactionsTable.update({ TransactionsTable.walletId eq id }) {
            it[deletedAt] = now
            it[updatedAt] = now
        }

        WalletsTable.deleteWhere { (WalletsTable.id eq id) and (WalletsTable.userId eq userId) } > 0
    }
}
