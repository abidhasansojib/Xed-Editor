package com.rk

import android.app.Application
import com.rk.activities.settings.SettingsRoutes
import com.rk.commands.CommandProvider
import com.rk.commands.ToolbarConfiguration
import com.rk.commands.global.TerminalCommand
import com.rk.extension.api.DynamicRoute
import com.rk.feature.Feature
import com.rk.feature.FeatureToggle
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.SettingsCategory
import com.rk.settings.SettingsRegistry
import com.rk.settings.editor.TerminalFontScreen
import com.rk.settings.terminal.SettingsTerminalScreen
import com.rk.settings.terminal.TerminalExtraKeys

class TerminalFeature : Feature {
    override val toggle =
        FeatureToggle(
            name = strings.terminal.getString(),
            key = "feature_terminal",
            default = true,
            icon = Icon.ResourceIcon(drawables.terminal),
        )

    private var settingsCategory: SettingsCategory? = null
    private val routes = mutableListOf<DynamicRoute>()

    override fun init(application: Application) {
        // Register settings category
        settingsCategory =
            SettingsCategory(
                label = strings.terminal.getString(),
                description = strings.terminal_desc.getString(),
                icon = Icon.ResourceIcon(drawables.terminal),
                route = SettingsRoutes.TerminalSettings.route,
            ).also { SettingsRegistry.registerCategory(it) }

        // Register settings routes
        routes.add(DynamicRoute(SettingsRoutes.TerminalSettings.route) { _, _ -> SettingsTerminalScreen() })
        routes.add(DynamicRoute(SettingsRoutes.TerminalExtraKeys.route) { _, _ -> TerminalExtraKeys() })
        routes.add(DynamicRoute(SettingsRoutes.TerminalFontScreen.route) { _, _ -> TerminalFontScreen() })

        routes.forEach { SettingsRegistry.registerRoute(it) }

        // Register global command
        CommandProvider.registerCommand(TerminalCommand)

        // Add to main editor toolbar
        ToolbarConfiguration.addGlobalToolbarCommand(TerminalCommand, index = 1)
    }

    override fun dispose(application: Application) {
        settingsCategory?.let { SettingsRegistry.unregisterCategory(it) }
        routes.forEach { SettingsRegistry.unregisterRoute(it) }
        routes.clear()

        CommandProvider.unregisterCommand(TerminalCommand)
        ToolbarConfiguration.removeGlobalToolbarCommand(TerminalCommand)
    }
}
