/*
 * Copyright (c) 2023 minecraft-dev
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3 only.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

@file:JvmName("Routing")

package io.mcdev.deduper

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.util.pipeline.PipelineContext
import io.mcdev.deduper.github.WebhookHandler
import io.mcdev.deduper.github.verifyWebhook
import kotlinx.serialization.Serializable
import org.jdbi.v3.core.Jdbi
import org.kohsuke.github.GitHub

typealias CallContext = PipelineContext<Unit, ApplicationCall>

fun Application.configureRouting(gh: GitHub, jdbi: Jdbi, config: DeduperConfig) {
    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, MessageResponse(cause.message))
        }
        exception<AuthenticationException> { call, _ ->
            call.respond(HttpStatusCode.Unauthorized)
        }
        exception<AuthorizationException> { call, _ ->
            call.respond(HttpStatusCode.Forbidden)
        }
    }

    val submitHandler = SubmitHandler(gh, jdbi)
    val webhookHandler = WebhookHandler(gh, jdbi)

    routing {
        route("/api/v1") {
            post("/submit") {
                submitHandler.apply {
                    handleSubmit()
                }
            }

            // GitHub webhook handling
            post("/webhook") {
                val requestContent = verifyWebhook(config.github.webhookSecret)

                webhookHandler.apply {
                    handleWebhook(requestContent)
                }
            }
        }
    }
}

@JvmRecord
@Serializable
data class MessageResponse(val message: String)

class BadRequestException(override val message: String) : Exception(message)
class AuthenticationException : Exception()
class AuthorizationException : Exception()
