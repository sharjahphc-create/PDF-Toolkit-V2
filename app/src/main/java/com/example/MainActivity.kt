package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.datastore.PreferencesRepository
import com.example.core.datastore.UserPreferences
import com.example.data.repository.LibraryRepository
import com.example.navigation.PdfToolkitAppShell
import com.example.system.PdfToolkitQuickSettingsTileService
import com.example.ui.theme.PdfToolkitTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID
import javax.inject.Inject

sealed interface LaunchCommand {
    val commandId: String

    data class OpenDocumentUri(
        val uri: Uri,
        val fromViewIntent: Boolean,
        override val commandId: String = UUID.randomUUID().toString()
    ) : LaunchCommand

    data class OpenSharedImages(
        val uris: List<Uri>,
        override val commandId: String = UUID.randomUUID().toString()
    ) : LaunchCommand

    data class OpenTool(
        val toolId: String,
        override val commandId: String = UUID.randomUUID().toString()
    ) : LaunchCommand

    data class OpenTab(
        val tabKey: String,
        override val commandId: String = UUID.randomUUID().toString()
    ) : LaunchCommand
}

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var libraryRepository: LibraryRepository

    private val launchCommandFlow = MutableStateFlow<LaunchCommand?>(null)

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        parseIntent(intent)

        setContent {
            val widthSizeClass = calculateWindowSizeClass(this).widthSizeClass
            val prefs by preferencesRepository.preferences.collectAsStateWithLifecycle(
                initialValue = UserPreferences()
            )
            val pendingLaunchCommand by launchCommandFlow.collectAsStateWithLifecycle()

            LaunchedEffect(prefs.appLocaleTag) {
                val targetLocales = if (prefs.appLocaleTag.isBlank()) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(prefs.appLocaleTag)
                }
                if (AppCompatDelegate.getApplicationLocales() != targetLocales) {
                    AppCompatDelegate.setApplicationLocales(targetLocales)
                }
            }

            PdfToolkitTheme(
                themeMode = prefs.themeMode,
                dynamicColor = prefs.dynamicColor,
                highContrast = prefs.highContrast,
                reduceMotion = prefs.reduceMotion
            ) {
                PdfToolkitAppShell(
                    widthSizeClass = widthSizeClass,
                    prefs = prefs,
                    preferencesRepository = preferencesRepository,
                    libraryRepository = libraryRepository,
                    launchCommand = pendingLaunchCommand,
                    onConsumeLaunchCommand = { launchCommandFlow.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseIntent(intent)
    }

    private fun parseIntent(intent: Intent?) {
        if (intent == null) return
        val shortcutTool = intent.getStringExtra(PdfToolkitQuickSettingsTileService.EXTRA_SHORTCUT_TOOL)
        if (!shortcutTool.isNullOrBlank()) {
            launchCommandFlow.value = when (shortcutTool) {
                "notes", "library" -> LaunchCommand.OpenTab(shortcutTool)
                else -> LaunchCommand.OpenTool(shortcutTool)
            }
            return
        }

        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val dataUri = intent.data ?: return
                launchCommandFlow.value = LaunchCommand.OpenDocumentUri(
                    uri = dataUri,
                    fromViewIntent = true
                )
            }
            Intent.ACTION_SEND -> {
                val streamUri = getParcelableExtraUri(intent, Intent.EXTRA_STREAM)
                val mime = intent.type.orEmpty()
                if (streamUri != null) {
                    if (mime.startsWith("image/")) {
                        launchCommandFlow.value = LaunchCommand.OpenSharedImages(listOf(streamUri))
                    } else {
                        launchCommandFlow.value = LaunchCommand.OpenDocumentUri(
                            uri = streamUri,
                            fromViewIntent = false
                        )
                    }
                } else if (mime == "text/plain" && !intent.getStringExtra(Intent.EXTRA_TEXT).isNullOrBlank()) {
                    launchCommandFlow.value = LaunchCommand.OpenTab("notes")
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = getParcelableArrayListUri(intent, Intent.EXTRA_STREAM).orEmpty()
                val mime = intent.type.orEmpty()
                if (uris.isNotEmpty()) {
                    if (mime.startsWith("image/")) {
                        launchCommandFlow.value = LaunchCommand.OpenSharedImages(uris)
                    } else {
                        launchCommandFlow.value = LaunchCommand.OpenDocumentUri(
                            uri = uris.first(),
                            fromViewIntent = false
                        )
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getParcelableExtraUri(intent: Intent, key: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(key, Uri::class.java)
        } else {
            intent.getParcelableExtra(key)
        }
    }

    @Suppress("DEPRECATION")
    private fun getParcelableArrayListUri(intent: Intent, key: String): ArrayList<Uri>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(key, Uri::class.java)
        } else {
            intent.getParcelableArrayListExtra(key)
        }
    }
}
