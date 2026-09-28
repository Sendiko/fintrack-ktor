package dev.sendiko.fintrack.plugins

import dev.sendiko.fintrack.models.SimpleResponse
import dev.sendiko.fintrack.models.UserDto
import dev.sendiko.fintrack.services.JwtService
import dev.sendiko.fintrack.services.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*

val UserKey = AttributeKey<UserDto>("AuthenticatedUser")

val ApplicationCall.authenticatedUser: UserDto
    get() = attributes[UserKey]

fun Route.authenticateUser(userService: UserService, jwtService: JwtService, build: Route.() -> Unit): Route {
    val route = createChild(object : RouteSelector() {
        override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int) =
            RouteSelectorEvaluation.Constant
    })
    route.intercept(ApplicationCallPipeline.Plugins) {
        val authHeader = call.request.headers[HttpHeaders.Authorization]
        if (authHeader.isNullOrBlank() || !authHeader.startsWith("Bearer ")) {
            call.respond(HttpStatusCode.Unauthorized, SimpleResponse(401, "Unauthorized."))
            finish()
            return@intercept
        }

        val token = authHeader.removePrefix("Bearer ").trim()
        val decoded = try {
            jwtService.verifier.verify(token)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.Unauthorized, SimpleResponse(401, "Unauthorized."))
            finish()
            return@intercept
        }

        val userId = decoded.subject ?: ""
        val user = userService.validateUserToken(userId, token)
        if (user == null) {
            call.respond(
                HttpStatusCode.Unauthorized,
                SimpleResponse(401, "Your account has logged in from another device.")
            )
            finish()
            return@intercept
        }

        call.attributes.put(UserKey, user)
        proceed()
    }
    route.build()
    return route
}

fun Application.configureSecurity() {
    // JWT verification is handled by the authenticateUser route interceptor
}
