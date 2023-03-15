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

package io.mcdev.deduper

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.outputStream
import kotlin.system.exitProcess
import kotlinx.serialization.Serializable
import kotlinx.serialization.hocon.Hocon
import kotlinx.serialization.hocon.decodeFromConfig

@JvmRecord
@Serializable
data class DeduperConfig(
    val server: ServerConfig,
    val database: DatabaseConfig,
    val github: GitHubApiConfig,
) {

    companion object {
        fun readConfig(): DeduperConfig {
            val file = Path("application.conf")
            if (!file.exists()) {
                writeDefaultConfigAndQuit(file)
            }

            val configFile = ConfigFactory.parseFile(file.toFile(), ConfigParseOptions.defaults().setAllowMissing(true))

            val configToDecode = if (configFile.isEmpty) {
                System.err.println("Empty config file: $file")
                exitProcess(0)
            } else {
                configFile
            }

            return Hocon.decodeFromConfig(configToDecode)
        }

        private fun writeDefaultConfigAndQuit(file: Path): Nothing {
            DeduperConfig::class.java.getResourceAsStream("/application.conf").use { input ->
                input ?: error("application.conf not found")
                file.outputStream().use { out ->
                    input.copyTo(out)
                }
            }
            exitProcess(0)
        }
    }
}

@JvmRecord
@Serializable
data class ServerConfig(val host: String, val port: Int)

@JvmRecord
@Serializable
data class DatabaseConfig(
    val username: String,
    val password: String,
    val host: String,
    val port: Int = 5432,
    val database: String,
)

@JvmRecord
@Serializable
data class GitHubApiConfig(
    val privateKeyFile: String,
    val appId: String,
    val organization: String,
    val webhookSecret: String,
)
