package com.appssimple.alarmsimple.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Сохраненная метка (NFC или штрихкод) в библиотеке пользователя.
 * Позволяет давать меткам человекопонятные имена ("Зубная паста", "Кружка на кухне")
 * и повторно использовать их при создании новых будильников без повторного сканирования.
 */
@Entity(tableName = "saved_quests")
data class SavedQuestEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String, // "NFC" or "BARCODE"
    val title: String, // e.g. "Кружка на кухне", "Кофе"
    val data: String, // Tag ID (hex) or Barcode value
    val createdAt: Long = System.currentTimeMillis()
)
