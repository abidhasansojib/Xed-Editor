package com.rk.settings.runners

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.components.InfoBlock
import com.rk.components.SettingsItem
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.icons.Icon
import com.rk.icons.XedIcon
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.runner.RunnerManager
import com.rk.settings.Settings
import com.rk.utils.openDocs

@Composable
fun RunnerSettings(modifier: Modifier = Modifier, navController: NavController) {
    val context = LocalContext.current
    val builtinRunners by RunnerManager.builtinRunners.collectAsStateWithLifecycle()
    val extensionRunners by RunnerManager.extensionRunners.collectAsStateWithLifecycle()

    PreferenceLayout(
        label = stringResource(strings.runners),
    ) {
        InfoBlock(
            onClick = { context.openDocs("runners") },
            icon = { Icon(imageVector = Icons.Outlined.Info, contentDescription = null) },
            text = stringResource(strings.info_runners),
        )

        PreferenceGroup(heading = stringResource(strings.built_in)) {
            SettingsItem(
                label = stringResource(strings.default_markdown_preview),
                description = stringResource(strings.default_markdown_preview_desc),
                default = Settings.default_markdown_preview,
                sideEffect = { Settings.default_markdown_preview = it },
            )

            builtinRunners.forEach { runner ->
                SettingsItem(
                    label = runner.label,
                    description = runner.description,
                    default = runner.isEnabled(),
                    sideEffect = { runner.setEnabled(it) },
                    onClick = runner.onConfigure,
                )
            }
        }

        if (extensionRunners.isNotEmpty()) {
            PreferenceGroup(heading = stringResource(strings.ext)) {
                extensionRunners.forEach { runner ->
                    SettingsItem(
                        label = runner.label,
                        description = runner.description,
                        startWidget = {
                            XedIcon(
                                icon = runner.getIcon(context) ?: Icon.ResourceIcon(drawableRes = drawables.run),
                                contentDescription = null,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        },
                        default = runner.isEnabled(),
                        sideEffect = { runner.setEnabled(it) },
                        onClick = runner.onConfigure,
                    )
                }
            }
        }
    }
}
