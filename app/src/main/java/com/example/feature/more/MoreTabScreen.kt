package com.example.feature.more

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.example.R
import com.example.core.datastore.PreferencesRepository
import com.example.core.datastore.ThemeMode
import com.example.core.datastore.UserPreferences
import com.example.core.ui.components.RunsOnDeviceBadge
import kotlinx.coroutines.launch

private data class LocaleOption(
    val tag: String,
    val label: String
)

private val supportedLocales = listOf(
    LocaleOption("", "System"),
    LocaleOption("en", "English"),
    LocaleOption("es", "Español"),
    LocaleOption("hi", "हिन्दी"),
    LocaleOption("ar", "العربية (RTL)"),
    LocaleOption("pt", "Português")
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MoreTabScreen(
    prefs: UserPreferences,
    preferencesRepository: PreferencesRepository,
    onOpenLibrary: () -> Unit,
    onOpenDesignSystem: () -> Unit,
    onOpenVault: () -> Unit = {},
    onOpenFeedback: () -> Unit = {},
    onOpenOnboarding: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.more_title),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = stringResource(R.string.more_subtitle_offline),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    RunsOnDeviceBadge()
                }
                item {
                    MoreActionCard(
                        icon = Icons.AutoMirrored.Filled.LibraryBooks,
                        title = stringResource(R.string.more_library_title),
                        subtitle = stringResource(R.string.more_library_subtitle),
                        onClick = onOpenLibrary,
                        testTag = "more_open_library"
                    )
                }
                item {
                    MoreActionCard(
                        icon = Icons.Default.Shield,
                        title = stringResource(R.string.more_vault_title),
                        subtitle = stringResource(R.string.more_vault_subtitle),
                        onClick = onOpenVault,
                        testTag = "more_open_vault"
                    )
                }
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.more_security_privacy), style = MaterialTheme.typography.titleMedium)
                            }
                            MoreToggleRow(
                                label = if (prefs.vaultPinHash.isNullOrBlank()) {
                                    "App Lock on Launch (Set Vault PIN first)"
                                } else {
                                    "App Lock on Launch"
                                },
                                checked = prefs.appLockEnabled && !prefs.vaultPinHash.isNullOrBlank(),
                                testTag = "more_app_lock_switch",
                                onCheckedChange = { enabled ->
                                    if (enabled && prefs.vaultPinHash.isNullOrBlank()) {
                                        onOpenVault()
                                    } else {
                                        scope.launch { preferencesRepository.setAppLockEnabled(enabled) }
                                    }
                                }
                            )
                            MoreToggleRow(
                                label = "Hide Locked Thumbnails",
                                checked = prefs.hideLockedThumbnails,
                                testTag = "more_hide_thumbnails_switch",
                                onCheckedChange = {
                                    scope.launch { preferencesRepository.setHideLockedThumbnails(it) }
                                }
                            )
                            Text(
                                text = "Auto-lock Timeout",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(0 to "Immediate", 1 to "1 min", 5 to "5 min", 15 to "15 min").forEach { (mins, label) ->
                                    FilterChip(
                                        selected = prefs.vaultAutoLockMinutes == mins,
                                        onClick = { scope.launch { preferencesRepository.setVaultAutoLockMinutes(mins) } },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(stringResource(R.string.more_appearance), style = MaterialTheme.typography.titleMedium)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ThemeMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = prefs.themeMode == mode,
                                        onClick = { scope.launch { preferencesRepository.setThemeMode(mode) } },
                                        label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                                    )
                                }
                            }
                            MoreToggleRow("Dynamic Color", prefs.dynamicColor, "more_dynamic_color_switch") {
                                scope.launch { preferencesRepository.setDynamicColor(it) }
                            }
                            MoreToggleRow("High Contrast", prefs.highContrast, "more_high_contrast_switch") {
                                scope.launch { preferencesRepository.setHighContrast(it) }
                            }
                            MoreToggleRow("Reduce Motion", prefs.reduceMotion, "more_reduce_motion_switch") {
                                scope.launch { preferencesRepository.setReduceMotion(it) }
                            }
                        }
                    }
                }
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.more_language), style = MaterialTheme.typography.titleMedium)
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                supportedLocales.forEach { localeOption ->
                                    FilterChip(
                                        selected = prefs.appLocaleTag == localeOption.tag,
                                        onClick = {
                                            scope.launch {
                                                preferencesRepository.setAppLocaleTag(localeOption.tag)
                                                val list = if (localeOption.tag.isBlank()) {
                                                    LocaleListCompat.getEmptyLocaleList()
                                                } else {
                                                    LocaleListCompat.forLanguageTags(localeOption.tag)
                                                }
                                                AppCompatDelegate.setApplicationLocales(list)
                                            }
                                        },
                                        label = { Text(localeOption.label) },
                                        modifier = Modifier.testTag("locale_chip_${localeOption.tag.ifEmpty { "system" }}")
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    MoreActionCard(
                        icon = Icons.Default.Feedback,
                        title = stringResource(R.string.more_feedback_title),
                        subtitle = stringResource(R.string.more_feedback_subtitle),
                        onClick = onOpenFeedback,
                        testTag = "more_open_feedback"
                    )
                }
                item {
                    MoreActionCard(
                        icon = Icons.AutoMirrored.Filled.HelpOutline,
                        title = stringResource(R.string.more_onboarding_title),
                        subtitle = stringResource(R.string.more_onboarding_subtitle),
                        onClick = onOpenOnboarding,
                        testTag = "more_open_onboarding"
                    )
                }
                item {
                    MoreActionCard(
                        icon = Icons.Default.Palette,
                        title = "Design System Gallery",
                        subtitle = "Inspect shared Material 3 Expressive components & steppers",
                        onClick = onOpenDesignSystem,
                        testTag = "more_open_design_system"
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .testTag(testTag)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun MoreToggleRow(
    label: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
