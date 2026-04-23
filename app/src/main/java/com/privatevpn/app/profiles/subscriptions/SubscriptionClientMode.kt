package com.privatevpn.app.profiles.subscriptions

import java.util.Locale

enum class SubscriptionClientMode(val wireValue: String) {
    AUTO("auto"),
    GENERIC("generic"),
    HAPP("happ"),
    MARZBAN_HWID("marzban-hwid");

    companion object {
        fun fromWireValue(raw: String?): SubscriptionClientMode {
            val normalized = raw?.trim()?.lowercase(Locale.US).orEmpty()
            return entries.firstOrNull { it.wireValue == normalized } ?: AUTO
        }
    }
}

