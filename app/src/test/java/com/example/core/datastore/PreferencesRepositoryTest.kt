package com.example.core.datastore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PreferencesRepositoryTest {

    @Test
    fun `view mode and sort mode persist across reads`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PreferencesRepository(context)

        repo.setToolsViewMode(ToolsViewMode.COMPACT_3)
        repo.setToolsSortMode(ToolsSortMode.MOST_USED)

        val loaded = repo.preferences.first()
        assertEquals(ToolsViewMode.COMPACT_3, loaded.toolsViewMode)
        assertEquals(ToolsSortMode.MOST_USED, loaded.toolsSortMode)
    }
}
