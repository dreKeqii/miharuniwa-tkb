package com.miharuniwa.tkb.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("app_prefs")

class AppPreferences(private val context: Context) {
    private val ROOT_URL_KEY = stringPreferencesKey("root_url")
    private val SAVE_LOCATION_KEY = stringPreferencesKey("save_location_uri")
    private val SAVE_GRADE_LOCATION_KEY = stringPreferencesKey("save_grade_location_uri")
    private val NOTIFICATION_ENABLED_KEY = booleanPreferencesKey("notification_enabled")
    private val FOLLOWED_SYSTEMS_KEY = stringPreferencesKey("followed_systems")
    private val PINNED_IMAGE_TIMESTAMP_KEY = androidx.datastore.preferences.core.longPreferencesKey("pinned_image_timestamp")
    private val GEMINI_API_KEY_KEY = stringPreferencesKey("gemini_api_key")
    private val GEMINI_MODEL_KEY = stringPreferencesKey("gemini_model")
    private val DEBUG_MODE_KEY = booleanPreferencesKey("debug_mode")
    private val DEFAULT_CLASS_KEY = stringPreferencesKey("default_class")
    private val DASHBOARD_CLASSES_KEY = androidx.datastore.preferences.core.stringSetPreferencesKey("dashboard_classes")

    // Báo thức
    private val ALARM_ENABLED_KEY = booleanPreferencesKey("alarm_enabled")
    private val ALARM_CLASS_NAME_KEY = stringPreferencesKey("alarm_class_name")
    private val ALARM_SYSTEM_KEY = stringPreferencesKey("alarm_system_key")
    private val ALARM_MORNING_TIME_KEY = stringPreferencesKey("alarm_morning_time") // Format HH:mm
    private val ALARM_AFTERNOON_TIME_KEY = stringPreferencesKey("alarm_afternoon_time")
    private val ALARM_SCHEDULE_JSON_KEY = stringPreferencesKey("alarm_schedule_json")

    val rootUrl: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[ROOT_URL_KEY]
    }
    
    val saveLocationUri: Flow<String?> = context.dataStore.data.map { it[SAVE_LOCATION_KEY] }
    val saveGradeLocationUri: Flow<String?> = context.dataStore.data.map { it[SAVE_GRADE_LOCATION_KEY] }
    
    val notificationEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[NOTIFICATION_ENABLED_KEY] ?: false
    }

    val followedSystems: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[FOLLOWED_SYSTEMS_KEY] ?: ""
        if (raw.isEmpty()) emptySet() else raw.split(",").toSet()
    }

    val pinnedImageTimestamp: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[PINNED_IMAGE_TIMESTAMP_KEY] ?: 0L
    }

    val geminiApiKey: Flow<String?> = context.dataStore.data.map { it[GEMINI_API_KEY_KEY] }
    
    val geminiModel: Flow<String?> = context.dataStore.data.map { it[GEMINI_MODEL_KEY] }

    val debugMode: Flow<Boolean> = context.dataStore.data.map { it[DEBUG_MODE_KEY] ?: false }
    val defaultClass: Flow<String?> = context.dataStore.data.map { it[DEFAULT_CLASS_KEY] }
    val dashboardClasses: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[DASHBOARD_CLASSES_KEY] ?: emptySet()
    }

    val alarmEnabled: Flow<Boolean> = context.dataStore.data.map { it[ALARM_ENABLED_KEY] ?: false }
    val alarmClassName: Flow<String?> = context.dataStore.data.map { it[ALARM_CLASS_NAME_KEY] }
    val alarmSystemKey: Flow<String?> = context.dataStore.data.map { it[ALARM_SYSTEM_KEY] }
    val alarmMorningTime: Flow<String> = context.dataStore.data.map { it[ALARM_MORNING_TIME_KEY] ?: "06:30" }
    val alarmAfternoonTime: Flow<String> = context.dataStore.data.map { it[ALARM_AFTERNOON_TIME_KEY] ?: "12:30" }
    val alarmScheduleJson: Flow<String?> = context.dataStore.data.map { it[ALARM_SCHEDULE_JSON_KEY] }

    suspend fun saveRootUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[ROOT_URL_KEY] = url
        }
    }
    
    suspend fun saveLocationUri(uri: String) {
        context.dataStore.edit { it[SAVE_LOCATION_KEY] = uri }
    }
    
    suspend fun saveGradeLocationUri(uri: String) {
        context.dataStore.edit { it[SAVE_GRADE_LOCATION_KEY] = uri }
    }

    suspend fun saveNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[NOTIFICATION_ENABLED_KEY] = enabled
        }
    }

    suspend fun toggleFollowSystem(base: String, systemType: String) {
        context.dataStore.edit { prefs ->
            val raw = prefs[FOLLOWED_SYSTEMS_KEY] ?: ""
            val current = if (raw.isEmpty()) mutableSetOf() else raw.split(",").toMutableSet()
            val itemKey = "$base|$systemType"
            if (current.contains(itemKey)) {
                current.remove(itemKey)
            } else {
                current.add(itemKey)
            }
            prefs[FOLLOWED_SYSTEMS_KEY] = current.joinToString(",")
        }
    }

    suspend fun clearLocationUri() {
        context.dataStore.edit { it.remove(SAVE_LOCATION_KEY) }
    }

    suspend fun clearGradeLocationUri() {
        context.dataStore.edit { it.remove(SAVE_GRADE_LOCATION_KEY) }
    }

    suspend fun updatePinnedImageTimestamp(timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[PINNED_IMAGE_TIMESTAMP_KEY] = timestamp
        }
    }

    suspend fun saveGeminiApiKey(key: String) {
        context.dataStore.edit { it[GEMINI_API_KEY_KEY] = key }
    }

    suspend fun saveGeminiModel(model: String) {
        context.dataStore.edit { it[GEMINI_MODEL_KEY] = model }
    }
    
    suspend fun setDebugMode(enabled: Boolean) {
        context.dataStore.edit { it[DEBUG_MODE_KEY] = enabled }
    }

    suspend fun saveDefaultClass(className: String) {
        context.dataStore.edit { it[DEFAULT_CLASS_KEY] = className }
    }

    suspend fun updateDashboardClasses(classes: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[DASHBOARD_CLASSES_KEY] = classes
        }
    }

    suspend fun saveAlarmConfig(
        enabled: Boolean,
        className: String,
        systemKey: String,
        morningTime: String,
        afternoonTime: String,
        scheduleJson: String
    ) {
        context.dataStore.edit { prefs ->
            prefs[ALARM_ENABLED_KEY] = enabled
            prefs[ALARM_CLASS_NAME_KEY] = className
            prefs[ALARM_SYSTEM_KEY] = systemKey
            prefs[ALARM_MORNING_TIME_KEY] = morningTime
            prefs[ALARM_AFTERNOON_TIME_KEY] = afternoonTime
            prefs[ALARM_SCHEDULE_JSON_KEY] = scheduleJson
        }
    }

    suspend fun resetFollowedSystems() {
        context.dataStore.edit { prefs ->
            prefs.remove(FOLLOWED_SYSTEMS_KEY)
        }
    }

    suspend fun clearAllPreferences() {
        context.dataStore.edit { it.clear() }
    }
}
