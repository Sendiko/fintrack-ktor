package id.my.sendiko.routes

import id.my.sendiko.models.*
import id.my.sendiko.plugins.authenticateUser
import id.my.sendiko.plugins.authenticatedUser
import id.my.sendiko.services.JwtService
import id.my.sendiko.services.UserService
import id.my.sendiko.services.WalletService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.walletRoutes(
    walletService: WalletService,
    userService: UserService,
    jwtService: JwtService
) {
    authenticateUser(userService, jwtService) {
        route("/wallets") {
            get {
                val user = call.authenticatedUser
                val wallets = walletService.listWallets(user.id)
                call.respond(
                    HttpStatusCode.OK,
                    WalletsResponse(
                        status = 200,
                        message = "Wallets sent successfully.",
                        wallets = wallets
                    )
                )
            }

            get("/{id}") {
                val id = call.parameters["id"]
                if (id.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, SimpleResponse(400, "Wallet ID is required."))
                    return@get
                }

                val user = call.authenticatedUser
                val wallet = walletService.getWallet(id, user.id)
                if (wallet == null) {
                    call.respond(HttpStatusCode.NotFound, SimpleResponse(404, "Wallet not found."))
                } else {
                    call.respond(
                        HttpStatusCode.OK,
                        WalletResponse(
                            status = 200,
                            message = "Wallet sent successfully.",
                            wallet = wallet
                        )
                    )
                }
            }

            post {
                val user = call.authenticatedUser
                val request = try {
                    call.receive<CreateWalletRequest>()
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, SimpleResponse(400, "Invalid request payload."))
                    return@post
                }

                val wallet = walletService.createWallet(user.id, request)
                call.respond(
                    HttpStatusCode.Created,
                    WalletResponse(
                        status = 201,
                        message = "Wallet registered successfully.",
                        wallet = wallet
                    )
                )
            }

            put("/{id}") {
                val id = call.parameters["id"]
                if (id.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, SimpleResponse(400, "Wallet ID is required."))
                    return@put
                }

                val user = call.authenticatedUser
                val request = try {
                    call.receive<UpdateWalletRequest>()
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, SimpleResponse(400, "Invalid request payload."))
                    return@put
                }

                val updated = walletService.updateWallet(id, user.id, request)
                if (updated == null) {
                    call.respond(HttpStatusCode.NotFound, SimpleResponse(404, "Wallet not found."))
                } else {
                    call.respond(
                        HttpStatusCode.OK,
                        WalletResponse(
                            status = 200,
                            message = "Wallet updated successfully.",
                            wallet = updated
                        )
                    )
                }
            }

            delete("/{id}") {
                val id = call.parameters["id"]
                if (id.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, SimpleResponse(400, "Wallet ID is required."))
                    return@delete
                }

                val user = call.authenticatedUser
                val deleted = walletService.deleteWallet(id, user.id)
                if (deleted) {
                    call.respond(HttpStatusCode.OK, SimpleResponse(200, "Wallet deleted successfully."))
                } else {
                    call.respond(HttpStatusCode.NotFound, SimpleResponse(404, "Wallet not found."))
                }
            }
        }
    }
}
