package com.example.feature.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.entity.ToolUsageEntity
import com.example.core.datastore.PreferencesRepository
import com.example.core.datastore.ToolsSortMode
import com.example.core.datastore.ToolsViewMode
import com.example.data.repository.LibraryRepository
import com.example.data.repository.ToolUsageRepository
import com.example.domain.model.LibraryDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ToolItemUi(
    val definition: ToolDefinition,
    val usageCount: Int,
    val isFavorite: Boolean,
    val favoriteOrder: Int
)

data class ToolsHomeUiState(
    val searchQuery: String = "",
    val viewMode: ToolsViewMode = ToolsViewMode.GRID_2,
    val sortMode: ToolsSortMode = ToolsSortMode.DEFAULT,
    val recentDocuments: List<LibraryDocument> = emptyList(),
    val favoriteTools: List<ToolItemUi> = emptyList(),
    val pdfTools: List<ToolItemUi> = emptyList(),
    val documentTools: List<ToolItemUi> = emptyList()
)

@HiltViewModel
class ToolsHomeViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val toolUsageRepository: ToolUsageRepository,
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    val uiState: StateFlow<ToolsHomeUiState> = combine(
        searchQuery,
        preferencesRepository.preferences,
        toolUsageRepository.observeAllToolUsage(),
        libraryRepository.observeRecentDocuments(limit = 10)
    ) { query, prefs, usageList, recentDocs ->
        val usageMap: Map<String, ToolUsageEntity> = usageList.associateBy { it.toolId }
        val hasCustomFavorites = usageList.any { it.isFavorite }

        val enriched = ToolCatalog.allTools.map { def ->
            val usage = usageMap[def.id]
            val isFav = usage?.isFavorite ?: (!hasCustomFavorites && def.defaultFavorite)
            val favOrder = usage?.favoriteOrder ?: def.defaultOrder
            ToolItemUi(
                definition = def,
                usageCount = usage?.usageCount ?: 0,
                isFavorite = isFav,
                favoriteOrder = favOrder
            )
        }

        val trimmed = query.trim().lowercase()
        val filtered = if (trimmed.isEmpty()) {
            enriched
        } else {
            enriched.filter {
                it.definition.title.lowercase().contains(trimmed) ||
                    it.definition.subtitle.lowercase().contains(trimmed)
            }
        }

        val comparator: Comparator<ToolItemUi> = when (prefs.toolsSortMode) {
            ToolsSortMode.DEFAULT -> compareBy { it.definition.defaultOrder }
            ToolsSortMode.A_TO_Z -> compareBy { it.definition.title.lowercase() }
            ToolsSortMode.MOST_USED -> compareByDescending<ToolItemUi> { it.usageCount }
                .thenBy { it.definition.defaultOrder }
        }

        val favorites = enriched
            .filter { it.isFavorite }
            .sortedWith(compareBy<ToolItemUi> { it.favoriteOrder }.thenBy { it.definition.defaultOrder })

        val pdfTools = filtered
            .filter { it.definition.section == ToolSection.PDF_TOOLS }
            .sortedWith(comparator)

        val docTools = filtered
            .filter { it.definition.section == ToolSection.DOCUMENTS_AND_NOTES }
            .sortedWith(comparator)

        ToolsHomeUiState(
            searchQuery = query,
            viewMode = prefs.toolsViewMode,
            sortMode = prefs.toolsSortMode,
            recentDocuments = recentDocs,
            favoriteTools = favorites,
            pdfTools = pdfTools,
            documentTools = docTools
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ToolsHomeUiState()
    )

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun cycleViewMode(): ToolsViewMode {
        val current = uiState.value.viewMode
        val next = when (current) {
            ToolsViewMode.GRID_2 -> ToolsViewMode.LIST
            ToolsViewMode.LIST -> ToolsViewMode.COMPACT_3
            ToolsViewMode.COMPACT_3 -> ToolsViewMode.GRID_2
        }
        viewModelScope.launch {
            preferencesRepository.setToolsViewMode(next)
        }
        return next
    }

    fun setSortMode(mode: ToolsSortMode) {
        viewModelScope.launch {
            preferencesRepository.setToolsSortMode(mode)
        }
    }

    fun recordToolLaunch(toolId: String) {
        viewModelScope.launch {
            toolUsageRepository.recordToolLaunch(toolId)
        }
    }

    fun toggleFavorite(item: ToolItemUi) {
        viewModelScope.launch {
            val currentFavs = uiState.value.favoriteTools.map { it.definition.id }
            if (item.isFavorite) {
                // Ensure existing default favorites are persisted if user edits for the first time
                val remaining = currentFavs.filter { it != item.definition.id }
                toolUsageRepository.setFavorite(item.definition.id, favorite = false, order = 0)
                toolUsageRepository.reorderFavorites(remaining)
            } else {
                val updated = currentFavs + item.definition.id
                toolUsageRepository.reorderFavorites(updated)
            }
        }
    }

    fun moveFavorite(fromIndex: Int, toIndex: Int) {
        val current = uiState.value.favoriteTools.map { it.definition.id }.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) return
        val moved = current.removeAt(fromIndex)
        current.add(toIndex, moved)
        viewModelScope.launch {
            toolUsageRepository.reorderFavorites(current)
        }
    }

    fun loadSamplePdf() {
        viewModelScope.launch {
            libraryRepository.importBundledSamplePdf()
        }
    }
}
