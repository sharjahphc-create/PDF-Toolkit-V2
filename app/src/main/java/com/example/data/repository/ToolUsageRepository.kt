package com.example.data.repository

import com.example.core.database.dao.ToolUsageDao
import com.example.core.database.entity.ToolUsageEntity
import com.example.core.datastore.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolUsageRepository @Inject constructor(
    private val toolUsageDao: ToolUsageDao,
    private val preferencesRepository: PreferencesRepository
) {
    fun observeAllToolUsage(): Flow<List<ToolUsageEntity>> = toolUsageDao.observeAllToolUsage()

    suspend fun recordToolLaunch(toolId: String) {
        val deviceId = preferencesRepository.getOrCreateDeviceId()
        val now = System.currentTimeMillis()
        val existing = toolUsageDao.getToolUsage(toolId)
        toolUsageDao.upsertToolUsage(
            ToolUsageEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                toolId = toolId,
                usageCount = (existing?.usageCount ?: 0) + 1,
                isFavorite = existing?.isFavorite ?: false,
                favoriteOrder = existing?.favoriteOrder ?: 0,
                lastUsedAt = now,
                updatedAt = now,
                deviceId = deviceId,
                isDeleted = false
            )
        )
    }

    suspend fun setFavorite(toolId: String, favorite: Boolean, order: Int) {
        val deviceId = preferencesRepository.getOrCreateDeviceId()
        val now = System.currentTimeMillis()
        val existing = toolUsageDao.getToolUsage(toolId)
        toolUsageDao.upsertToolUsage(
            ToolUsageEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                toolId = toolId,
                usageCount = existing?.usageCount ?: 0,
                isFavorite = favorite,
                favoriteOrder = order,
                lastUsedAt = existing?.lastUsedAt ?: 0L,
                updatedAt = now,
                deviceId = deviceId,
                isDeleted = false
            )
        )
    }

    suspend fun reorderFavorites(orderedToolIds: List<String>) {
        val deviceId = preferencesRepository.getOrCreateDeviceId()
        val now = System.currentTimeMillis()
        orderedToolIds.forEachIndexed { index, toolId ->
            val existing = toolUsageDao.getToolUsage(toolId)
            toolUsageDao.upsertToolUsage(
                ToolUsageEntity(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    toolId = toolId,
                    usageCount = existing?.usageCount ?: 0,
                    isFavorite = true,
                    favoriteOrder = index,
                    lastUsedAt = existing?.lastUsedAt ?: 0L,
                    updatedAt = now,
                    deviceId = deviceId,
                    isDeleted = false
                )
            )
        }
    }
}
