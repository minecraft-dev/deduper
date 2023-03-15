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

@file:JvmName("GitHubSync")

package io.mcdev.deduper.github

import io.mcdev.deduper.database.DuplicateIssue
import io.mcdev.deduper.database.IssueStateUpdate
import io.mcdev.deduper.database.Queries
import io.mcdev.deduper.database.StacktraceTargetUpdate
import io.mcdev.deduper.database.data.CloseableIssue
import io.mcdev.deduper.getLogger
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.sqlobject.kotlin.attach
import org.kohsuke.github.GHIssue
import org.kohsuke.github.GHIssueComment
import org.kohsuke.github.GHIssueState
import org.kohsuke.github.GHPermissionType
import org.kohsuke.github.GitHub

private val logger = getLogger("io.mcdev.deduper.github.GitHubSync")

private const val packagePrefix = "\tat com.demonwav.mcdev"
private val duplicateRegex = Regex("(?i)^\\s*duplicate of\\s+#(\\d+)\\s*$")

private val numberRegex = Regex("\\d+")
private val newLineRegex = Regex("[\r\n]+")

suspend fun scheduleSyncIssues(gh: GitHub, jdbi: Jdbi) {
    // Daily sync and update issues to make sure we don't miss anything, such as a failed webhook
    try {
        do {
            syncIssues(gh, jdbi)
            updateIssues(gh, jdbi)

            val now = ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC)
            val next = now.plusDays(1).with(LocalTime.MIDNIGHT)

            // This will handle cancellations for us
            delay(now.until(next, ChronoUnit.MILLIS))
        } while (true)
    } catch (ignored: CancellationException) {
    }
}

private suspend fun syncIssues(gh: GitHub, jdbi: Jdbi) = coroutineScope {
    logger.info("Syncing issues from GitHub")

    val repo = gh.getRepository("minecraft-dev/mcdev-error-report")

    try {
        jdbi.open().use { handle ->
            val queries = handle.attach<Queries>()

            // Insert entries for all issues
            val issues = mutableListOf<GHIssue>()
            for (issue in repo.getIssues(GHIssueState.ALL)) {
                logger.trace("Checking issue #{}", issue.number)

                if (issue.user.login != "minecraft-dev-autoreporter") {
                    logger.debug("Skipping issue #{} with creator: {}", issue.number, issue.user.login)
                    continue
                }

                val lines = extractStacktrace(issue) ?: continue
                val title = extractAndModifyTitle(issue)

                handle.useTransaction<Exception> {
                    logger.debug("Syncing issue #{}", issue.number)
                    val stacktraceId = queries.upsertStacktrace(lines)
                    logger.trace("Assigning stacktrace_id {} to issue #{}", stacktraceId, issue.number)
                    queries.upsertIssue(issue.number, title, stacktraceId, IssueState.from(issue.state))
                    logger.trace("Issue #{} synced", issue.number)
                }

                issues += issue
            }

            // Find and mark duplicate issues
            val asyncDupes = mutableListOf<Deferred<DuplicateIssue?>>()
            val states = mutableListOf<IssueStateUpdate>()
            val stacktraceTargetUpdates = ConcurrentLinkedQueue<StacktraceTargetUpdate>()

            for (issue in issues) {
                states += IssueStateUpdate(issue.number, IssueState.from(issue.state))

                asyncDupes += async(Dispatchers.IO) {
                    var dupe: DuplicateIssue? = null
                    // Unfortunately we have to look at the comments, rather than the issue events
                    // This is because the marked_as_duplicate event does not contain info about
                    // which issue this is the duplicate of.
                    //
                    // Loop through all comments to make sure a later comment doesn't override the first comment
                    val dbIssue = queries.getIssue(issue.number)
                    for (comment in issue.listComments()) {
                        val num = getDuplicateOfId(comment) ?: continue
                        logger.trace("Marking issue #{} as a duplicate of #{}", issue.number, num)

                        dupe = DuplicateIssue(issueId = issue.number, duplicateOfId = num)

                        dbIssue?.let {
                            stacktraceTargetUpdates.add(
                                StacktraceTargetUpdate(
                                    it.stacktraceId,
                                    num,
                                    comment.createdAt.toInstant(),
                                ),
                            )
                        }
                    }

                    dupe
                }
            }

            logger.debug("Executing batch state update for {} issues", states.size)
            queries.updateIssueStates(states)

            val dupes = asyncDupes.mapNotNullTo(ArrayList()) { it.await() }

            logger.debug("Executing batch duplicates update for {} issues", dupes.size)
            queries.updateDuplicateIssues(dupes)
            // sort duplicate updates by date to make sure we maintain any later comments which override the targets
            // of previous comments
            queries.setManyIssueTargets(stacktraceTargetUpdates.sortedWith(nullsFirst(compareBy { it.createdAt })))
        }

        logger.info("GitHub sync complete")
    } catch (e: Throwable) {
        logger.error("Error syncing GitHub issues", e)
    }
}

