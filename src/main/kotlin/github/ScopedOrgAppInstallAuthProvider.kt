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

package io.mcdev.deduper.github

import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlin.jvm.Throws
import org.kohsuke.github.GHPermissionType
import org.kohsuke.github.GitHub
import org.kohsuke.github.authorization.AuthorizationProvider

/**
 * Reimplementation of [org.kohsuke.github.authorization.OrgAppInstallationAuthorizationProvider] which supports
 * defining permission for the installation token.
 */
class ScopedOrgAppInstallAuthProvider(
    private val organization: String,
    private val permissions: Map<String, GHPermissionType>,
    authorizationProvider: AuthorizationProvider,
) : GitHub.DependentAuthorizationProvider(authorizationProvider) {

    private var authorization: String? = null
    private var validUntil: Instant = Instant.MIN

    private val lock = Any()

    override fun getEncodedAuthorization(): String {
        synchronized(lock) {
            if (authorization == null || Instant.now().isAfter(validUntil)) {
                authorization = "token ${refreshToken()}"
            }
            return authorization!!
        }
    }

    @Throws(IOException::class)
    private fun refreshToken(): String {
        val installation = gitHub().app.getInstallationByOrganization(organization)
        val installationToken = installation.createToken().permissions(permissions).create()

        validUntil = installationToken.expiresAt.toInstant().minus(Duration.ofMinutes(5))
        return installationToken.token
    }
}
