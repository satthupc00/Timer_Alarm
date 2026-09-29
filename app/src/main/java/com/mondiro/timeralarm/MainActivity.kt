package com.mondiro.timeralarm

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var switchBreakfast: SwitchMaterial
    private lateinit var switchData: SwitchMaterial
    private lateinit var tvExpiryStatus: TextView
    private lateinit var btnRescan: Button
    private lateinit var btnTestRealBreakfast: Button
    private lateinit var btnTestRealData: Button
    private lateinit var tvPermStatus: TextView
    private lateinit var btnPermAll: Button
    private lateinit var switchSound: SwitchMaterial

    // true khi đang giữa chừng "bật đủ quyền": mỗi lần quay lại app, tự mở tiếp màn cài đặt
    // của quyền kế tiếp còn thiếu, để 1 lần bấm là đi hết được cả 3 quyền.
    private var permFlowActive = false

    private val bgExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val smsGranted = results[Manifest.permission.RECEIVE_SMS] ?: hasReceiveSmsPermission()
        if (!smsGranted) {
            switchData.isChecked = false
        }
    }

    private val rescanPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            performRescan()
        } else {
            Toast.makeText(this, R.string.rescan_need_permission, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        switchBreakfast = findViewById(R.id.switchBreakfast)
        switchData = findViewById(R.id.switchData)
        tvExpiryStatus = findViewById(R.id.tvExpiryStatus)
        btnRescan = findViewById(R.id.btnRescan)
        btnTestRealBreakfast = findViewById(R.id.btnTestRealBreakfast)
        btnTestRealData = findViewById(R.id.btnTestRealData)
        tvPermStatus = findViewById(R.id.tvPermStatus)
        btnPermAll = findViewById(R.id.btnPermAll)
        switchSound = findViewById(R.id.switchSound)

        val prefs = Prefs.get(this)
        switchBreakfast.isChecked = prefs.getBoolean(Prefs.KEY_BREAKFAST_ON, false)
        switchData.isChecked = prefs.getBoolean(Prefs.KEY_DATA_ON, false)
        updateExpiryStatusText()
        if (switchBreakfast.isChecked) NotificationHelper.showBreakfastStatus(this)

        switchBreakfast.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(Prefs.KEY_BREAKFAST_ON, isChecked).apply()
            if (isChecked) {
                AlarmScheduler.scheduleBreakfastAlarm(this)
                NotificationHelper.showBreakfastStatus(this)
            } else {
                AlarmScheduler.cancelBreakfastAlarm(this)
                NotificationHelper.hideBreakfastStatus(this)
            }
        }

        switchData.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !hasReceiveSmsPermission()) {
                requestNeededPermissions()
            }
            prefs.edit().putBoolean(Prefs.KEY_DATA_ON, isChecked).apply()
            if (isChecked) {
                AlarmScheduler.rescheduleDataExpiryFromStoredValue(this)
            } else {
                AlarmScheduler.cancelDataExpiryCheck(this)
            }
        }

        btnRescan.setOnClickListener {
            if (hasReadSmsPermission()) {
                performRescan()
            } else {
                rescanPermissionLauncher.launch(Manifest.permission.READ_SMS)
            }
        }

        // Test: hẹn giờ qua AlarmManager, đi đúng đường báo thật chạy ngầm
        btnTestRealBreakfast.setOnClickListener { scheduleRealPathTest(FullScreenAlarmActivity.TYPE_BREAKFAST) }
        btnTestRealData.setOnClickListener { scheduleRealPathTest(FullScreenAlarmActivity.TYPE_DATA_EXPIRY) }

        btnPermAll.setOnClickListener {
            permFlowActive = true
            advancePermissionFlow()
        }

        switchSound.isChecked = prefs.getBoolean(Prefs.KEY_SOUND_ON, false)
        switchSound.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(Prefs.KEY_SOUND_ON, isChecked).apply()
        }

        if (switchData.isChecked && !hasReceiveSmsPermission()) {
            requestNeededPermissions()
        }
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        updateExpiryStatusText()
        updatePermissionStatus()
        if (permFlowActive) advancePermissionFlow()
    }

    private fun scheduleRealPathTest(type: String) {
        val ok = AlarmScheduler.scheduleTestAlarm(this, type, 15_000L)
        Toast.makeText(
            this,
            if (ok) R.string.test_real_scheduled else R.string.test_real_failed,
            Toast.LENGTH_LONG
        ).show()
    }

    // ---------- Quyền để màn hình báo tự bật (gộp 3 quyền vào 1 nút) ----------

    private fun hasOverlayPermission() = Settings.canDrawOverlays(this)
    private fun hasFsiPermission() = AlarmNotificationHelper.hasFullScreenIntentPermission(this)
    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun updatePermissionStatus() {
        val overlayOk = hasOverlayPermission()
        val fsiOk = hasFsiPermission()
        val battOk = isIgnoringBatteryOptimizations()
        tvPermStatus.text = getString(
            R.string.perm_status_line,
            if (overlayOk) "✅" else "❌",
            if (fsiOk) "✅" else "❌",
            if (battOk) "✅" else "❌"
        )
        val allOk = overlayOk && fsiOk && battOk
        btnPermAll.setText(if (allOk) R.string.perm_btn_all_ok else R.string.perm_btn_all_bad)
    }

    /** Mở màn cài đặt của quyền ĐẦU TIÊN còn thiếu; nếu đã đủ cả 3 thì dừng luồng. */
    private fun advancePermissionFlow() {
        updatePermissionStatus()
        when {
            !hasOverlayPermission() -> openOverlaySettings()
            !hasFsiPermission() -> AlarmNotificationHelper.openFullScreenIntentSettings(this)
            !isIgnoringBatteryOptimizations() -> requestIgnoreBatteryOptimizations()
            else -> {
                permFlowActive = false
                Toast.makeText(this, "Đã bật đủ quyền", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openOverlaySettings() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            permFlowActive = false
            Toast.makeText(this, "Không mở được màn hình cài đặt: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                permFlowActive = false
                Toast.makeText(this, "Không mở được màn hình cài đặt pin", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun performRescan() {
        btnRescan.isEnabled = false
        bgExecutor.execute {
            val expiry = SmsScanner.scanInboxForLatestExpiry(this)
            mainHandler.post {
                btnRescan.isEnabled = true
                if (expiry != null) {
                    MobifoneSms.applyExpiry(this, expiry, onlyIfNewer = false)
                    updateExpiryStatusText()
                    Toast.makeText(this, R.string.rescan_found, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.rescan_not_found, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateExpiryStatusText() {
        val prefs = Prefs.get(this)
        val expiry = prefs.getLong(Prefs.KEY_EXPIRY_TIMESTAMP, 0L)
        tvExpiryStatus.text = if (expiry <= 0L) {
            getString(R.string.data_status_none)
        } else {
            val sdf = SimpleDateFormat("HH:mm 'ngày' dd/MM/yyyy", Locale.getDefault())
            "Gói data hết hạn: ${sdf.format(expiry)}"
        }
    }

    private fun hasReceiveSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) ==
                PackageManager.PERMISSION_GRANTED

    private fun hasReadSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED

    private fun requestNeededPermissions() {
        // Xin cả 2 quyền cùng lúc (nhận tin mới + đọc tin cũ) để người dùng chỉ phải xác nhận 1 lần
        permissionLauncher.launch(
            arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
        )
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }
    }
}
