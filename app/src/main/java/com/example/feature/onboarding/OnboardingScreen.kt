package com.example.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.datastore.PreferencesRepository
import com.example.core.ui.components.RunsOnDeviceBadge
import com.example.data.repository.LibraryRepository
import com.example.ui.theme.LocalReduceMotion
import com.example.ui.theme.surfaceHairlineBorder
import kotlinx.coroutines.launch

private data class OnboardingSlide(
    val icon: ImageVector,
    val titleRes: Int,
    val bodyRes: Int,
    val highlights: List<String>
)

@Composable
fun OnboardingScreen(
    preferencesRepository: PreferencesRepository,
    libraryRepository: LibraryRepository,
    onCompleted: (openSampleDocId: String?) -> Unit
) {
    val slides = listOf(
        OnboardingSlide(
            icon = Icons.Default.Shield,
            titleRes = R.string.onboarding_slide1_title,
            bodyRes = R.string.onboarding_slide1_body,
            highlights = listOf("Zero INTERNET permission", "SAF-only file access", "Works in airplane mode")
        ),
        OnboardingSlide(
            icon = Icons.Default.Draw,
            titleRes = R.string.onboarding_slide2_title,
            bodyRes = R.string.onboarding_slide2_body,
            highlights = listOf("Visual & PKCS#12 signing", "Redact, compare & batch ZIP", "Word, Excel & OCR offline")
        ),
        OnboardingSlide(
            icon = Icons.Default.Lock,
            titleRes = R.string.onboarding_slide3_title,
            bodyRes = R.string.onboarding_slide3_body,
            highlights = listOf("Hardware Keystore AES-256", "Biometric & PIN lock", "PDF/EPUB reader + TTS")
        )
    )
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current

    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch {
            if (reduceMotion) pagerState.scrollToPage(pagerState.currentPage - 1)
            else pagerState.animateScrollToPage(pagerState.currentPage - 1)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
                        )
                    )
                )
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
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
                        Text(stringResource(R.string.onboarding_skip))
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("onboarding_pager")
                ) { pageIndex ->
                    val slide = slides[pageIndex]
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            tonalElevation = 4.dp,
                            modifier = Modifier.size(104.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = slide.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(22.dp))

                        Text(
                            text = stringResource(slide.titleRes),
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(10.dp))

                        Text(
                            text = stringResource(slide.bodyRes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(20.dp))

                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            border = surfaceHairlineBorder(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                slide.highlights.forEach { bullet ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = bullet,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    slides.indices.forEach { idx ->
                        val active = idx == pagerState.currentPage
                        val width by animateDpAsState(
                            targetValue = if (active) 28.dp else 10.dp,
                            animationSpec = if (reduceMotion) snap() else spring(),
                            label = "onboarding_indicator"
                        )
                        Box(
                            modifier = Modifier
                                .size(width = width, height = 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                if (pagerState.currentPage < slides.lastIndex) {
                    Button(
                        onClick = {
                            scope.launch {
                                val next = pagerState.currentPage + 1
                                if (reduceMotion) pagerState.scrollToPage(next)
                                else pagerState.animateScrollToPage(next)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("onboarding_next_button")
                    ) {
                        Text(stringResource(R.string.onboarding_next))
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
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("onboarding_sample_start_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.onboarding_open_sample))
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                preferencesRepository.setOnboardingCompleted(true)
                                onCompleted(null)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("onboarding_start_empty_button")
                    ) {
                        Text(stringResource(R.string.onboarding_start_tools))
                    }
                }
            }
        }
    }
}
