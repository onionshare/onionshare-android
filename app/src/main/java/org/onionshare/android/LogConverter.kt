package org.onionshare.android

import ch.qos.logback.classic.pattern.MessageConverter
import ch.qos.logback.classic.spi.ILoggingEvent

class LogConverter : MessageConverter() {

    override fun convert(event: ILoggingEvent): String {
        val before = super.convert(event)
        // Scrub local port numbers
        return before.replace("\\b127\\.0\\.0\\.1:[0-9]+\\b".toRegex(), "127.0.0.1:[scrubbed]")
    }
}