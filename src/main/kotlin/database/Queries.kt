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

package io.mcdev.deduper.database

import io.mcdev.deduper.database.data.CloseableIssue
import io.mcdev.deduper.database.data.Issue
import io.mcdev.deduper.github.IssueState
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.kotlin.BindKotlin
import org.jdbi.v3.sqlobject.kotlin.RegisterKotlinMapper
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys
import org.jdbi.v3.sqlobject.statement.SqlBatch
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate

private val stacktraceLock = Any()

@RegisterKotlinMapper(Issue::class)
interface Queries {

    // Postgres doesn't natively support `RETURNING` with `ON CONFLICT DO NOTHING`, so
    // we have to use this rather awful workaround.

    @SqlQuery("SELECT s.id FROM stacktraces s WHERE s.lines = :lines::TEXT[]")
    fun getStacktraceId(@Bind lines: List<String>): Int?

    @SqlUpdate("INSERT INTO stacktraces (lines) VALUES (:lines)")
    @GetGeneratedKeys("id")
    fun insertStacktrace(@Bind lines: List<String>): Int

    fun upsertStacktrace(@Bind lines: List<String>): Int {
        // Postgres doesn't have a fully working upsert mechanism
        // ON CONFLICT DO NOTHING doesn't work with RETURNING, and the workaround is
        // pretty gross: https://stackoverflow.com/a/42217872
        // This is a much more elegant solution, the lock is harmless for this
        synchronized(stacktraceLock) {
            return getStacktraceId(lines) ?: insertStacktrace(lines)
        }
    }

    @SqlQuery("SELECT i.* FROM issues i WHERE i.id = :id")
    fun getIssue(@Bind id: Int): Issue?

    @SqlUpdate(
        """
        INSERT INTO issues (id, title, stacktrace_id, state)
        VALUES (:id, :title, :stacktraceId, :state)
        ON CONFLICT (id)
        DO UPDATE SET
            title = excluded.title,
            stacktrace_id = excluded.stacktrace_id,
            state = excluded.state
        """
    )
    fun upsertIssue(@Bind id: Int, @Bind title: String, @Bind stacktraceId: Int, @Bind state: IssueState)

    @SqlBatch("UPDATE issues SET duplicate_of = :duplicateOfId WHERE id = :issueId")
    fun updateDuplicateIssues(@BindKotlin duplicateIssues: List<DuplicateIssue>)

    @SqlUpdate(
        """
        INSERT INTO stacktrace_targets (stacktrace_id, issue_id)
        VALUES (:stacktraceId, :issueId)
        ON CONFLICT (stacktrace_id) DO UPDATE SET issue_id = excluded.issue_id
        """
    )
    fun setIssueTarget(@Bind stacktraceId: Int, @Bind issueId: Int)

    @SqlQuery("SELECT t.issue_id FROM stacktrace_targets t WHERE t.stacktrace_id = :stacktraceId")
    fun findDuplicateIssue(@Bind stacktraceId: Int): Int?

    @SqlBatch("UPDATE issues SET state = :state::issue_state WHERE id = :issueId")
    fun updateIssueStates(@BindKotlin updateIssue: List<IssueStateUpdate>)

    @SqlQuery(
        """
        SELECT i.id AS issue_id, i.stacktrace_id
        FROM issues i
        INNER JOIN stacktrace_targets st ON i.stacktrace_id = st.stacktrace_id
        WHERE i.state = 'open' AND i.id != st.issue_id
        """
    )
    fun findCloseableIssues(): List<CloseableIssue>
}

@JvmRecord
data class DuplicateIssue(val issueId: Int, val duplicateOfId: Int)

@JvmRecord
data class IssueStateUpdate(val issueId: Int, val state: IssueState)
