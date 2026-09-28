package id.my.sendiko.routes

import id.my.sendiko.plugins.authenticateUser
import id.my.sendiko.plugins.authenticatedUser
import id.my.sendiko.services.AnalysisService
import id.my.sendiko.services.JwtService
import id.my.sendiko.services.UserService
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
