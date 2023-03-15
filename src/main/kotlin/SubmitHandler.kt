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

import io.ktor.http.ContentType
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.call
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveMultipart
import io.mcdev.deduper.submission.Submission
import io.mcdev.deduper.submission.SubmissionAttachment
import io.mcdev.deduper.submission.SubmissionMetadata
import io.mcdev.deduper.submission.SubmissionStacktrace
import io.mcdev.deduper.submission.submitErrorReport
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jdbi.v3.core.Jdbi
import org.kohsuke.github.GitHub

class SubmitHandler(private val gh: GitHub, private val jdbi: Jdbi) {
    suspend fun CallContext.handleSubmit() {
        val contentType = call.request.contentType()
        if (!contentType.match(ContentType.MultiPart.FormData)) {
            throw BadRequestException("Request is not ${ContentType.MultiPart.FormData}")
        }
        val parts = mutableMapOf<String, PartData>()

        call.receiveMultipart().forEachPart { part ->
            val name = part.name ?: throw BadRequestException("Unnamed part")

            if (parts.containsKey(name)) {
                throw BadRequestException("Duplicate part name: $name")
            }

            parts[name] = part
        }

        val metadataPart = parts.remove(SubmissionMetadata.PartName)
            ?: throw BadRequestException("No ${SubmissionMetadata.PartName} part found")
        PartVerifier.verify(metadataPart, ContentType.Application.Json)
        val metadata = Json.decodeFromString<SubmissionMetadata>(metadataPart.value)

        val stacktracePart = parts.remove(SubmissionStacktrace.PartName)
            ?: throw BadRequestException("No ${SubmissionStacktrace.PartName} part found")
        PartVerifier.verify(stacktracePart, ContentType.Text.Plain)
        val stacktrace = SubmissionStacktrace(stacktracePart.value)

        val attachments = mutableListOf<SubmissionAttachment>()

        val attachmentNames = parts.keys.map { it.substringBefore('-') }.distinct()
        for (attachmentName in attachmentNames) {
            val bodyPart = parts["$attachmentName-${SubmissionAttachment.Body}"]
                ?: throw BadRequestException("Unknown attachment part name: $attachmentName")
            val displayTextPart = parts["$attachmentName-${SubmissionAttachment.DisplayText}"]

            PartVerifier.verify(bodyPart, ContentType.Text.Plain)
            val displayText = displayTextPart?.let { part ->
                PartVerifier.verify(part, ContentType.Text.Plain)
                part.value
            }

            attachments += SubmissionAttachment(displayText, bodyPart.value)
        }

        submitErrorReport(Submission(metadata, stacktrace, attachments))
    }
}
