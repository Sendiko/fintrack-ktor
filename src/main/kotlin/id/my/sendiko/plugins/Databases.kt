package id.my.sendiko.plugins

import id.my.sendiko.database.CategoriesTable
import id.my.sendiko.database.TransactionsTable
import id.my.sendiko.database.UsersTable
import id.my.sendiko.database.WalletsTable
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.configureDatabases(): Database {
    val dbHost = System.getenv("DB_HOST")
    val dbPort = System.getenv("DB_PORT") ?: "3306"
    val dbName = System.getenv("DB_NAME")
    val dbUser = System.getenv("DB_USER") ?: "root"
    val dbPass = System.getenv("DB_PASS") ?: ""

    val database = if (!dbHost.isNullOrBlank() && !dbName.isNullOrBlank()) {
        try {
            Database.connect(
                url = "jdbc:mysql://$dbHost:$dbPort/$dbName?useSSL=false&allowPublicKeyRetrieval=true",
                driver = "com.mysql.cj.jdbc.Driver",
                user = dbUser,
                password = dbPass
            )
        } catch (e: Exception) {
            Database.connect(
                url = "jdbc:h2:mem:fintrack;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
                user = "root",
                password = ""
            )
        }
    } else {
        Database.connect(
            url = "jdbc:h2:mem:fintrack;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            user = "root",
            password = ""
        )
    }

    transaction(database) {
        SchemaUtils.create(
            UsersTable,
            WalletsTable,
            CategoriesTable,
            TransactionsTable
        )
    }

    return database
}
