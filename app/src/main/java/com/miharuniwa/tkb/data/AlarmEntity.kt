package com.miharuniwa.tkb.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey val id: String, // format: "systemKey|className"
    val systemKey: String,
    val className: String,
    val morningTime: String,
    val afternoonTime: String,
    val isEnabled: Boolean,
    val scheduleJson: String,
    val weekId: String = ""
)

