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

@file:JvmName("SubmitErrorReport")

package io.mcdev.deduper.submission

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.util.pipeline.PipelineContext
import io.mcdev.deduper.MessageResponse
import kotlinx.serialization.Serializable

suspend fun PipelineContext<Unit, ApplicationCall>.submitErrorReport(submission: Submission) {
    call.respond(HttpStatusCode.Created, MessageResponse("Success"))
}

@JvmRecord
@Serializable
data class SubmissionMetadata(
    val pluginName: String,
    val pluginVersion: String,
    val osName: String,
    val javaVersion: String,
    val javaVmVendor: String,
    val isEap: Boolean,
    val ideaBuild: String,
    val ideaVersion: String,
    val lastAction: String?
) {
    companion object {
        const val PartName = "metadata"
    }
}

@JvmInline
value class SubmissionStacktrace(val text: String) {
    companion object {
        const val PartName = "stacktrace"
    }
}

@JvmRecord
data class SubmissionAttachment(val displayText: String?, val body: String) {
    companion object {
        const val DisplayText = "displayText"
        const val Body = "body"
    }
}

@JvmRecord
data class Submission(
    val metadata: SubmissionMetadata,
    val stacktrace: SubmissionStacktrace,
    val attachments: List<SubmissionAttachment>
)
