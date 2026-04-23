package com.privatevpn.app.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeFormatter {
    private val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())

    fun format(timestampMs: Long): String = formatter.format(Date(timestampMs))
}
