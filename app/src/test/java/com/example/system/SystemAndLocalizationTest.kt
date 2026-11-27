package com.example.system

import android.Manifest
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.example.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SystemAndLocalizationTest {

    @Test
    fun `offline privacy verifier confirms zero internet permission and SAF-only storage`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val report = OfflinePrivacyVerifier.inspect(context)

        assertFalse(
            "App must NOT declare android.permission.INTERNET",
            report.hasInternetPermission
        )
        assertTrue("App must be verified 100% offline", report.isFullyOffline)
        assertTrue(
            "App must NOT declare broad storage permissions: ${report.broadStoragePermissionsFound}",
            report.usesSafOnly
        )
        assertFalse(report.declaredPermissions.contains(Manifest.permission.INTERNET))
        assertFalse(report.declaredPermissions.contains(Manifest.permission.READ_EXTERNAL_STORAGE))
        assertFalse(report.declaredPermissions.contains(Manifest.permission.WRITE_EXTERNAL_STORAGE))
    }

    @Test
    fun `shared intent bridge performs one-shot handoff for images and plain text`() {
        val bridge = SharedIntentBridge()
        val uris = listOf(Uri.parse("content://example/img1.jpg"), Uri.parse("content://example/img2.jpg"))

        bridge.enqueueSharedImages(uris)
        assertEquals(uris, bridge.consumeSharedImages())
        assertTrue("Second consume must be empty", bridge.consumeSharedImages().isEmpty())

        bridge.enqueueSharedText("  Meeting action items\n- Sign NDA  ")
        assertEquals("Meeting action items\n- Sign NDA", bridge.consumeSharedText())
        assertNull("Second consume must return null", bridge.consumeSharedText())
    }

    @Test
    fun `all five locales resolve localized strings and Arabic enables RTL layout direction`() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()

        val expectedToolsTitles = mapOf(
            "en" to "Tools",
            "es" to "Herramientas",
            "hi" to "टूल्स",
            "ar" to "الأدوات",
            "pt" to "Ferramentas"
        )

        expectedToolsTitles.forEach { (langTag, expectedNavTools) ->
            val locale = Locale.forLanguageTag(langTag)
            val config = Configuration(baseContext.resources.configuration).apply {
                setLocale(locale)
            }
            val localizedContext = baseContext.createConfigurationContext(config)

            assertEquals(expectedNavTools, localizedContext.getString(R.string.nav_tools))
            assertTrue(localizedContext.getString(R.string.onboarding_slide1_title).isNotBlank())
            assertTrue(localizedContext.getString(R.string.widget_title).isNotBlank())
            assertTrue(localizedContext.getString(R.string.more_privacy_verified).isNotBlank())

            if (langTag == "ar") {
                assertEquals(
                    "Arabic locale must configure RTL layout direction",
                    View.LAYOUT_DIRECTION_RTL,
                    config.layoutDirection
                )
            }
        }
    }

    @Test
    fun `shortcuts widget and locale XML resources parse cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        context.resources.getXml(R.xml.shortcuts).use { parser ->
            assertTrue(parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT)
        }
        context.resources.getXml(R.xml.pdf_toolkit_widget_info).use { parser ->
            assertTrue(parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT)
        }
        context.resources.getXml(R.xml.locales_config).use { parser ->
            assertTrue(parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT)
        }
    }
}
