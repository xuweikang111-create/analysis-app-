package com.lobsterai.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "app_settings")

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val activeLobster = longPreferencesKey("active_lobster")
        val activeConversation = longPreferencesKey("active_conversation")
        val activeModel = longPreferencesKey("active_model")
        val darkMode = booleanPreferencesKey("dark_mode")
        val clipboardDetection = booleanPreferencesKey("clipboard_detection")
        val thinkingEnabled = booleanPreferencesKey("thinking_enabled")
        val visionEnabled = booleanPreferencesKey("vision_enabled")
        val autoKnowledgeEnabled = booleanPreferencesKey("auto_knowledge_enabled")
    }

    val activeLobsterId: Flow<Long?> = context.dataStore.data.map { it[Keys.activeLobster] }
    val activeConversationId: Flow<Long?> = context.dataStore.data.map { it[Keys.activeConversation] }
    val activeModelId: Flow<Long?> = context.dataStore.data.map { it[Keys.activeModel] }
    val darkMode: Flow<Boolean> = context.dataStore.data.map { it[Keys.darkMode] ?: false }
    val clipboardDetection: Flow<Boolean> = context.dataStore.data.map { it[Keys.clipboardDetection] ?: true }
    val thinkingEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.thinkingEnabled] ?: false }
    val visionEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.visionEnabled] ?: true }
    val autoKnowledgeEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.autoKnowledgeEnabled] ?: true }

    suspend fun setActiveLobster(id: Long?) = context.dataStore.edit { prefs ->
        if (id == null) prefs.remove(Keys.activeLobster) else prefs[Keys.activeLobster] = id
    }

    suspend fun setActiveConversation(id: Long?) = context.dataStore.edit { prefs ->
        if (id == null) prefs.remove(Keys.activeConversation) else prefs[Keys.activeConversation] = id
    }

    suspend fun setActiveModel(id: Long?) = context.dataStore.edit { prefs ->
        if (id == null) prefs.remove(Keys.activeModel) else prefs[Keys.activeModel] = id
    }

    suspend fun setDarkMode(enabled: Boolean) = context.dataStore.edit { it[Keys.darkMode] = enabled }
    suspend fun setClipboardDetection(enabled: Boolean) = context.dataStore.edit { it[Keys.clipboardDetection] = enabled }
    suspend fun setThinkingEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.thinkingEnabled] = enabled }
    suspend fun setVisionEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.visionEnabled] = enabled }
    suspend fun setAutoKnowledgeEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.autoKnowledgeEnabled] = enabled }
}
