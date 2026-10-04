package com.localised.stream

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
class SrtDataSource(private val session: Session) : BaseDataSource(true) {
    private var current: ByteArray? = null
    private var pos = 0
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        opened = true
        transferStarted(dataSpec)
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        while (true) {
            val cur = current
            if (cur != null && pos < cur.size) {
                val n = minOf(length, cur.size - pos)
                System.arraycopy(cur, pos, buffer, offset, n)
                pos += n
                bytesTransferred(n)
                return n
            }
            val next = session.queue.poll(200, TimeUnit.MILLISECONDS)
            if (next != null) {
                current = next
                pos = 0
            } else if (session.closed) {
                return C.RESULT_END_OF_INPUT
            }
        }
    }

    override fun getUri(): Uri? = Uri.parse("srt://live")

    override fun close() {
        if (opened) {
            opened = false
            transferEnded()
        }
    }
}
