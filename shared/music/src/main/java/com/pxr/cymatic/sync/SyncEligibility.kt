package com.pxr.cymatic.sync

internal object SyncEligibility {
    fun isEligible(
        config: RemoteSourceConfig,
        hasCredentials: Boolean,
        pins: OfflineSelection,
    ): Boolean {
        if (!config.enabled || !hasCredentials) return false
        if (!pins.isEmpty) return true
        if (!config.contentConfigured) return false
        val mode = SyncContentMode.from(config.contentMode)
        if (mode == SyncContentMode.NONE) return false
        if (mode == SyncContentMode.SELECTED_PLAYLISTS && config.selectedPlaylists.isEmpty()) {
            return false
        }
        return true
    }
}
