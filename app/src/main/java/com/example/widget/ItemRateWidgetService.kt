package com.example.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.R
import com.example.data.AppPreferencesManager
import com.example.data.LedgerDatabase
import com.example.data.RateItemEntity
import java.util.Locale

class ItemRateWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return ItemRateWidgetFactory(applicationContext)
    }
}

class ItemRateWidgetFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<RateItemEntity> = emptyList()
    private var currencySymbol: String = "₹"

    override fun onCreate() {
        fetchData()
    }

    override fun onDataSetChanged() {
        fetchData()
    }

    private fun fetchData() {
        try {
            val db = LedgerDatabase.getDatabase(context)
            items = db.ledgerDao().getAllRateItemsDirect()
            val prefs = AppPreferencesManager(context)
            currencySymbol = prefs.currencySymbol
        } catch (e: Exception) {
            e.printStackTrace()
            items = emptyList()
        }
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews? {
        if (position < 0 || position >= items.size) return null
        val item = items[position]

        val views = RemoteViews(context.packageName, R.layout.widget_rate_item_row)

        // Left info
        views.setTextViewText(R.id.widget_item_name, item.name)
        val unitText = if (item.qty == 1.0) "Per ${item.unit}" else "${item.qty} ${item.unit}"
        views.setTextViewText(R.id.widget_item_unit, unitText)

        // Right info (Sell Price in primary accent, Buy Price in muted gray)
        views.setTextViewText(R.id.widget_item_sell_price, "SELL: $currencySymbol${item.getDisplaySellPrice()}")
        val buyFormatted = if (item.buyRate % 1.0 == 0.0) {
            item.buyRate.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", item.buyRate)
        }
        views.setTextViewText(R.id.widget_item_buy_price, "Buy: $currencySymbol$buyFormatted")

        // Fill-in Intent for tapping the item row to deep link to edit / history modal
        val fillInIntent = Intent().apply {
            putExtra(ItemRateWidgetProvider.EXTRA_ITEM_ID, item.id)
            putExtra(ItemRateWidgetProvider.EXTRA_ITEM_NAME, item.name)
        }
        views.setOnClickFillInIntent(R.id.widget_row_container, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false
}
