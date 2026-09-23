package com.lobsterai.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lobsterai.app.data.preferences.SettingsStore
import com.lobsterai.app.domain.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val repository: AppRepository,
    private val settingsStore: SettingsStore
) : ViewModel() {
    val darkMode: StateFlow<Boolean> = settingsStore.darkMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch {
            val lobsters = repository.observeLobsters().first()
            val current = settingsStore.activeLobsterId.first()
            if (lobsters.isEmpty()) {
                val id = repository.createLobster(
                    name = "洛洛",
                    prompt = "你是一只聪明、克制、可靠的AI龙虾伙伴。回答准确、自然，不要用廉价卖萌语气。"
                )
                settingsStore.setActiveLobster(id)
            } else if (current == null || lobsters.none { it.id == current }) {
                settingsStore.setActiveLobster(lobsters.first().id)
            }
        }
    }
}