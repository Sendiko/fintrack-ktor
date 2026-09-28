package dev.sendiko.fintrack.services

import dev.sendiko.fintrack.database.CategoriesTable
import dev.sendiko.fintrack.database.TransactionsTable
import dev.sendiko.fintrack.models.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

class CategoryService {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    suspend fun listCategories(userId: String): List<CategoryDto> = dbQuery {
        CategoriesTable.selectAll().where { CategoriesTable.userId eq userId }.map { c ->
            val catId = c[CategoriesTable.id]
            val transactions = TransactionsTable.selectAll()
                .where { (TransactionsTable.categoryId eq catId) and (TransactionsTable.deletedAt.isNull()) }
                .orderBy(TransactionsTable.createdAt to SortOrder.DESC)
                .map { t ->
                    TransactionDto(
                        id = t[TransactionsTable.id],
                        name = t[TransactionsTable.name],
                        amount = t[TransactionsTable.amount].toDouble(),
                        type = t[TransactionsTable.type],
                        categoryId = t[TransactionsTable.categoryId],
                        walletId = t[TransactionsTable.walletId],
                        userId = t[TransactionsTable.userId],
                        createdAt = t[TransactionsTable.createdAt].toString(),
                        updatedAt = t[TransactionsTable.updatedAt].toString(),
                        deletedAt = t[TransactionsTable.deletedAt]?.toString()
                    )
                }

            CategoryDto(
                id = catId,
                name = c[CategoriesTable.name],
                budget = c[CategoriesTable.budget].toDouble(),
                color = c[CategoriesTable.color],
                icon = c[CategoriesTable.icon],
                userId = c[CategoriesTable.userId],
                createdAt = c[CategoriesTable.createdAt].toString(),
                updatedAt = c[CategoriesTable.updatedAt].toString(),
                transactions = transactions
            )
        }
    }

    suspend fun getCategory(id: String, userId: String): CategoryDto? = dbQuery {
        val c = CategoriesTable.selectAll().where {
            (CategoriesTable.id eq id) and (CategoriesTable.userId eq userId)
        }.singleOrNull() ?: return@dbQuery null

        val transactions = TransactionsTable.selectAll()
            .where { (TransactionsTable.categoryId eq id) and (TransactionsTable.deletedAt.isNull()) }
            .orderBy(TransactionsTable.createdAt to SortOrder.DESC)
            .map { t ->
                TransactionDto(
                    id = t[TransactionsTable.id],
                    name = t[TransactionsTable.name],
                    amount = t[TransactionsTable.amount].toDouble(),
                    type = t[TransactionsTable.type],
                    categoryId = t[TransactionsTable.categoryId],
                    walletId = t[TransactionsTable.walletId],
                    userId = t[TransactionsTable.userId],
                    createdAt = t[TransactionsTable.createdAt].toString(),
                    updatedAt = t[TransactionsTable.updatedAt].toString(),
                    deletedAt = t[TransactionsTable.deletedAt]?.toString()
                )
            }

        CategoryDto(
            id = c[CategoriesTable.id],
            name = c[CategoriesTable.name],
            budget = c[CategoriesTable.budget].toDouble(),
            color = c[CategoriesTable.color],
            icon = c[CategoriesTable.icon],
            userId = c[CategoriesTable.userId],
            createdAt = c[CategoriesTable.createdAt].toString(),
            updatedAt = c[CategoriesTable.updatedAt].toString(),
            transactions = transactions
        )
    }

    suspend fun createCategory(ownerId: String, request: CreateCategoryRequest): CategoryDto = dbQuery {
        val targetUserId = request.userId ?: ownerId
        val categoryId = UUID.randomUUID().toString()
        val now = Instant.now()
        val budgetVal = BigDecimal.valueOf(request.budget ?: 0.0).setScale(2, RoundingMode.HALF_UP)
        val colorVal = request.color ?: "#3B82F6"
        val iconVal = request.icon ?: "folder"

        CategoriesTable.insert {
            it[id] = categoryId
            it[userId] = targetUserId
            it[name] = request.name
            it[budget] = budgetVal
            it[color] = colorVal
            it[icon] = iconVal
            it[createdAt] = now
            it[updatedAt] = now
        }

        CategoryDto(
            id = categoryId,
            name = request.name,
            budget = budgetVal.toDouble(),
            color = colorVal,
            icon = iconVal,
            userId = targetUserId,
            createdAt = now.toString(),
            updatedAt = now.toString()
        )
    }

    suspend fun updateCategory(id: String, userId: String, request: UpdateCategoryRequest): CategoryDto? = dbQuery {
        val existing = CategoriesTable.selectAll().where {
            (CategoriesTable.id eq id) and (CategoriesTable.userId eq userId)
        }.singleOrNull() ?: return@dbQuery null

        val now = Instant.now()
        CategoriesTable.update({ (CategoriesTable.id eq id) and (CategoriesTable.userId eq userId) }) {
            request.name?.let { n -> it[name] = n }
            request.budget?.let { b -> it[budget] = BigDecimal.valueOf(b).setScale(2, RoundingMode.HALF_UP) }
            request.color?.let { c -> it[color] = c }
            request.icon?.let { i -> it[icon] = i }
            it[updatedAt] = now
        }

        val updated = CategoriesTable.selectAll().where { CategoriesTable.id eq id }.single()
        CategoryDto(
            id = updated[CategoriesTable.id],
            name = updated[CategoriesTable.name],
            budget = updated[CategoriesTable.budget].toDouble(),
            color = updated[CategoriesTable.color],
            icon = updated[CategoriesTable.icon],
            userId = updated[CategoriesTable.userId],
            createdAt = updated[CategoriesTable.createdAt].toString(),
            updatedAt = updated[CategoriesTable.updatedAt].toString()
        )
    }

    suspend fun deleteCategory(id: String, userId: String): Boolean = dbQuery {
        val count = CategoriesTable.deleteWhere { (CategoriesTable.id eq id) and (CategoriesTable.userId eq userId) }
        count > 0
    }
}
