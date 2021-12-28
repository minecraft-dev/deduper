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

@file:JvmName("Utils")

package io.mcdev.deduper

import ch.qos.logback.classic.LoggerContext
import java.io.Reader
import org.kohsuke.github.GHEventPayload
import org.kohsuke.github.GitHub
import org.slf4j.Logger
import org.slf4j.LoggerFactory

inline fun <reified T : GHEventPayload> GitHub.parseEventPayload(reader: Reader): T {
    return parseEventPayload(reader, T::class.java)
}

fun getLogger(name: String): Logger = LoggerFactory.getLogger(name)
inline fun <reified T> getLogger(): Logger = LoggerFactory.getLogger(T::class.java)

fun shutdownLogger() = (LoggerFactory.getILoggerFactory() as LoggerContext).stop()
