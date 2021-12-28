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

@file:JvmName("GitHubInit")

package io.mcdev.deduper.github

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.mcdev.deduper.GitHubApiConfig
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.time.Instant
import java.util.Date
import kotlin.io.path.Path
import org.kohsuke.github.GHPermissionType
import org.kohsuke.github.GitHub
import org.kohsuke.github.GitHubBuilder
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("io.mcdev.deduper.github.GitHubInit")

fun GitHubApiConfig.initializeApp(): GitHub {
    logger.info("Initializing GitHub client")

    val jwt = createJwt()

    val gitHubApp = GitHubBuilder().withJwtToken(jwt).build()
    val installation = gitHubApp.app.getInstallationById(installationId)

    val permissions = mapOf("issues" to GHPermissionType.WRITE)
    val installationToken = installation.createToken().permissions(permissions).create()

    val gitHubAppInstallation = GitHubBuilder().withAppInstallationToken(installationToken.token).build()

    if (!gitHubAppInstallation.isCredentialValid) {
        throw Exception("Failed to authenticate with GitHub")
    }

    logger.info("GitHub client initialized successfully")

    return gitHubAppInstallation
}

private fun GitHubApiConfig.createJwt(): String {
    val signatureAlgorithm = SignatureAlgorithm.RS256
    val signingKey = readPrivateKey(Path(privateKeyFile))

    val now = Instant.now()
    val expiration = now + Duration.ofMinutes(10)

    return Jwts.builder()
        .setIssuedAt(Date.from(now))
        .setExpiration(Date.from(expiration))
        .setIssuer(appId)
        .signWith(signingKey, signatureAlgorithm)
        .compact()
}

private fun readPrivateKey(path: Path): PrivateKey {
    val bytes = Files.readAllBytes(path)

    val spec = PKCS8EncodedKeySpec(bytes)
    val kf = KeyFactory.getInstance("RSA")
    return kf.generatePrivate(spec)
}
