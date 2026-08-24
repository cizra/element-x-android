/*
 * Copyright (c) 2025 Element Creations Ltd.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.mediaviewer.impl.local.video

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import io.element.android.libraries.matrix.api.media.StreamingMediaFile
import java.io.EOFException
import java.io.RandomAccessFile

@UnstableApi
internal class GrowingFileDataSource(
    private val media: StreamingMediaFile,
) : BaseDataSource(false) {
    private var reader: RandomAccessFile? = null
    private var uri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        if (dataSpec.position > media.file.length()) throw EOFException("Position is not downloaded")
        uri = Uri.fromFile(media.file)
        reader = RandomAccessFile(media.file, "r").apply { seek(dataSpec.position) }
        transferStarted(dataSpec)
        return media.expectedSize?.minus(dataSpec.position) ?: C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val reader = checkNotNull(reader)
        while (true) {
            val available = media.file.length() - reader.filePointer
            if (available > 0) {
                val read = reader.read(buffer, offset, minOf(length.toLong(), available).toInt())
                if (read > 0) {
                    bytesTransferred(read)
                    return read
                }
            }
            if (media.isComplete) return C.RESULT_END_OF_INPUT
            media.awaitMoreBytes(media.file.length())
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        reader?.close()
        reader = null
        uri = null
        transferEnded()
    }

    class Factory(private val media: StreamingMediaFile) : DataSource.Factory {
        override fun createDataSource(): DataSource = GrowingFileDataSource(media)
    }
}
