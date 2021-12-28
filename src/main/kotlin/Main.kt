/*
 * Copyright (c) 2021 minecraft-dev
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

@file:JvmName("Main")

package io.mcdev.deduper

import io.ktor.application.ApplicationStarted
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.ForwardedHeaderSupport
import io.ktor.features.XForwardedHeaderSupport
import io.ktor.serialization.json
import io.ktor.server.engine.addShutdownHook
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.mcdev.deduper.database.configureJdbi
import io.mcdev.deduper.database.initialize
import io.mcdev.deduper.database.openDbConnection
import io.mcdev.deduper.github.initializeApp
import io.mcdev.deduper.github.scheduleSyncIssues
import kotlinx.coroutines.launch

private val logger = getLogger("io.mcdev.deduper.Main")

fun main() {
    logger.info("Reading config")
    val config = DeduperConfig.readConfig()

    val gh = config.github.initializeApp()

    try {
        config.database.openDbConnection().use { db ->
            val jdbi = db.configureJdbi()
            jdbi.initialize()

            logger.info("Starting webserver")

            val app = embeddedServer(Netty, port = config.server.port, host = config.server.host) {
                install(ForwardedHeaderSupport)
                install(XForwardedHeaderSupport)
                install(CallLogging)

                install(ContentNegotiation) {
                    json()
                }

                configureRouting(gh, jdbi, config)
            }

            app.addShutdownHook {
                runCatching {
                    db.close()
                }
                shutdownLogger()
            }

            app.environment.monitor.subscribe(ApplicationStarted) {
                it.launch {
                    scheduleSyncIssues(gh, jdbi)
                }
            }

            app.start(wait = true)
        }
    } finally {
        shutdownLogger()
    }
}
