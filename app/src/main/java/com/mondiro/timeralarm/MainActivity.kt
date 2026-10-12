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
import android.animation.Animator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var switchBreakfast: MaterialSwitch
    private lateinit var switchData: MaterialSwitch
    private lateinit var tvExpiryStatus: TextView
    private lateinit var btnRescan: MaterialButton
    private lateinit var btnTestRealBreakfast: MaterialButton
    private lateinit var btnTestRealData: MaterialButton
    private lateinit var ivPermOverlay: ImageView
    private lateinit var ivPermFsi: ImageView
    private lateinit var ivPermBattery: ImageView
    private lateinit var btnPermAll: MaterialButton
    private lateinit var switchSound: MaterialSwitch

    // Cập nhật app
    private lateinit var tvLatest: TextView
    private lateinit var btnCheckUpdate: View
    private lateinit var ivCheckUpdate: ImageView
    private lateinit var updateBanner: View
    private lateinit var tvUpdateTitle: TextView
    private lateinit var btnUpdateNow: MaterialButton
    private var updateSpin: Animator? = null
    private var updatePulse: Animator? = null
    private var pendingRelease: UpdateChecker.Release? = null
    private var pendingApk: File? = null // đã tải xong nhưng đang chờ cấp quyền cài
    private var downloading = false

    /** Trạng thái quyền lần trước, để chỉ "pop" chấm nào vừa chuyển sang OK. */
    private val lastPermOk = mutableMapOf<Int, Boolean>()

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
        ivPermOverlay = findViewById(R.id.ivPermOverlay)
        ivPermFsi = findViewById(R.id.ivPermFsi)
        ivPermBattery = findViewById(R.id.ivPermBattery)
        btnPermAll = findViewById(R.id.btnPermAll)
        switchSound = findViewById(R.id.switchSound)
        tvLatest = findViewById(R.id.tvLatest)
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate)
        ivCheckUpdate = findViewById(R.id.ivCheckUpdate)
        updateBanner = findViewById(R.id.updateBanner)
        tvUpdateTitle = findViewById(R.id.tvUpdateTitle)
        btnUpdateNow = findViewById(R.id.btnUpdateNow)

        permFlowActive = savedInstanceState?.getBoolean(STATE_PERM_FLOW) ?: false
        if (savedInstanceState == null) {
            Motion.rise(
                listOf(R.id.cardBreakfast, R.id.cardData, R.id.cardPerm, R.id.cardSound).map { findViewById(it) }
            )
        }

        val prefs = Prefs.get(this)
        switchBreakfast.isChecked = prefs.getBoolean(Prefs.KEY_BREAKFAST_ON, false)
        switchData.isChecked = prefs.getBoolean(Prefs.KEY_DATA_ON, false)
        updateExpiryStatusText()
        if (switchBreakfast.isChecked) NotificationHelper.showBreakfastStatus(this)

        switchBreakfast.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) Motion.wiggle(findViewById(R.id.badgeBreakfast))
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
            if (isChecked) Motion.wiggle(findViewById(R.id.badgeData))
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
            if (isChecked) Motion.wiggle(findViewById(R.id.badgeSound))
            prefs.edit().putBoolean(Prefs.KEY_SOUND_ON, isChecked).apply()
        }

        if (switchData.isChecked && !hasReceiveSmsPermission()) {
            requestNeededPermissions()
        }
        requestNotificationPermissionIfNeeded()

        btnCheckUpdate.setOnClickListener { checkForUpdate(manual = true) }
        btnUpdateNow.setOnClickListener { pendingRelease?.let { showUpdateDialog(it) } }
        // Mở app là tự kiểm tra bản mới (im lặng nếu lỗi mạng). Gập/mở máy tạo lại Activity -> không check lại.
        if (savedInstanceState == null) checkForUpdate(manual = false)
    }

    override fun onResume() {
        super.onResume()
        updateExpiryStatusText()
        updatePermissionStatus()
        if (permFlowActive) advancePermissionFlow()
        // Vừa cấp quyền "cài app không rõ nguồn" xong -> cài tiếp
        pendingApk?.let { apk ->
            if (packageManager.canRequestPackageInstalls()) {
                pendingApk = null
                UpdateChecker.install(this, apk)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_PERM_FLOW, permFlowActive)
    }

    // ---------- Cập nhật app qua GitHub Releases ----------

    private fun checkForUpdate(manual: Boolean) {
        if (updateSpin != null || downloading) return
        updateSpin = Motion.spin(ivCheckUpdate)
        UpdateChecker.check { result ->
            if (isFinishing || isDestroyed) return@check
            updateSpin?.cancel()
            updateSpin = null
            ivCheckUpdate.animate().rotation(0f).setDuration(200).start()
            when (result) {
                is UpdateChecker.Result.Latest -> {
                    updateBanner.visibility = View.GONE
                    if (tvLatest.visibility != View.VISIBLE) {
                        tvLatest.visibility = View.VISIBLE
                        Motion.pop(tvLatest, 0.6f)
                    }
                    if (manual) {
                        Toast.makeText(this, getString(R.string.update_latest_toast, BuildConfig.VERSION_NAME), Toast.LENGTH_SHORT).show()
                    }
                }
                is UpdateChecker.Result.Available -> {
                    tvLatest.visibility = View.GONE
                    pendingRelease = result.release
                    tvUpdateTitle.text = getString(R.string.update_available, result.release.version)
                    if (updateBanner.visibility != View.VISIBLE) {
                        updateBanner.visibility = View.VISIBLE
                        Motion.rise(listOf(updateBanner))
                    }
                    if (updatePulse == null) updatePulse = Motion.pulse(btnUpdateNow)
                    if (manual) showUpdateDialog(result.release)
                }
                is UpdateChecker.Result.Error -> {
                    if (manual) {
                        Toast.makeText(this, getString(R.string.update_failed, result.message), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun showUpdateDialog(release: UpdateChecker.Release) {
        updatePulse?.cancel()
        updatePulse = null
        btnUpdateNow.scaleX = 1f
        btnUpdateNow.scaleY = 1f
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.update_dialog_title, release.version))
            .setMessage(release.notes.ifBlank { getString(R.string.update_available, release.version) })
            .setPositiveButton(R.string.update_install) { _, _ -> startDownload(release) }
            .setNegativeButton(R.string.update_later, null)
            .show()
    }

    private fun startDownload(release: UpdateChecker.Release) {
        if (downloading) return
        downloading = true
        btnUpdateNow.isEnabled = false
        btnUpdateNow.text = getString(R.string.update_downloading, 0)
        UpdateChecker.download(this, release,
            onProgress = { pct -> btnUpdateNow.text = getString(R.string.update_downloading, pct) },
            onDone = { apk, err ->
                downloading = false
                btnUpdateNow.isEnabled = true
                btnUpdateNow.setText(R.string.update_now)
                if (apk == null) {
                    Toast.makeText(this, getString(R.string.update_download_failed, err ?: ""), Toast.LENGTH_LONG).show()
                } else if (!UpdateChecker.install(this, apk)) {
                    pendingApk = apk
                    Toast.makeText(this, R.string.update_need_unknown_sources, Toast.LENGTH_LONG).show()
                }
            }
        )
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
        setPermDot(ivPermOverlay, overlayOk)
        setPermDot(ivPermFsi, fsiOk)
        setPermDot(ivPermBattery, battOk)
        val allOk = overlayOk && fsiOk && battOk
        btnPermAll.setText(if (allOk) R.string.perm_btn_all_ok else R.string.perm_btn_all_bad)
        if (allOk) {
            btnPermAll.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.mint))
            btnPermAll.setTextColor(ContextCompat.getColor(this, R.color.green))
            btnPermAll.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.mint_line))
            btnPermAll.strokeWidth = resources.displayMetrics.density.toInt()
            btnPermAll.setIconResource(R.drawable.ic_check)
            btnPermAll.iconTint = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.green))
            btnPermAll.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        } else {
            btnPermAll.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.red))
            btnPermAll.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            btnPermAll.strokeWidth = 0
            btnPermAll.icon = null
        }
    }

    private fun setPermDot(iv: ImageView, ok: Boolean) {
        val before = lastPermOk[iv.id]
        lastPermOk[iv.id] = ok
        iv.setBackgroundResource(if (ok) R.drawable.bg_dot_ok else R.drawable.bg_dot_bad)
        iv.setImageResource(if (ok) R.drawable.ic_check else R.drawable.ic_cross)
        iv.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, if (ok) R.color.green else R.color.red))
        if (ok && before == false) Motion.pop(iv)
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
        btnRescan.setText(R.string.btn_rescanning)
        val spin: ValueAnimator? = Motion.spinDrawable(btnRescan.icon)
        bgExecutor.execute {
            val expiry = SmsScanner.scanInboxForLatestExpiry(this)
            mainHandler.post {
                spin?.cancel()
                btnRescan.setText(R.string.btn_rescan)
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
            getString(R.string.data_status_fmt, sdf.format(expiry))
        }
    }

    companion object {
        private const val STATE_PERM_FLOW = "perm_flow_active"
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
