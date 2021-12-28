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

import io.ktor.features.BadRequestException
import io.ktor.http.ContentType
import io.ktor.http.content.PartData
import kotlin.contracts.contract

object PartVerifier {
    fun verify(part: PartData, contentType: ContentType) {
        contract {
            returns() implies (part is PartData.FormItem)
        }

        val name = part.name ?: throw BadRequestException("Unnamed part")

        if (part !is PartData.FormItem) {
            throw BadRequestException("$name part is not a form-item")
        }
        if (part.contentType?.match(contentType) == false) {
            throw BadRequestException("$name must have a Content-Type of $contentType")
        }
    }
}
