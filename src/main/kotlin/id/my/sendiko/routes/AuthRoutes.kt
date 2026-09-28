package id.my.sendiko.routes

import id.my.sendiko.models.*
import id.my.sendiko.services.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoutes(userService: UserService) {
    post("/register") {
        val request = try {
            call.receive<RegisterRequest>()
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, SimpleResponse(400, "Invalid request payload."))
            return@post
        }

        val result = userService.register(request)
        result.onSuccess { user ->
            call.respond(
                HttpStatusCode.Created,
                RegisterResponse(
                    status = 201,
                    message = "User registered successfully.",
                    user = user
                )
            )
        }.onFailure { ex ->
            val msg = ex.message ?: "Registration failed."
            when {
                msg.contains("Email is required") ->
                    call.respond(HttpStatusCode.BadRequest, SimpleResponse(400, "Email is required."))
                msg.contains("already registered") ->
                    call.respond(HttpStatusCode.BadRequest, SimpleResponse(400, "Email is already registered."))
                else ->
                    call.respond(HttpStatusCode.BadRequest, SimpleResponse(400, msg))
            }
        }
    }

    post("/login") {
        val request = try {
            call.receive<LoginRequest>()
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, SimpleResponse(400, "Invalid request payload."))
            return@post
        }

        val result = userService.login(request)
        result.onSuccess { user ->
            call.respond(
                HttpStatusCode.OK,
                LoginResponse(
                    status = 200,
                    message = "User logged in successfully.",
                    user = user
                )
            )
        }.onFailure { ex ->
            when (ex) {
                is NoSuchElementException ->
                    call.respond(HttpStatusCode.NotFound, SimpleResponse(404, "User not found."))
                is SecurityException ->
                    call.respond(HttpStatusCode.Unauthorized, SimpleResponse(401, "Invalid password."))
                else ->
                    call.respond(HttpStatusCode.BadRequest, SimpleResponse(400, ex.message ?: "Login failed."))
            }
        }
    }

    put("/user/change-password/{id}") {
        val id = call.parameters["id"]
        if (id.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, SimpleResponse(400, "User ID is required."))
            return@put
        }

        val request = try {
            call.receive<ChangePasswordRequest>()
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, SimpleResponse(400, "Invalid request payload."))
            return@put
        }

        if (request.password.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, SimpleResponse(400, "Password is required."))
            return@put
        }

        val success = userService.changePassword(id, request.password)
        if (success) {
            call.respond(HttpStatusCode.OK, SimpleResponse(200, "Password reset successfully."))
        } else {
            call.respond(HttpStatusCode.NotFound, SimpleResponse(404, "User not found."))
        }
    }
}
