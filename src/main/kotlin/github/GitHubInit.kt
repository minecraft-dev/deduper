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

@file:JvmName("GitHubInit")

package io.mcdev.deduper.github

import io.mcdev.deduper.GitHubApiConfig
import kotlin.io.path.Path
import org.kohsuke.github.GHPermissionType
import org.kohsuke.github.GitHub
import org.kohsuke.github.GitHubBuilder
import org.kohsuke.github.extras.authorization.JWTTokenProvider
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("io.mcdev.deduper.github.GitHubInit")

fun GitHubApiConfig.initializeApp(): GitHub {
    logger.info("Initializing GitHub client")

    val jwt = JWTTokenProvider(appId, Path(privateKeyFile))

    val permissions = mapOf("issues" to GHPermissionType.WRITE)
    val orgAuth = ScopedOrgAppInstallAuthProvider(organization, permissions, jwt)

    val gh = GitHubBuilder().withAuthorizationProvider(orgAuth).build()
    if (!gh.isCredentialValid) {
        throw Exception("Failed to authenticate with GitHub")
    }

    logger.info("GitHub client initialized successfully")

    return gh
}
