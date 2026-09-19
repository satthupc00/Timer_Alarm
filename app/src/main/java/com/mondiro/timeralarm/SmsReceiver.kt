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

        // Khớp đúng mẫu SMS Mobifone thật (không dấu):
        // "...Han su dung den 15/09/2026 08:15:49. De kiem tra..."
        private val DATE_PATTERN: Pattern = Pattern.compile(
            "[Hh]an\\s+su\\s+dung\\s+den\\s+(\\d{1,2})/(\\d{1,2})/(\\d{4})\\s+(\\d{1,2}):(\\d{1,2}):(\\d{1,2})"
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

    /**
     * Trả về mốc thời gian hết hạn chính xác (đến từng giây) được nêu trong SMS,
     * ví dụ "Han su dung den 15/09/2026 08:15:49" -> 15/09/2026 08:15:49.
     */
    private fun extractExpiryDate(body: String): Long? {
        val matcher = DATE_PATTERN.matcher(body)
        if (!matcher.find()) return null

        val day = matcher.group(1)?.toIntOrNull() ?: return null
        val month = matcher.group(2)?.toIntOrNull() ?: return null
        val year = matcher.group(3)?.toIntOrNull() ?: return null
        val hour = matcher.group(4)?.toIntOrNull() ?: 23
        val minute = matcher.group(5)?.toIntOrNull() ?: 59
        val second = matcher.group(6)?.toIntOrNull() ?: 0

        if (day !in 1..31 || month !in 1..12) return null

        val cal = Calendar.getInstance()
        cal.set(year, month - 1, day, hour, minute, second)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
