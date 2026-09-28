package id.my.sendiko.plugins

import id.my.sendiko.routes.*
import id.my.sendiko.services.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting(
    userService: UserService,
    walletService: WalletService,
    categoryService: CategoryService,
    transactionService: TransactionService,
    receiptService: ReceiptService,
    analysisService: AnalysisService,
    jwtService: JwtService
) {
    routing {
        get("/") {
            call.respondText("FinTrack API is running")
        }

        authRoutes(userService)
        userRoutes(userService, jwtService)
        walletRoutes(walletService, userService, jwtService)
        categoryRoutes(categoryService, userService, jwtService)
        transactionRoutes(transactionService, userService, jwtService)
        receiptRoutes(receiptService, userService, jwtService)
        analysisRoutes(analysisService, userService, jwtService)
    }
}
