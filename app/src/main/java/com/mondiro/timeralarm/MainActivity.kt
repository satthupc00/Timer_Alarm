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
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
    private lateinit var btnPermOverlay: Button
    private lateinit var btnPermFsi: Button
    private lateinit var btnPermBattery: Button
    private lateinit var btnShowLog: Button
    private lateinit var switchSound: SwitchMaterial

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
        btnPermOverlay = findViewById(R.id.btnPermOverlay)
        btnPermFsi = findViewById(R.id.btnPermFsi)
        btnPermBattery = findViewById(R.id.btnPermBattery)
        btnShowLog = findViewById(R.id.btnShowLog)
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

        btnPermOverlay.setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Quyền này đã được bật rồi", Toast.LENGTH_SHORT).show()
            } else {
                openOverlaySettings()
            }
        }
        btnPermFsi.setOnClickListener {
            if (AlarmNotificationHelper.hasFullScreenIntentPermission(this)) {
                Toast.makeText(this, "Quyền này đã được bật rồi", Toast.LENGTH_SHORT).show()
            } else {
                AlarmNotificationHelper.openFullScreenIntentSettings(this)
            }
        }
        btnPermBattery.setOnClickListener {
            if (isIgnoringBatteryOptimizations()) {
                Toast.makeText(this, "Quyền này đã được bật rồi", Toast.LENGTH_SHORT).show()
            } else {
                requestIgnoreBatteryOptimizations()
            }
        }
        btnShowLog.setOnClickListener { showDiagnosticLog() }

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
        updatePermissionButtons()
    }

    private fun scheduleRealPathTest(type: String) {
        val ok = AlarmScheduler.scheduleTestAlarm(this, type, 15_000L)
        Toast.makeText(
            this,
            if (ok) R.string.test_real_scheduled else R.string.test_real_failed,
            Toast.LENGTH_LONG
        ).show()
    }

    // ---------- Quyền để màn hình báo tự bật ----------

    private fun updatePermissionButtons() {
        btnPermOverlay.setText(if (Settings.canDrawOverlays(this)) R.string.perm_overlay_ok else R.string.perm_overlay_bad)
        if (Build.VERSION.SDK_INT >= 34) {
            btnPermFsi.visibility = View.VISIBLE
            btnPermFsi.setText(
                if (AlarmNotificationHelper.hasFullScreenIntentPermission(this)) R.string.perm_fsi_ok else R.string.perm_fsi_bad
            )
        } else {
            btnPermFsi.visibility = View.GONE
        }
        btnPermBattery.setText(if (isIgnoringBatteryOptimizations()) R.string.perm_batt_ok else R.string.perm_batt_bad)
    }

    private fun openOverlaySettings() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            Toast.makeText(this, "Không mở được màn hình cài đặt: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestIgnoreBatteryOptimizations() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(this, "Không mở được màn hình cài đặt pin", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ---------- Nhật ký chẩn đoán ----------

    private fun showDiagnosticLog() {
        val header = "Trạng thái máy hiện tại:\n${DiagLog.deviceState(this)}\n\n— Nhật ký (mới nhất ở cuối) —\n"
        val log = DiagLog.read(this).ifBlank { "(trống)" }
        val tv = TextView(this)
        tv.text = header + log
        tv.textSize = 12f
        tv.setTextIsSelectable(true)
        tv.setPadding(48, 32, 48, 32)
        val scroll = ScrollView(this)
        scroll.addView(tv)
        AlertDialog.Builder(this)
            .setTitle("Nhật ký chẩn đoán")
            .setView(scroll)
            .setPositiveButton("Đóng", null)
            .setNeutralButton("Xoá nhật ký") { _, _ -> DiagLog.clear(this) }
            .show()
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
