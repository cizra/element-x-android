/*
 * Copyright (c) 2025 Element Creations Ltd.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.impl.media

import io.element.android.libraries.matrix.api.media.StreamingMediaFile
import org.matrix.rustcomponents.sdk.StreamingMediaFileHandle
import java.io.File
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class RustStreamingMediaFile(
    private val handle: StreamingMediaFileHandle,
    private val lock: ReentrantLock,
) : StreamingMediaFile {
    private val changed = lock.newCondition()

    @Volatile
    override var isComplete = false
        private set

    @Volatile
    override var failure: Throwable? = null
        private set

    override val file: File = File(handle.path())
    override val expectedSize: Long?
        get() = handle.expectedSize()?.toLong()

    fun signalProgress() = lock.withLock { changed.signalAll() }

    override suspend fun awaitCompletion(): Result<Unit> = runCatching {
        handle.waitForCompletion()
    }.also { result ->
        lock.withLock {
            result.onSuccess { isComplete = true }.onFailure { failure = it }
            changed.signalAll()
        }
    }

    override fun awaitMoreBytes(previousLength: Long) {
        lock.withLock {
            while (file.length() <= previousLength && !isComplete && failure == null) {
                changed.await()
            }
            failure?.let { throw IOException("Media download failed", it) }
        }
    }

    override fun path(): String = file.absolutePath
    override fun persist(path: String): Boolean = false
    override fun cancel() = handle.cancel()

    override fun close() {
        handle.close()
        lock.withLock {
            failure = failure ?: IOException("Media download cancelled")
            changed.signalAll()
        }
    }
}
