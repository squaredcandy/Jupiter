package com.squaredcandy

import com.ryanharter.ktor.moshi.moshi
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.features.*
import org.slf4j.event.*
import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.auth.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

object Users : Table() {
    val id = uuid("id")
    val name = varchar("name", 50)
    val password = varchar("password", 50)
    override val primaryKey: PrimaryKey = PrimaryKey(id)
}

data class User(
    val id: String,
    val name: String,
    val password: String
)

@Suppress("unused") // Referenced in application.conf
@JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    install(DefaultHeaders) {
        header("X-Engine", "Ktor") // will send this header with each response
    }

    install(ContentNegotiation) {
        moshi()
    }

//    install(WebSockets) {
//        pingPeriod = Duration.ofSeconds(15)
//        timeout = Duration.ofSeconds(15)
//        maxFrameSize = Long.MAX_VALUE
//        masking = false
//    }

    install(Authentication) {
        basic("myBasicAuth") {
            realm = "Ktor Server"
            validate { if (it.name == "test" && it.password == "password") UserIdPrincipal(it.name) else null }
        }
    }

    Database.connect(hikari())
    transaction {
        SchemaUtils.create(Users)
    }

    val client = HttpClient(Apache) {
    }

    routing {
        get("/") {
            val id = transaction {
                Users.insert {
                    it[id] = UUID.randomUUID()
                    it[name] = "username"
                    it[password] = "password"
                } get Users.id
            }
            call.respondText("Hello new user $id", contentType = ContentType.Text.Plain)
        }

        get("/getall") {
            val users = transaction {
                Users.selectAll().map {
                    User(it[Users.id].toString(), it[Users.name], it[Users.password])
                }
            }
            val count = users.count()
            call.respondText("There are $count users in the database", contentType = ContentType.Text.Plain)
        }

//        webSocket("/myws/echo") {
//            send(Frame.Text("Hi from server"))
//            while (true) {
//                val frame = incoming.receive()
//                if (frame is Frame.Text) {
//                    send(Frame.Text("Client said: " + frame.readText()))
//                }
//            }
//        }

        authenticate("myBasicAuth") {
            get("/protected/route/basic") {
                val principal = call.principal<UserIdPrincipal>()!!
                call.respondText("Hello ${principal.name}")
            }
        }
    }
}

private fun hikari(): HikariDataSource {
    val config = HikariConfig().apply {
        driverClassName = "org.h2.Driver"
        jdbcUrl = "jdbc:h2:mem:test"
        maximumPoolSize = 3
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        validate()
    }
    return HikariDataSource(config)
}