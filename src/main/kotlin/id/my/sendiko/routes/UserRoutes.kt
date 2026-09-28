package id.my.sendiko.routes

import id.my.sendiko.models.SimpleResponse
import id.my.sendiko.models.UserResponse
import id.my.sendiko.models.UserUpdateRequest
import id.my.sendiko.plugins.authenticateUser
import id.my.sendiko.services.JwtService
import id.my.sendiko.services.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.userRoutes(userService: UserService, jwtService: JwtService) {
    // Public: Find user by email query
    get("/users") {
        val email = call.request.queryParameters["email"]
        if (email.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, SimpleResponse(400, "Email query parameter is required."))
            return@get
        }

        val user = userService.findByEmail(email)
        if (user == null) {
            call.respond(HttpStatusCode.NotFound, SimpleResponse(404, "User not found."))
        } else {
            call.respond(HttpStatusCode.OK, UserResponse(200, "User fetched successfully.", user))
        }
    }

    // Protected user management routes
    authenticateUser(userService, jwtService) {
        get("/users/{id}") {
            val id = call.parameters["id"]
            if (id.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, SimpleResponse(400, "User ID is required."))
                return@get
            }

            val user = userService.findById(id, includeRelations = true)
            if (user == null) {
                call.respond(HttpStatusCode.NotFound, SimpleResponse(404, "User not found."))
            } else {
                call.respond(HttpStatusCode.OK, UserResponse(200, "User sent successfully.", user))
            }
        }

        put("/users/{id}") {
            val id = call.parameters["id"]
            if (id.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, SimpleResponse(400, "User ID is required."))
                return@put
            }

            val request = try {
                call.receive<UserUpdateRequest>()
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, SimpleResponse(400, "Invalid request payload."))
                return@put
            }

            val updated = userService.update(id, request)
            if (updated == null) {
                call.respond(HttpStatusCode.NotFound, SimpleResponse(404, "User not found."))
            } else {
                call.respond(HttpStatusCode.OK, UserResponse(200, "User updated successfully.", updated))
            }
        }

        delete("/users/{id}") {
            val id = call.parameters["id"]
            if (id.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, SimpleResponse(400, "User ID is required."))
                return@delete
            }

            val success = userService.delete(id)
            if (success) {
                call.respond(HttpStatusCode.OK, SimpleResponse(200, "User deleted successfully."))
            } else {
                call.respond(HttpStatusCode.NotFound, SimpleResponse(404, "User not found."))
            }
        }

        get("/users/{id}/statistics") {
            val id = call.parameters["id"]
            if (id.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, SimpleResponse(400, "User ID is required."))
                return@get
            }

            val stats = userService.getStatistics(id)
            if (stats == null) {
                call.respond(HttpStatusCode.NotFound, SimpleResponse(404, "User not found."))
            } else {
                call.respond(HttpStatusCode.OK, stats)
            }
        }
    }
}
