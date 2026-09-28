package dev.sendiko.fintrack.routes

import dev.sendiko.fintrack.plugins.authenticateUser
import dev.sendiko.fintrack.plugins.authenticatedUser
import dev.sendiko.fintrack.services.AnalysisService
import dev.sendiko.fintrack.services.JwtService
import dev.sendiko.fintrack.services.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.analysisRoutes(
    analysisService: AnalysisService,
    userService: UserService,
    jwtService: JwtService
) {
    authenticateUser(userService, jwtService) {
        route("/analysis") {
            get("/spending") {
                val user = call.authenticatedUser
                val range = call.request.queryParameters["range"]
                val startDate = call.request.queryParameters["startDate"]
                val endDate = call.request.queryParameters["endDate"]

                val analysis = analysisService.getSpendingAnalysis(user.id, range, startDate, endDate)
                call.respond(HttpStatusCode.OK, analysis)
            }
        }
    }
}
