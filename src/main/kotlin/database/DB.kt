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

@file:JvmName("DB")

package io.mcdev.deduper.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.mcdev.deduper.DatabaseConfig
import io.mcdev.deduper.getLogger
import javax.sql.DataSource
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.kotlin.KotlinPlugin
import org.jdbi.v3.postgres.PostgresPlugin
import org.jdbi.v3.sqlobject.kotlin.KotlinSqlObjectPlugin
import org.jdbi.v3.sqlobject.kotlin.attach
import org.postgresql.ds.PGSimpleDataSource

private val logger = getLogger("io.mcdev.deduper.database.DB")

fun DatabaseConfig.openDbConnection(): HikariDataSource {
    logger.info("Initialize database connection")

    val config = HikariConfig()
    config.username = username
    config.password = password
    config.dataSourceClassName = PGSimpleDataSource::class.java.name
    config.addDataSourceProperty("databaseName", database)
    config.addDataSourceProperty("serverName", host)
    config.addDataSourceProperty("portNumber", port)

    config.transactionIsolation = "TRANSACTION_READ_COMMITTED"

    return HikariDataSource(config)
}

fun DataSource.configureJdbi(): Jdbi {
    val jdbi = Jdbi.create(this)
    jdbi.installPlugin(PostgresPlugin())
    jdbi.installPlugin(KotlinPlugin())
    jdbi.installPlugin(KotlinSqlObjectPlugin())

    return jdbi
}

fun Jdbi.initialize() {
    open().use { handle ->
        val tables = handle.attach<Tables>()

        tables.initializeStacktraces()
        tables.initializeIssues()
        tables.initializeStacktraceTargets()
    }
}
