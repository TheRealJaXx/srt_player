package com.localised.stream

import io.github.thibaultbee.srtdroid.core.models.SrtSocket
import java.util.concurrent.LinkedBlockingQueue

class Session {
    val queue = LinkedBlockingQueue<ByteArray>()
    @Volatile
    var closed = false
}

class SrtReceiver(
    private val port: Int,
    private val onSession: (Session) -> Unit,
    private val onStatus: (String) -> Unit
) {
    @Volatile
    private var running = false
    private var thread: Thread? = null
    private var server: SrtSocket? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread { loop() }.apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        try { server?.close() } catch (_: Throwable) {}
        thread?.interrupt()
    }

    private fun loop() {
        try {
            val s = SrtSocket()
            server = s
            s.bind("0.0.0.0", port)
            s.listen(1)
            while (running) {
                onStatus("Waiting for stream")
                val client = s.accept().first
                onStatus("Connected")
                val session = Session()
                onSession(session)
                try {
                    while (running) {
                        val data = client.recv(1456)
                        if (data.isEmpty()) break
                        session.queue.put(data)
                    }
                } catch (e: Throwable) {
                    onStatus("Stream ended: " + e.message)
                } finally {
                    session.closed = true
                    try { client.close() } catch (_: Throwable) {}
                }
            }
        } catch (e: Throwable) {
            onStatus("Error: " + e.message)
        }
    }
}
