package com.example.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.datastore.PreferencesRepository
import com.example.core.ui.components.RunsOnDeviceBadge
import com.example.data.repository.LibraryRepository
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    preferencesRepository: PreferencesRepository,
    libraryRepository: LibraryRepository,
    onCompleted: (openSampleDocId: String?) -> Unit
) {
    var page by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    val slides = listOf(
        Triple(
            Icons.Default.Shield,
            "100% Offline & Private",
            "No internet permission, no cloud uploads, no analytics. Your contracts, scans, and notes stay strictly on your device."
        ),
        Triple(
            Icons.Default.Draw,
            "Sign, Edit & Scan Everything",
            "Draw signatures, fill forms, merge/split/redact PDFs, edit Word & Excel files, and scan documents with ML Kit."
        ),
        Triple(
            Icons.Default.Lock,
            "AES-256 Vault & Read Aloud",
            "Protect sensitive documents with Keystore encryption + biometrics, read EPUB/PDFs with warm light, and listen via offline TTS."
        )
    )
    val current = slides[page]

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 540.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RunsOnDeviceBadge()
                    TextButton(
                        onClick = {
                            scope.launch {
                                preferencesRepository.setOnboardingCompleted(true)
                                onCompleted(null)
                            }
                        },
                        modifier = Modifier.testTag("onboarding_skip_button")
                    ) {
                        Text("Skip")
                    }
                }

                Spacer(Modifier.height(12.dp))

                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(96.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = current.first,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(46.dp)
                        )
                    }
                }

                Text(text = current.second, style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = current.third,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    slides.indices.forEach { idx ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (idx == page) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(width = if (idx == page) 28.dp else 10.dp, height = 8.dp)
                        ) {}
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (page < slides.lastIndex) {
                    Button(
                        onClick = { page += 1 },
                        modifier = Modifier.fillMaxWidth().testTag("onboarding_next_button")
                    ) {
                        Text("Next")
                    }
                } else {
                    Button(
                        onClick = {
                            scope.launch {
                                val sample = libraryRepository.importBundledSamplePdf().getOrNull()
                                preferencesRepository.setOnboardingCompleted(true)
                                onCompleted(sample?.id)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("onboarding_sample_start_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Open Sample PDF Guide")
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                preferencesRepository.setOnboardingCompleted(true)
                                onCompleted(null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("onboarding_start_empty_button")
                    ) {
                        Text("Go to Tools Home")
                    }
                }
            }
        }
    }
}
