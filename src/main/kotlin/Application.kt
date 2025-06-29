package id.my.sendiko

import id.my.sendiko.plugins.configureDatabases
import id.my.sendiko.plugins.configureRouting
import id.my.sendiko.plugins.configureSecurity
import id.my.sendiko.plugins.configureSerialization
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSerialization()
    configureDatabases()
    configureSecurity()
    configureRouting()
}
