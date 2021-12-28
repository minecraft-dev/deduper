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

package io.mcdev.deduper.github

import io.ktor.application.call
import io.ktor.features.BadRequestException
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.mcdev.deduper.CallContext
import io.mcdev.deduper.database.DuplicateIssue
import io.mcdev.deduper.database.IssueStateUpdate
import io.mcdev.deduper.database.Queries
import io.mcdev.deduper.getLogger
import io.mcdev.deduper.parseEventPayload
import java.io.Reader
import java.io.StringReader
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.sqlobject.kotlin.attach
import org.kohsuke.github.GHEventPayload
import org.kohsuke.github.GitHub

class WebhookHandler(private val gitHub: GitHub, private val jdbi: Jdbi) {
    suspend fun CallContext.handleWebhook(requestContent: String) {
        val reader = StringReader(requestContent)

        val event = call.request.headers["X-GitHub-Event"]
            ?: throw BadRequestException("No X-GitHub-Event header")

        call.respond(HttpStatusCode.OK)

        // Don't block the response while the rest of the handler executes
        handleEvent(event, reader)
    }

    private suspend fun handleEvent(event: String, reader: Reader) = coroutineScope {
        launch {
            when (event) {
                "issues" -> handleIssue(gitHub.parseEventPayload(reader))
                "issue_comment" -> handleIssueComment(gitHub.parseEventPayload(reader))
            }
        }
    }

    private fun handleIssue(event: GHEventPayload.Issue) {
        when (event.action) {
            "opened" -> handleIssueOpened(event)
            "closed" -> handleIssueClosed(event)
        }
    }

    private fun handleIssueOpened(event: GHEventPayload.Issue) {
        // We're only interested in keeping issues opened by minecraft-dev-autoreporter in sync
        if (event.issue.user.login != "minecraft-dev-autoreporter") {
            return
        }

        logger.info("Syncing new issue: #{}", event.issue.number)
        val lines = extractStacktrace(event.issue) ?: return

        try {
            jdbi.open().use { handler ->
                val queries = handler.attach<Queries>()

                val stacktraceId = queries.upsertStacktrace(lines)
                queries.upsertIssue(event.issue.number, stacktraceId, IssueState.open)

                queries.closeIfDuplicateIssue(event.issue, stacktraceId)
            }
        } catch (e: Throwable) {
            logger.error("Error handling issue opened webhook for issue #{}", event.issue.number, e)
        }
    }

    private fun handleIssueClosed(event: GHEventPayload.Issue) {
        logger.info("Marking issue #{} as closed", event.issue.number)
        try {
            jdbi.open().use { handler ->
                val queries = handler.attach<Queries>()
                queries.updateIssueStates(listOf(IssueStateUpdate(event.issue.number, IssueState.closed)))
            }
        } catch (e: Throwable) {
            logger.error("Error marking issue #{} as closed", event.issue.number, e)
        }
    }

    private fun handleIssueComment(event: GHEventPayload.IssueComment) {
        val duplicateOfId = getDuplicateOfId(event.comment) ?: return
        val dupe = DuplicateIssue(event.issue.number, duplicateOfId)

        logger.info("Marking #{} as a duplicate of #{}", event.issue.number, duplicateOfId)

        try {
            jdbi.open().use { handler ->
                val queries = handler.attach<Queries>()

                queries.updateDuplicateIssues(listOf(dupe))

                val issue = queries.getIssue(event.issue.number) ?: return@use
                queries.setIssueTarget(issue.stacktraceId, duplicateOfId)
                logger.info("Set issue #{} as the target for stacktrace_id {}", duplicateOfId, issue.stacktraceId)
            }
        } catch (e: Throwable) {
            logger.error("Error handling 'Duplicate of' issue comment {}", event.comment.url, e)
        }
    }

    companion object {
        private val logger = getLogger<WebhookHandler>()
    }
}
