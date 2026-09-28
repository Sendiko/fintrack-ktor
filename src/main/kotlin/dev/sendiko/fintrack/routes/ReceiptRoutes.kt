package dev.sendiko.fintrack.routes

import dev.sendiko.fintrack.models.ErrorResponse
import dev.sendiko.fintrack.models.ReceiptExtractRequest
import dev.sendiko.fintrack.models.ReceiptResponse
import dev.sendiko.fintrack.models.SimpleResponse
import dev.sendiko.fintrack.plugins.authenticateUser
import dev.sendiko.fintrack.services.JwtService
import dev.sendiko.fintrack.services.ReceiptService
import dev.sendiko.fintrack.services.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.receiptRoutes(
    receiptService: ReceiptService,
    userService: UserService,
    jwtService: JwtService
) {
    authenticateUser(userService, jwtService) {
        post("/extract") {
            call.handleExtract(receiptService)
        }
        get("/extract") {
            call.handleExtract(receiptService)
        }
    }
}

private suspend fun ApplicationCall.handleExtract(receiptService: ReceiptService) {
    val request = try {
        receive<ReceiptExtractRequest>()
    } catch (e: Exception) {
        respond(HttpStatusCode.BadRequest, SimpleResponse(400, "Receipt text is required."))
        return
    }

    val text = request.receiptText?.trim()
    if (text.isNullOrBlank()) {
        respond(HttpStatusCode.BadRequest, SimpleResponse(400, "Receipt text is required."))
        return
    }

    val result = receiptService.extractReceipt(text)
    result.onSuccess { data ->
        respond(
            HttpStatusCode.OK,
            ReceiptResponse(
                status = 200,
                message = "Receipt information extracted successfully.",
                data = data
            )
        )
    }.onFailure { ex ->
        respond(
            HttpStatusCode.InternalServerError,
            ErrorResponse(
                status = 500,
                message = "Server error.",
                error = ex.message
            )
        )
    }
}
