package id.my.sendiko.routes

import id.my.sendiko.models.ErrorResponse
import id.my.sendiko.models.ReceiptExtractRequest
import id.my.sendiko.models.ReceiptResponse
import id.my.sendiko.models.SimpleResponse
import id.my.sendiko.plugins.authenticateUser
import id.my.sendiko.services.JwtService
import id.my.sendiko.services.ReceiptService
import id.my.sendiko.services.UserService
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
