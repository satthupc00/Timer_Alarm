package com.mondiro.timeralarm

import android.content.Context
import java.util.Calendar
import java.util.regex.Pattern

/**
 * Logic dùng chung để nhận diện & xử lý SMS gói data từ Mobifone (đầu số 9199),
 * dùng cho cả 2 nguồn: SMS mới đến (SmsReceiver) và quét lại hộp thư cũ (SmsScanner).
 */
object MobifoneSms {

    const val SENDER_FILTER = "9199"

    // Khớp đúng mẫu SMS Mobifone thật (không dấu):
    // "...Han su dung den 15/09/2026 08:15:49. De kiem tra..."
    val DATE_PATTERN: Pattern = Pattern.compile(
        "[Hh]an\\s+su\\s+dung\\s+den\\s+(\\d{1,2})/(\\d{1,2})/(\\d{4})\\s+(\\d{1,2}):(\\d{1,2}):(\\d{1,2})"
    )

    /** Trả về mốc hết hạn chính xác (millis) nếu nội dung SMS khớp mẫu, ngược lại null. */
    fun extractExpiryDate(body: String): Long? {
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

    /**
     * Lưu mốc hết hạn mới nhất phát hiện được và đặt lại lịch nhắc tương ứng.
     * onlyIfNewer = true: chỉ áp dụng nếu mốc mới muộn hơn mốc đang lưu (dùng khi có SMS mới đến,
     * coi là "vừa gia hạn"). onlyIfNewer = false: luôn ghi đè (dùng khi quét lại từ hộp thư,
     * lấy đúng tin mới nhất tìm thấy làm trạng thái hiện tại).
     */
    fun applyExpiry(context: Context, newExpiry: Long, onlyIfNewer: Boolean) {
        val prefs = Prefs.get(context)
        val oldExpiry = prefs.getLong(Prefs.KEY_EXPIRY_TIMESTAMP, 0L)

        if (onlyIfNewer && oldExpiry > 0L && newExpiry <= oldExpiry) return

        prefs.edit().putLong(Prefs.KEY_EXPIRY_TIMESTAMP, newExpiry).apply()

        if (prefs.getBoolean(Prefs.KEY_DATA_ON, false)) {
            AlarmScheduler.cancelDataExpiryCheck(context)
            AlarmScheduler.rescheduleDataExpiryFromStoredValue(context)
        }
    }
}