private fun updateIssues(gh: GitHub, jdbi: Jdbi) {
    val repo = gh.getRepository("minecraft-dev/mcdev-error-report")

    try {
        jdbi.open().use { handle ->
            val queries = handle.attach<Queries>()

            val closeableIssues = queries.findCloseableIssues()
            val actuallyClosedIssues = mutableListOf<CloseableIssue>()
            logger.info("Found {} duplicate issues to close", closeableIssues.size)
            for (closeableIssue in closeableIssues) {
                try {
                    val issue = repo.getIssue(closeableIssue.issueId)
                    if (queries.closeIfDuplicateIssue(issue, closeableIssue.stacktraceId)) {
                        actuallyClosedIssues += closeableIssue
                    }
                } catch (e: Throwable) {
                    logger.error("Failure in checking closeable issue #{}", closeableIssue.issueId, e)
                }
            }

            // we should receive the webhook notification telling us the issue is closed, but just to be safe, mark it
            // here while we have the chance
            queries.updateIssueStates(actuallyClosedIssues.map { IssueStateUpdate(it.issueId, IssueState.closed) })
        }
    } catch (e: Throwable) {
        logger.error("Failure finding closeable issues", e)
    }
}

fun Queries.closeIfDuplicateIssue(issue: GHIssue, stacktraceId: Int): Boolean {
    try {
        // Check if it's a duplicate
        val dupeId = findDuplicateIssue(stacktraceId) ?: return false
        logger.info("Issue #{} is a duplicate of #{}", issue.number, dupeId)
        // if so, mark it and close
        issue.comment("Duplicate of #$dupeId")
        issue.close()
        logger.info("Issue #{} marked as duplicate of #{} and closed successfully", issue.number, dupeId)
        return true
    } catch (e: Throwable) {
        val msg = "Failure during duplicate issue check for issue #{}, with stacktrace {}"
        logger.error(msg, issue.number, stacktraceId, e)
        return false
    }
}

@Suppress("EnumEntryName")
enum class IssueState {
    open, closed;

    companion object {
        fun from(state: GHIssueState): IssueState {
            return when (state) {
                GHIssueState.OPEN -> open
                GHIssueState.CLOSED -> closed
                GHIssueState.ALL -> error("Unexpected state: ALL")
            }
        }
    }
}

fun extractAndModifyTitle(issue: GHIssue): String {
    if (issue.title != "[auto-generated] Exception in plugin") {
        // if the title name has already been modified, keep it
        // this allows us to modify the title without it getting overwritten
        return issue.title
    }
    // TODO: in the future have the title set correctly in the first place
    val body = issue.body
    var title = body.substringAfter("\n```\n").substringBefore("\n")
    if (title.isEmpty()) {
        title = "[auto-generated] Exception in plugin"
    } else if (title.length > 255) {
        title = title.substring(0, 252) + "..."
    }
    if (title != issue.title) {
        issue.title = title
    }
    return title
}

fun extractStacktrace(issue: GHIssue): List<String>? {
    val body = issue.body
    val first = body.indexOf("\n```\n", startIndex = 0) + 5
    val second = body.indexOf("\n```\n", startIndex = first)
    if (first == 4 || second == -1) {
        logger.debug("No stacktrace found for issue #{}", issue.number)
        return null
    }

    val stacktrace = body.substring(first, second)
        .replace(numberRegex, "")
        .replace(newLineRegex, "\n")

    return stacktrace.lineSequence()
        .filter { it.startsWith(packagePrefix) }
        .map { it.trim() }
        .map { it.removePrefix("at ") }
        .toList()
}

fun getDuplicateOfId(comment: GHIssueComment): Int? {
    try {
        val match = duplicateRegex.matchEntire(comment.body) ?: return null
        val numString = match.groups[1]?.value ?: return null
        val num = numString.toIntOrNull() ?: return null

        // Is the user allowed to mark as duplicate?
        when (comment.parent.repository.getPermission(comment.user)) {
            GHPermissionType.ADMIN, GHPermissionType.WRITE -> {}
            else -> return null
        }

        return num
    } catch (e: Throwable) {
        logger.error("Failed to determine if comment is a 'Duplicate of' comment: {}", comment.url, e)
        return null
    }
}
