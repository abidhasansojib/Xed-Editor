package com.rk.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.activities.settings.SettingsRoutes
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.category.PreferenceCategory
import com.rk.feature.FeatureRegistry
import com.rk.icons.XedIcon
import com.rk.resources.drawables
import com.rk.resources.getFilledString
import com.rk.resources.getString
import com.rk.resources.strings

@Composable
fun SettingsScreen(navController: NavController) {
    PreferenceLayout(label = stringResource(id = strings.settings), backArrowVisible = true) {
        Categories(navController)
    }
}

@Composable
private fun Categories(navController: NavController) {
    PreferenceCategory(
        label = stringResource(id = strings.app),
        description = stringResource(id = strings.app_desc),
        iconResource = drawables.android,
        onNavigate = { navController.navigate(SettingsRoutes.AppSettings.route) },
    )

    PreferenceCategory(
        label = stringResource(strings.themes),
        description = stringResource(strings.theme_settings),
        iconResource = drawables.palette,
        onNavigate = { navController.navigate(SettingsRoutes.Themes.route) },
    )

    PreferenceCategory(
        label = stringResource(id = strings.editor),
        description = stringResource(id = strings.editor_desc),
        iconResource = drawables.edit_note,
        onNavigate = { navController.navigate(SettingsRoutes.EditorSettings.route) },
    )

    PreferenceCategory(
        label = stringResource(strings.keybindings),
        description = stringResource(strings.keybindings_desc),
        iconResource = drawables.keyboard,
        onNavigate = { navController.navigate(SettingsRoutes.Keybindings.route) },
    )

    SettingsRegistry.categories.value.forEach { category ->
        PreferenceCategory(
            label = category.label,
            description = category.description,
            startWidget = {
                XedIcon(
                    icon = category.icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            onNavigate = { navController.navigate(category.route) },
        )
    }

    if (FeatureRegistry.isEnabled("debug_mode")) {
        PreferenceCategory(
            label = stringResource(strings.debug_options),
            description = strings.debug_options_desc.getFilledString(strings.app_name.getString()),
            iconResource = drawables.build,
            onNavigate = { navController.navigate(SettingsRoutes.DeveloperOptions.route) },
        )
    }
}
