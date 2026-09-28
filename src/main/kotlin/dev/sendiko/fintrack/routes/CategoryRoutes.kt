package dev.sendiko.fintrack.routes

import dev.sendiko.fintrack.models.*
import dev.sendiko.fintrack.plugins.authenticateUser
import dev.sendiko.fintrack.plugins.authenticatedUser
import dev.sendiko.fintrack.services.CategoryService
import dev.sendiko.fintrack.services.JwtService
import dev.sendiko.fintrack.services.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.categoryRoutes(
    categoryService: CategoryService,
    userService: UserService,
    jwtService: JwtService
) {
    authenticateUser(userService, jwtService) {
        route("/categories") {
            get {
                val user = call.authenticatedUser
                val categories = categoryService.listCategories(user.id)
                call.respond(
                    HttpStatusCode.OK,
                    CategoriesResponse(
                        status = 200,
                        message = "Categories sent successfully.",
                        categories = categories
                    )
                )
            }

            get("/{id}") {
                val id = call.parameters["id"]
                if (id.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, SimpleResponse(400, "Category ID is required."))
                    return@get
                }

                val user = call.authenticatedUser
                val category = categoryService.getCategory(id, user.id)
                if (category == null) {
                    call.respond(HttpStatusCode.NotFound, SimpleResponse(404, "Category not found."))
                } else {
                    call.respond(
                        HttpStatusCode.OK,
                        CategoryResponse(
                            status = 200,
                            message = "Category sent successfully.",
                            category = category
                        )
                    )
                }
            }

            post {
                val user = call.authenticatedUser
                val request = try {
                    call.receive<CreateCategoryRequest>()
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, SimpleResponse(400, "Invalid request payload."))
                    return@post
                }

                val category = categoryService.createCategory(user.id, request)
                call.respond(
                    HttpStatusCode.Created,
                    CategoryResponse(
                        status = 201,
                        message = "Category registered successfully.",
                        category = category
                    )
                )
            }

            put("/{id}") {
                val id = call.parameters["id"]
                if (id.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, SimpleResponse(400, "Category ID is required."))
                    return@put
                }

                val user = call.authenticatedUser
                val request = try {
                    call.receive<UpdateCategoryRequest>()
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, SimpleResponse(400, "Invalid request payload."))
                    return@put
                }

                val updated = categoryService.updateCategory(id, user.id, request)
                if (updated == null) {
                    call.respond(HttpStatusCode.NotFound, SimpleResponse(404, "Category not found."))
                } else {
                    call.respond(
                        HttpStatusCode.OK,
                        CategoryResponse(
                            status = 200,
                            message = "Category updated successfully.",
                            category = updated
                        )
                    )
                }
            }

            delete("/{id}") {
                val id = call.parameters["id"]
                if (id.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, SimpleResponse(400, "Category ID is required."))
                    return@delete
                }

                val user = call.authenticatedUser
                val deleted = categoryService.deleteCategory(id, user.id)
                if (deleted) {
                    call.respond(HttpStatusCode.OK, SimpleResponse(200, "Category deleted successfully."))
                } else {
                    call.respond(HttpStatusCode.NotFound, SimpleResponse(404, "Category not found."))
                }
            }
        }
    }
}
