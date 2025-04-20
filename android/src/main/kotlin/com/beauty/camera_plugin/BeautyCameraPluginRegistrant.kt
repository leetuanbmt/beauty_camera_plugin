package com.beauty.camera_plugin

import io.flutter.plugin.common.PluginRegistry

object BeautyCameraPluginRegistrant {
    fun registerWith(registry: PluginRegistry) {
        if (alreadyRegisteredWith(registry)) {
            return
        }
        BeautyCameraPlugin.registerWith(registry.registrarFor("com.beauty.camera_plugin.BeautyCameraPlugin"))
    }

    private fun alreadyRegisteredWith(registry: PluginRegistry): Boolean {
        val key = BeautyCameraPluginRegistrant::class.java.canonicalName
        if (registry.hasPlugin(key)) {
            return true
        }
        registry.registrarFor(key)
        return false
    }
} 