package id.my.sendiko.routes

import id.my.sendiko.models.*
import id.my.sendiko.plugins.authenticateUser
import id.my.sendiko.plugins.authenticatedUser
import id.my.sendiko.services.JwtService
import id.my.sendiko.services.TransactionService
import id.my.sendiko.services.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.transactionRoutes(
    transactionService: TransactionService,
    userService: UserService,
    jwtService: JwtService
) {
    authenticateUser(userService, jwtService) {
        route("/transactions") {
            get {
                val user = call.authenticatedUser
                val transactions = transactionService.listTransactions(user.id)
                call.respond(
                    HttpStatusCode.OK,
                    TransactionsResponse(
                        status = 200,
                        message = "Transactions sent successfully.",
                        transactions = transactions
                    )
                )
            }

            get("/{id}") {
                val id = call.parameters["id"]
                if (id.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, SimpleResponse(400, "Transaction ID is required."))
                    return@get
                }

                val user = call.authenticatedUser
                val transaction = transactionService.getTransaction(id, user.id)
                if (transaction == null) {
                    call.respond(HttpStatusCode.NotFound, SimpleResponse(404, "Transaction not found."))
                } else {
                    call.respond(
                        HttpStatusCode.OK,
                        TransactionResponse(
                            status = 200,
                            message = "Transaction sent successfully.",
                            transaction = transaction
                        )
                    )
                }
            }

            post {
                val user = call.authenticatedUser
                val request = try {
                    call.receive<CreateTransactionRequest>()
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, SimpleResponse(400, "Invalid request payload."))
                    return@post
                }

                val result = transactionService.createTransaction(user.id, request)
                result.onSuccess { tx ->
                    call.respond(
                        HttpStatusCode.Created,
                        TransactionResponse(
                            status = 201,
                            message = "Transaction registered successfully.",
                            transaction = tx
                        )
                    )
                }.onFailure { ex ->
                    when (ex) {
                        is NoSuchElementException ->
                            call.respond(HttpStatusCode.NotFound, SimpleResponse(404, ex.message ?: "Not found."))
                        else ->
                            call.respond(HttpStatusCode.BadRequest, SimpleResponse(400, ex.message ?: "Failed to create transaction."))
                    }
                }
            }

            put("/{id}") {
                val id = call.parameters["id"]
                if (id.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, SimpleResponse(400, "Transaction ID is required."))
                    return@put
                }

                val user = call.authenticatedUser
                val request = try {
                    call.receive<UpdateTransactionRequest>()
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, SimpleResponse(400, "Invalid request payload."))
                    return@put
                }

                val result = transactionService.updateTransaction(id, user.id, request)
                result.onSuccess { tx ->
                    call.respond(
                        HttpStatusCode.OK,
                        TransactionResponse(
                            status = 200,
                            message = "Transaction updated successfully.",
                            transaction = tx
                        )
                    )
                }.onFailure { ex ->
                    when (ex) {
                        is NoSuchElementException ->
                            call.respond(HttpStatusCode.NotFound, SimpleResponse(404, "Transaction not found."))
                        else ->
                            call.respond(HttpStatusCode.BadRequest, SimpleResponse(400, ex.message ?: "Failed to update transaction."))
                    }
                }
            }

            delete("/{id}") {
                val id = call.parameters["id"]
                if (id.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, SimpleResponse(400, "Transaction ID is required."))
                    return@delete
                }

                val user = call.authenticatedUser
                val deleted = transactionService.deleteTransaction(id, user.id)
                if (deleted) {
                    call.respond(HttpStatusCode.OK, SimpleResponse(200, "Transaction deleted successfully."))
                } else {
                    call.respond(HttpStatusCode.NotFound, SimpleResponse(404, "Transaction not found."))
                }
            }
        }
    }
}
