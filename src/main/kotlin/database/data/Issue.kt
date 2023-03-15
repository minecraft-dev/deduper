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

package io.mcdev.deduper.database.data

import io.mcdev.deduper.github.IssueState
import org.jdbi.v3.core.mapper.reflect.ColumnName

@JvmRecord
data class Issue(
    @ColumnName("id")
    val id: Int,
    @ColumnName("title")
    val title: String,
    @ColumnName("stacktrace_id")
    val stacktraceId: Int,
    @ColumnName("state")
    val state: IssueState,
    @ColumnName("duplicate_of")
    val duplicateOf: Int,
)
