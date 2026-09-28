package id.my.sendiko.services

import id.my.sendiko.database.CategoriesTable
import id.my.sendiko.database.TransactionsTable
import id.my.sendiko.database.UsersTable
import id.my.sendiko.database.WalletsTable
import id.my.sendiko.models.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.mindrot.jbcrypt.BCrypt
import java.time.Instant
import java.util.UUID

class UserService(private val jwtService: JwtService) {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    suspend fun register(request: RegisterRequest): Result<UserDto> {
        val email = request.email?.trim()
        val name = request.name?.trim()
        val password = request.password

        if (email.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Email is required."))
        }
        if (name.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Name is required."))
        }
        if (password.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Password is required."))
        }

        return dbQuery {
            val existing = UsersTable.selectAll().where { UsersTable.email eq email }.singleOrNull()
            if (existing != null) {
                return@dbQuery Result.failure(IllegalStateException("Email is already registered."))
            }

            val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt(10))
            val now = Instant.now()
            val userId = UUID.randomUUID().toString()

            UsersTable.insert {
                it[id] = userId
                it[UsersTable.name] = name
                it[UsersTable.email] = email
                it[UsersTable.password] = hashedPassword
                it[token] = null
                it[createdAt] = now
                it[updatedAt] = now
            }

            Result.success(
                UserDto(
                    id = userId,
                    name = name,
                    email = email,
                    createdAt = now.toString(),
                    updatedAt = now.toString()
                )
            )
        }
    }

    suspend fun login(request: LoginRequest): Result<UserDto> {
        val password = request.password
        if (password.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Password is required."))
        }

        val identifier = (request.email ?: request.name)?.trim()
        if (identifier.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Email or username is required."))
        }

        return dbQuery {
            val userRow = UsersTable.selectAll().where {
                (UsersTable.email eq identifier) or (UsersTable.name eq identifier)
            }.singleOrNull() ?: return@dbQuery Result.failure(NoSuchElementException("User not found."))

            val storedHash = userRow[UsersTable.password]
            if (!BCrypt.checkpw(password, storedHash)) {
                return@dbQuery Result.failure(SecurityException("Invalid password."))
            }

            val userId = userRow[UsersTable.id]
            val userName = userRow[UsersTable.name]
            val userEmail = userRow[UsersTable.email]
            val token = jwtService.generateToken(userId, userName, userEmail)
            val now = Instant.now()

            UsersTable.update({ UsersTable.id eq userId }) {
                it[UsersTable.token] = token
                it[updatedAt] = now
            }

            Result.success(
                UserDto(
                    id = userId,
                    name = userName,
                    email = userEmail,
                    token = token,
                    createdAt = userRow[UsersTable.createdAt].toString(),
                    updatedAt = now.toString()
                )
            )
        }
    }

    suspend fun findByEmail(email: String): UserDto? = dbQuery {
        UsersTable.selectAll().where { UsersTable.email eq email }.singleOrNull()?.let {
            UserDto(
                id = it[UsersTable.id],
                name = it[UsersTable.name],
                email = it[UsersTable.email]
            )
        }
    }

    suspend fun findById(id: String, includeRelations: Boolean = false): UserDto? = dbQuery {
        val userRow = UsersTable.selectAll().where { UsersTable.id eq id }.singleOrNull() ?: return@dbQuery null

        if (!includeRelations) {
            return@dbQuery UserDto(
                id = userRow[UsersTable.id],
                name = userRow[UsersTable.name],
                email = userRow[UsersTable.email],
                token = userRow[UsersTable.token],
                createdAt = userRow[UsersTable.createdAt].toString(),
                updatedAt = userRow[UsersTable.updatedAt].toString()
            )
        }

        // Fetch Wallets
        val wallets = WalletsTable.selectAll().where { WalletsTable.userId eq id }.map { w ->
            val wId = w[WalletsTable.id]
            val transactions = TransactionsTable.selectAll()
                .where { (TransactionsTable.walletId eq wId) and (TransactionsTable.deletedAt.isNull()) }
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
            WalletDto(
                id = wId,
                name = w[WalletsTable.name],
                purpose = w[WalletsTable.purpose],
                type = w[WalletsTable.type],
                balance = w[WalletsTable.balance].toDouble(),
                walletNumber = w[WalletsTable.walletNumber],
                userId = w[WalletsTable.userId],
                createdAt = w[WalletsTable.createdAt].toString(),
                updatedAt = w[WalletsTable.updatedAt].toString(),
                transactions = transactions
            )
        }

        // Fetch Categories
        val categories = CategoriesTable.selectAll().where { CategoriesTable.userId eq id }.map { c ->
            val cId = c[CategoriesTable.id]
            val transactions = TransactionsTable.selectAll()
                .where { (TransactionsTable.categoryId eq cId) and (TransactionsTable.deletedAt.isNull()) }
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
                id = cId,
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

        UserDto(
            id = userRow[UsersTable.id],
            name = userRow[UsersTable.name],
            email = userRow[UsersTable.email],
            token = userRow[UsersTable.token],
            createdAt = userRow[UsersTable.createdAt].toString(),
            updatedAt = userRow[UsersTable.updatedAt].toString(),
            wallets = wallets,
            categories = categories
        )
    }

    suspend fun update(id: String, request: UserUpdateRequest): UserDto? = dbQuery {
        val userRow = UsersTable.selectAll().where { UsersTable.id eq id }.singleOrNull() ?: return@dbQuery null
        val now = Instant.now()

        UsersTable.update({ UsersTable.id eq id }) {
            request.name?.let { n -> it[name] = n }
            request.password?.takeIf { p -> p.isNotBlank() }?.let { p ->
                it[password] = BCrypt.hashpw(p, BCrypt.gensalt(10))
            }
            it[updatedAt] = now
        }

        val updated = UsersTable.selectAll().where { UsersTable.id eq id }.single()
        UserDto(
            id = updated[UsersTable.id],
            name = updated[UsersTable.name],
            email = updated[UsersTable.email],
            updatedAt = now.toString()
        )
    }

    suspend fun delete(id: String): Boolean = dbQuery {
        val count = UsersTable.deleteWhere { UsersTable.id eq id }
        count > 0
    }

    suspend fun changePassword(id: String, newPassword: String): Boolean = dbQuery {
        val userRow = UsersTable.selectAll().where { UsersTable.id eq id }.singleOrNull() ?: return@dbQuery false
        val hashedPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt(10))
        val now = Instant.now()
        UsersTable.update({ UsersTable.id eq id }) {
            it[password] = hashedPassword
            it[updatedAt] = now
        }
        true
    }

    suspend fun getStatistics(id: String): UserStatisticsResponse? = dbQuery {
        val userRow = UsersTable.selectAll().where { UsersTable.id eq id }.singleOrNull() ?: return@dbQuery null

        val wallets = WalletsTable.selectAll().where { WalletsTable.userId eq id }.map { w ->
            WalletDto(
                id = w[WalletsTable.id],
                name = w[WalletsTable.name],
                purpose = w[WalletsTable.purpose],
                type = w[WalletsTable.type],
                balance = w[WalletsTable.balance].toDouble(),
                walletNumber = w[WalletsTable.walletNumber],
                userId = w[WalletsTable.userId],
                createdAt = w[WalletsTable.createdAt].toString(),
                updatedAt = w[WalletsTable.updatedAt].toString()
            )
        }

        val txRows = TransactionsTable.selectAll()
            .where { (TransactionsTable.userId eq id) and (TransactionsTable.deletedAt.isNull()) }
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

        UserStatisticsResponse(
            status = 200,
            message = "Statistics sent successfully sent.",
            wallets = wallets,
            transactions = TransactionCountRows(
                count = txRows.size.toLong(),
                rows = txRows
            )
        )
    }

    suspend fun validateUserToken(userId: String, token: String): UserDto? = dbQuery {
        val user = UsersTable.selectAll().where { UsersTable.id eq userId }.singleOrNull() ?: return@dbQuery null
        if (user[UsersTable.token] == token) {
            UserDto(
                id = user[UsersTable.id],
                name = user[UsersTable.name],
                email = user[UsersTable.email],
                token = user[UsersTable.token]
            )
        } else {
            null
        }
    }
}
