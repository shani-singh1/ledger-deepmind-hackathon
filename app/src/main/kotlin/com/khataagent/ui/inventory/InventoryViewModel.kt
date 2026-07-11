package com.khataagent.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khataagent.core.data.LedgerRepository
import com.khataagent.core.model.InventoryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs [InventoryScreen]: reactive stock list, low-stock items surfaced first. */
class InventoryViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    val inventory: StateFlow<List<InventoryItem>> = repository.observeInventory()
        .map { items -> items.sortedWith(compareByDescending<InventoryItem> { it.isLow }.thenBy { it.item.lowercase() }) }
        .onEach { _loading.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** [delta] is in the item's own unit (kg, pcs, L, ...); positive restocks, negative sells/uses. */
    fun adjust(item: InventoryItem, delta: Double) {
        viewModelScope.launch {
            repository.adjustStock(item.item, delta)
        }
    }
}
