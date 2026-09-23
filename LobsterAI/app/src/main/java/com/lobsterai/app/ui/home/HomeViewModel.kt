package com.lobsterai.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lobsterai.app.data.preferences.SettingsStore
import com.lobsterai.app.domain.model.DailyProgress
import com.lobsterai.app.domain.model.Lobster
import com.lobsterai.app.domain.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: AppRepository,
    private val settings: SettingsStore
) : ViewModel() {
    private val busy = MutableStateFlow(false)

    val state: StateFlow<HomeUiState> = combine(
        repository.observeLobsters(),
        settings.activeLobsterId,
        busy
    ) { lobsters, activeId, isBusy ->
        HomeUiState(
            lobsters = lobsters,
            selected = lobsters.firstOrNull { it.id == activeId } ?: lobsters.firstOrNull(),
            busy = isBusy
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    val daily: StateFlow<DailyProgress> = settings.activeLobsterId
        .flatMapLatest { id -> if (id == null) flowOf(DailyProgress(false, false, false, false)) else repository.observeDailyProgress(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyProgress(false, false, false, false))

    fun select(id: Long) = viewModelScope.launch {
        settings.setActiveLobster(id)
        settings.setActiveConversation(null)
    }

    fun addLobster(name: String, prompt: String) = viewModelScope.launch {
        val id = repository.createLobster(name, prompt)
        settings.setActiveLobster(id)
        settings.setActiveConversation(null)
    }

    fun updatePrompt(prompt: String) = viewModelScope.launch {
        state.value.selected?.let { repository.updateLobster(it.copy(prompt = prompt.trim())) }
    }

    fun deleteSelected() = viewModelScope.launch {
        val current = state.value.selected ?: return@launch
        if (state.value.lobsters.size <= 1) return@launch
        repository.deleteLobster(current)
        settings.setActiveLobster(state.value.lobsters.firstOrNull { it.id != current.id }?.id)
        settings.setActiveConversation(null)
    }

    fun perform(action: String) = viewModelScope.launch {
        val lobster = state.value.selected ?: return@launch
        busy.value = true
        try {
            val first = repository.markDailyAction(lobster.id, action)
            when (action) {
                "checkin" -> repository.applyGrowth(lobster.id, exp = if (first) 25 else 5, mood = 4, intimacy = 1)
                "feed" -> repository.applyGrowth(lobster.id, exp = if (first) 15 else 3, satiety = 20, mood = 2)
                "interact" -> repository.applyGrowth(lobster.id, exp = if (first) 20 else 4, mood = 8, intimacy = 4)
            }
        } finally {
            busy.value = false
        }
    }
}

data class HomeUiState(
    val lobsters: List<Lobster> = emptyList(),
    val selected: Lobster? = null,
    val busy: Boolean = false
)
