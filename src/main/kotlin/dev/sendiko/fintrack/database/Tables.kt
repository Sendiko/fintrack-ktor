package dev.sendiko.fintrack.database

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.math.BigDecimal

object UsersTable : Table("users") {
    val id = varchar("id", 36)
    val name = varchar("name", 255)
    val email = varchar("email", 255).uniqueIndex()
    val password = varchar("password", 255)
    val token = varchar("token", 512).nullable()
    val createdAt = timestamp("createdAt")
    val updatedAt = timestamp("updatedAt")

    override val primaryKey = PrimaryKey(id)
}

object WalletsTable : Table("wallets") {
    val id = varchar("id", 36)
    val userId = varchar("userId", 36).references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 255)
    val purpose = varchar("purpose", 255)
    val type = varchar("type", 50)
    val balance = decimal("balance", 15, 2).default(BigDecimal.ZERO)
    val walletNumber = varchar("walletNumber", 255).nullable()
    val createdAt = timestamp("createdAt")
    val updatedAt = timestamp("updatedAt")

    override val primaryKey = PrimaryKey(id)
}

object CategoriesTable : Table("categories") {
    val id = varchar("id", 36)
    val userId = varchar("userId", 36).references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 255)
    val budget = decimal("budget", 15, 2).default(BigDecimal.ZERO)
    val color = varchar("color", 32).default("#3B82F6")
    val icon = varchar("icon", 64).default("folder")
    val createdAt = timestamp("createdAt")
    val updatedAt = timestamp("updatedAt")

    override val primaryKey = PrimaryKey(id)
}

object TransactionsTable : Table("transactions") {
    val id = varchar("id", 36)
    val userId = varchar("userId", 36).references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val walletId = varchar("walletId", 36).references(WalletsTable.id, onDelete = ReferenceOption.CASCADE)
    val categoryId = varchar("categoryId", 36).references(CategoriesTable.id, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 255)
    val amount = decimal("amount", 15, 2)
    val type = varchar("type", 50)
    val createdAt = timestamp("createdAt")
    val updatedAt = timestamp("updatedAt")
    val deletedAt = timestamp("deletedAt").nullable()

    override val primaryKey = PrimaryKey(id)
}
