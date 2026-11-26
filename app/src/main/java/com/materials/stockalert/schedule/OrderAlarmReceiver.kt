package com.materials.stockalert.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import com.materials.stockalert.data.AppDatabase
import com.materials.stockalert.data.SendChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URLEncoder

class OrderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val orderId = intent.getLongExtra(OrderScheduler.EXTRA_ORDER_ID, -1L)
        if (orderId == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val order = db.orderDao().getOrderById(orderId) ?: return@launch
                val merchant = db.merchantDao().getById(order.merchantId) ?: return@launch

                when (order.channel) {
                    SendChannel.SMS -> {
                        MessageSender.sendSms(merchant.phone, order.messageBody)
                    }
                    SendChannel.WHATSAPP -> {
                        MessageSender.openWhatsAppChat(context, merchant.phone, order.messageBody)
                    }
                }

                db.orderDao().update(order.copy(isSent = true))
            } finally {
                pendingResult.finish()
            }
        }
    }
}

object MessageSender {
    fun sendSms(phone: String, message: String) {
        val cleanPhone = phone.filter { it.isDigit() || it == '+' }
        val smsManager = SmsManager.getDefault()
        val parts = smsManager.divideMessage(message)
        smsManager.sendMultipartTextMessage(cleanPhone, null, parts, null, null)
    }

    fun openWhatsAppChat(context: Context, phone: String, message: String) {
        val digitsOnly = phone.filter { it.isDigit() }
        val normalizedPhone = if (digitsOnly.length == 9 && digitsOnly.startsWith("7")) {
            "967$digitsOnly"
        } else {
            digitsOnly
        }

        val encodedText = URLEncoder.encode(message, "UTF-8")
        val uri = Uri.parse("https://wa.me/$normalizedPhone?text=$encodedText")
        val waIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(waIntent)
    }
}
