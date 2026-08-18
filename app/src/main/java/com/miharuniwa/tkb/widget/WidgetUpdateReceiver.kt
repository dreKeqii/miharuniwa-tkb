package com.miharuniwa.tkb.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WidgetUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_DATE_CHANGED || 
            action == Intent.ACTION_TIME_CHANGED || 
            action == Intent.ACTION_TIMEZONE_CHANGED) {
            
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    ScheduleWidgetUpdater.updateAllWidgets(context)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}

object ScheduleWidgetUpdater {
    suspend fun updateAllWidgets(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        val widget = ScheduleWidget()
        val glanceIds = manager.getGlanceIds(widget.javaClass)
        
        for (id in glanceIds) {
            updateAppWidgetState(context, id) { prefs ->
                prefs[stringPreferencesKey("update_trigger")] = System.currentTimeMillis().toString()
            }
            widget.update(context, id)
        }
    }
}
