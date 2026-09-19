package com.mondiro.timeralarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import java.util.Calendar
import java.util.regex.Pattern

/**
 * Lắng nghe SMS đến. Chỉ xử lý tin từ đầu số Mobifone "9199".
 *
 * LƯU Ý QUAN TRỌNG: mẫu SMS thật của Mobifone có thể khác với regex bên dưới.
 * Regex hiện tại tìm 1 ngày dạng dd/MM hoặc dd/MM/yyyy trong nội dung tin nhắn,
 * và coi đó là ngày hết hạn gói. Nếu app không tự nhận đúng ngày, gửi cho Claude
 * 1 mẫu SMS thật (che số điện thoại) để chỉnh lại regex cho khớp.
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val SENDER_FILTER = "9199"

        // Bắt các dạng: "đến hết ngày 25/09/2026", "hết hạn 25/09", "HSD: 25/09/2026"...
        private val DATE_PATTERN: Pattern = Pattern.compile(
            "(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?"
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (msg in messages) {
            val sender = msg.originatingAddress ?: continue
            if (!sender.contains(SENDER_FILTER)) continue

            val body = msg.messageBody ?: continue
            handleMobifoneSms(context, body)
        }
    }

    private fun handleMobifoneSms(context: Context, body: String) {
        val newExpiry = extractExpiryDate(body) ?: return

        val prefs = Prefs.get(context)
        val oldExpiry = prefs.getLong(Prefs.KEY_EXPIRY_TIMESTAMP, 0L)

        // Lưu ngày hết hạn mới nhất tìm được trong SMS
        prefs.edit().putLong(Prefs.KEY_EXPIRY_TIMESTAMP, newExpiry).apply()

        // Nếu ngày hết hạn mới MUỘN HƠN ngày cũ đã lưu -> coi như vừa gia hạn/mua gói mới
        // -> hủy vòng lặp nhắc lại 6h hiện tại, đặt lại lịch theo mốc hạn mới
        if (prefs.getBoolean(Prefs.KEY_DATA_ON, false)) {
            AlarmScheduler.cancelDataExpiryCheck(context)
            AlarmScheduler.rescheduleDataExpiryFromStoredValue(context)
        }
    }

    /** Trả về mốc thời gian (millis, 23:59 ngày đó) nếu tìm thấy ngày trong nội dung SMS. */
    private fun extractExpiryDate(body: String): Long? {
        val matcher = DATE_PATTERN.matcher(body)
        var last: Long? = null

        while (matcher.find()) {
            val day = matcher.group(1)?.toIntOrNull() ?: continue
            val month = matcher.group(2)?.toIntOrNull() ?: continue
            if (day !in 1..31 || month !in 1..12) continue

            val cal = Calendar.getInstance()
            val yearGroup = matcher.group(3)
            val year = when {
                yearGroup == null -> cal.get(Calendar.YEAR)
                yearGroup.length == 2 -> 2000 + yearGroup.toInt()
                else -> yearGroup.toInt()
            }

            cal.set(year, month - 1, day, 23, 59, 0)
            cal.set(Calendar.MILLISECOND, 0)
            last = cal.timeInMillis
        }
        return last
    }
}
