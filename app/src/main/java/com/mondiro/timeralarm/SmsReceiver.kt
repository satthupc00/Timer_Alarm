package com.mondiro.timeralarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/** Lắng nghe SMS mới đến. Chỉ xử lý tin từ đầu số Mobifone "9199". */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (msg in messages) {
            val sender = msg.originatingAddress ?: continue
            if (!sender.contains(MobifoneSms.SENDER_FILTER)) continue

            val body = msg.messageBody ?: continue
            val expiry = MobifoneSms.extractExpiryDate(body) ?: continue

            // SMS mới đến -> coi là tin mới nhất, luôn ghi đè (nếu muộn hơn thì reset vòng nhắc lặp)
            MobifoneSms.applyExpiry(context, expiry, onlyIfNewer = true)
        }
    }
}
