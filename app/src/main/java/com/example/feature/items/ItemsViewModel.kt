package com.example.feature.items

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.example.data.RateItemEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

@Immutable
data class ItemsScreenUiState(
    val items: List<RateItemEntity> = emptyList(),
    val selectedCategory: String = "ALL",
    val searchQuery: String = "",
    val isCompactView: Boolean = false,
    val isCounterMode: Boolean = false
)

/**
 * Unidirectional state & pricing math helpers for the Items feature (#7, #8, #9, #17).
 */
class ItemsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ItemsScreenUiState())
    val uiState: StateFlow<ItemsScreenUiState> = _uiState.asStateFlow()

    companion object {
        /**
         * Returns Triple(profitPerUnit, markupPercent, marginPercent)
         * Markup % = (Sell - Buy) / Buy * 100
         * Margin % = (Sell - Buy) / Sell * 100
         */
        fun computeProfitMarkupMargin(unitBuy: Double, unitSell: Double): Triple<Double, Double, Double> {
            if (unitBuy <= 0.0 || unitSell <= 0.0) return Triple(0.0, 0.0, 0.0)
            val profit = unitSell - unitBuy
            val markupPct = (profit / unitBuy) * 100.0
            val marginPct = (profit / unitSell) * 100.0
            return Triple(profit, markupPct, marginPct)
        }

        fun formatMarkupAndMargin(unitBuy: Double, unitSell: Double): String {
            val (_, markup, margin) = computeProfitMarkupMargin(unitBuy, unitSell)
            val sign = if (markup >= 0) "+" else ""
            return String.format(Locale.US, "Markup %s%.0f%% • Margin %s%.0f%%", sign, markup, sign, margin)
        }
    }
}
