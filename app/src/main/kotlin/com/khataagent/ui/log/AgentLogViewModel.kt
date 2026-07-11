package com.khataagent.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khataagent.core.data.LedgerRepository
import com.khataagent.core.model.DeferralEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class AgentLogViewModel(repository: LedgerRepository) : ViewModel() {
    val deferrals: StateFlow<List<DeferralEntry>> = repository.observeDeferrals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
