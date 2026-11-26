package com.materials.stockalert.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SendChannel {
    SMS, WHATSAPP
}

@Entity(tableName = "merchants")
data class Merchant(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val preferredChannel: SendChannel = SendChannel.WHATSAPP
)

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = Merchant::class,
            parentColumns = ["id"],
            childColumns = ["merchantId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("merchantId")]
)
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val unit: String = "حبة",
    val merchantId: Long
)

@Entity(
    tableName = "scheduled_orders",
    foreignKeys = [
        ForeignKey(
            entity = Merchant::class,
            parentColumns = ["id"],
            childColumns = ["merchantId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("merchantId")]
)
data class ScheduledOrder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchantId: Long,
    val messageBody: String,
    val triggerAtMillis: Long,
    val channel: SendChannel,
    val isSent: Boolean = false
)
