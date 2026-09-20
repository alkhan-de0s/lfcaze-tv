package com.turkligasi

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class TurkLigasiPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(TurkLigasiProvider())
    }
}
