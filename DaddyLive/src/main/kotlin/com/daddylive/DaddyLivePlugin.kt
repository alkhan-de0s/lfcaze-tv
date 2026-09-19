package com.daddylive

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class DaddyLivePlugin : Plugin() {
    override fun load(context: Context) {
        // Register provider
        registerMainAPI(DaddyLiveProvider())
    }
}
