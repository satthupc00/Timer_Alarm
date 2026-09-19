package com.mondiro.timeralarm

import android.content.Context
import android.provider.Telephony

/** Quét hộp thư SMS hiện có trên máy để tìm tin nhắn gói data gần nhất từ Mobifone. */
object SmsScanner {

    /**
     * Duyệt hộp thư đến (mới -> cũ), tìm tin đầu tiên từ đầu số 9199 khớp được ngày hết hạn.
     * Trả về mốc hết hạn (millis) nếu tìm thấy, null nếu không có tin nào phù hợp.
     */
    fun scanInboxForLatestExpiry(context: Context): Long? {
        val uri = Telephony.Sms.Inbox.CONTENT_URI
        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        val selection = "${Telephony.Sms.ADDRESS} LIKE ?"
        val selectionArgs = arrayOf("%${MobifoneSms.SENDER_FILTER}%")
        val sortOrder = "${Telephony.Sms.DATE} DESC"

        val resolver = context.contentResolver
        var cursor: android.database.Cursor? = null
        try {
            cursor = resolver.query(uri, projection, selection, selectionArgs, sortOrder)
            cursor ?: return null

            val bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY)
            while (cursor.moveToNext()) {
                val body = cursor.getString(bodyIndex) ?: continue
                val expiry = MobifoneSms.extractExpiryDate(body)
                if (expiry != null) return expiry
            }
        } catch (_: SecurityException) {
            // Chưa có quyền READ_SMS
            return null
        } finally {
            cursor?.close()
        }
        return null
    }
}
