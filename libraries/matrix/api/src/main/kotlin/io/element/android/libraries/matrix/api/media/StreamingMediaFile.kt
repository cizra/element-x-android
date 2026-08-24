/*
 * Copyright (c) 2025 Element Creations Ltd.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.api.media

import java.io.File
import java.io.IOException

/** A temporary file that grows while its Matrix media response is downloaded. */
interface StreamingMediaFile : MediaFile {
    val file: File
    val expectedSize: Long?
    val isComplete: Boolean
    val failure: Throwable?

    suspend fun awaitCompletion(): Result<Unit>
    fun cancel()

    /** Blocks a background reader until the file grows or the download terminates. */
    @Throws(IOException::class)
    fun awaitMoreBytes(previousLength: Long)
}
