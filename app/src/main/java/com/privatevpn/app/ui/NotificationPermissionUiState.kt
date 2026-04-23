package com.privatevpn.app.ui

data class NotificationPermissionUiState(
    val supported: Boolean = false,
    val granted: Boolean = true,
    val promptShown: Boolean = false
) {
    val shouldShowOnboardingPrompt: Boolean
        get() = supported && !granted && !promptShown

    val shouldOpenSystemSettings: Boolean
        get() = supported && !granted && promptShown
}