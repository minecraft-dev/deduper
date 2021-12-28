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

@file:JvmName("WebhookVerifier")

package io.mcdev.deduper.github

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.features.BadRequestException
import io.ktor.request.contentCharset
import io.ktor.request.receiveChannel
import io.ktor.util.pipeline.PipelineContext
import io.ktor.util.toByteArray
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private var webhookSecret: ByteArray? = null

suspend fun PipelineContext<Unit, ApplicationCall>.verifyWebhook(secret: String): String {
    val expectedSignatureHex = call.request.headers["X-Hub-Signature-256"]?.removePrefix("sha256=")
        ?: throw BadRequestException("No X-Hub-Signature-256 header")

    val expectedSignature = HexFormat.of().parseHex(expectedSignatureHex)

    val data = call.receiveChannel().toByteArray()

    val sha256hmac = Mac.getInstance("HmacSHA256")
    val secretKey = SecretKeySpec(computeSecret(secret), "HmacSHA256")
    sha256hmac.init(secretKey)
    val actualSignature = sha256hmac.doFinal(data)

    if (!expectedSignature.contentEquals(actualSignature)) {
        throw BadRequestException("Signatures do not match")
    }

    val charset = call.request.contentCharset() ?: Charsets.UTF_8
    return String(data, charset)
}

private fun computeSecret(secret: String): ByteArray {
    var s = webhookSecret
    if (s != null) {
        return s
    }

    s = secret.toByteArray()
    webhookSecret = s
    return s
}
