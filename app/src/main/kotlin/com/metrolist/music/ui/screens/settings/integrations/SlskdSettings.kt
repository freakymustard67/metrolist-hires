/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings.integrations

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.SlskdApiKeyKey
import com.metrolist.music.constants.SlskdBaseUrlKey
import com.metrolist.music.constants.SlskdEnabledKey
import com.metrolist.music.slskd.SlskdApiClient
import com.metrolist.music.slskd.SlskdConfig
import com.metrolist.music.slskd.SlskdErrorMapper
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.InfoLabel
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.component.TextFieldDialog
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlskdSettings(
    navController: NavController,
) {
    val scope = rememberCoroutineScope()

    var slskdEnabled by rememberPreference(SlskdEnabledKey, false)
    var baseUrl by rememberPreference(SlskdBaseUrlKey, "")
    var apiKey by rememberPreference(SlskdApiKeyKey, "")

    var showUrlDialog by rememberSaveable { mutableStateOf(false) }
    var showKeyDialog by rememberSaveable { mutableStateOf(false) }
    var testing by rememberSaveable { mutableStateOf(false) }
    var testResultRes by rememberSaveable { mutableStateOf<Int?>(null) }

    fun runTest() {
        testing = true
        testResultRes = null
        scope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    val client =
                        try {
                            SlskdApiClient(baseUrl, apiKey)
                        } catch (e: Exception) {
                            return@withContext SlskdErrorMapper.messageRes(e)
                        }
                    try {
                        val probe = client.probe()
                        when {
                            !probe.authValid -> R.string.slskd_error_credentials
                            !probe.securityEnabled -> R.string.slskd_connected_no_auth
                            else -> R.string.slskd_connected
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        SlskdErrorMapper.messageRes(e)
                    } finally {
                        client.close()
                    }
                }
            testing = false
            testResultRes = result
        }
    }

    if (showUrlDialog) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.slskd_server_url)) },
            icon = { Icon(painterResource(R.drawable.link), null) },
            initialTextFieldValue = TextFieldValue(text = baseUrl),
            placeholder = { Text(stringResource(R.string.slskd_server_url_hint)) },
            isInputValid = { it.isNotBlank() },
            onDone = {
                baseUrl = it.trim()
                testResultRes = null
                showUrlDialog = false
            },
            onDismiss = { showUrlDialog = false },
        )
    }

    if (showKeyDialog) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.slskd_api_key)) },
            icon = { Icon(painterResource(R.drawable.key), null) },
            initialTextFieldValue = TextFieldValue(text = apiKey),
            isInputValid = { true },
            onDone = {
                apiKey = it.trim()
                testResultRes = null
                showKeyDialog = false
            },
            onDismiss = { showKeyDialog = false },
        )
    }

    Column(
        modifier =
            Modifier
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
    ) {
        Material3SettingsGroup(
            items =
                listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.cloud),
                        title = { Text(stringResource(R.string.slskd_enable)) },
                        description = { Text(stringResource(R.string.slskd_enable_desc)) },
                        trailingContent = {
                            Switch(
                                checked = slskdEnabled,
                                onCheckedChange = { slskdEnabled = it },
                                thumbContent = {
                                    Icon(
                                        painter =
                                            painterResource(
                                                id = if (slskdEnabled) R.drawable.check else R.drawable.close,
                                            ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize),
                                    )
                                },
                            )
                        },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.link),
                        title = { Text(stringResource(R.string.slskd_server_url)) },
                        description = {
                            Text(
                                baseUrl.ifBlank { stringResource(R.string.slskd_server_url_hint) },
                                color =
                                    if (baseUrl.isBlank()) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                            )
                        },
                        onClick = { showUrlDialog = true },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.key),
                        title = { Text(stringResource(R.string.slskd_api_key)) },
                        description = {
                            Text(
                                if (apiKey.isNotEmpty()) {
                                    "•".repeat(minOf(apiKey.length, 8))
                                } else {
                                    stringResource(R.string.not_set)
                                },
                            )
                        },
                        onClick = { showKeyDialog = true },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.check),
                        title = { Text(stringResource(R.string.slskd_test_connection)) },
                        description = {
                            when {
                                testing -> Text(stringResource(R.string.slskd_testing))
                                testResultRes != null -> Text(stringResource(testResultRes!!))
                            }
                        },
                        onClick = {
                            if (!testing && SlskdConfig.isConfigured(baseUrl, apiKey)) {
                                runTest()
                            } else if (!testing) {
                                testResultRes = R.string.slskd_error_config
                            }
                        },
                    ),
                ),
        )

        Spacer(modifier = Modifier.height(12.dp))

        InfoLabel(text = stringResource(R.string.slskd_api_key_desc))

        Spacer(modifier = Modifier.height(27.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.slskd_integration)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
    )
}
