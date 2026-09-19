package com.sinematv

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class SinemaTvPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(SinemaTvProvider())
    }
}
