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

import org.jdbi.v3.sqlobject.statement.SqlScript

interface Tables {

    @SqlScript(
        """
        CREATE TABLE IF NOT EXISTS stacktraces (
            id      SERIAL  NOT NULL PRIMARY KEY,
            lines   TEXT[]  NOT NULL UNIQUE
        );
        """
    )
    fun initializeStacktraces()

    // JDBC doesn't seem to support $$ quotes
    // And this is the best workaround for being unable to say `CREATE TYPE IF NOT EXISTS`
    @SqlScript(
        """
        DO '
        BEGIN
            CREATE TYPE issue_state AS ENUM (''open'', ''closed'');
        EXCEPTION
            WHEN duplicate_object THEN NULL;
        END;
        ';
        """
    )
    @SqlScript(
        """
        CREATE TABLE IF NOT EXISTS issues (
            id              INT         NOT NULL PRIMARY KEY CHECK (id > 0),
            stacktrace_id   INT         NOT NULL,
            state           issue_state NOT NULL,
            duplicate_of    INT         NULL,
        
            FOREIGN KEY (stacktrace_id) REFERENCES stacktraces (id)
                ON DELETE RESTRICT
                ON UPDATE RESTRICT,
        
            FOREIGN KEY (duplicate_of) REFERENCES issues (id)
                ON DELETE RESTRICT
                ON UPDATE RESTRICT
        );
        """
    )
    @SqlScript("CREATE INDEX IF NOT EXISTS stacktrace_id_index ON issues (stacktrace_id);")
    @SqlScript("CREATE INDEX IF NOT EXISTS duplicate_of_index ON issues (duplicate_of);")
    fun initializeIssues()

    @SqlScript(
        """
        CREATE TABLE IF NOT EXISTS stacktrace_targets (
            id              SERIAL  NOT NULL PRIMARY KEY,
            stacktrace_id   INT     NOT NULL UNIQUE,
            issue_id        INT     NOT NULL,
        
            FOREIGN KEY (stacktrace_id) REFERENCES stacktraces (id)
                ON DELETE RESTRICT
                ON UPDATE RESTRICT,
        
            FOREIGN KEY (issue_id) REFERENCES issues (id)
                ON DELETE RESTRICT
                ON UPDATE RESTRICT
        );
        """
    )
    fun initializeStacktraceTargets()
}
