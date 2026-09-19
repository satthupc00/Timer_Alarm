package com.mondiro.timeralarm

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var switchBreakfast: SwitchMaterial
    private lateinit var switchData: SwitchMaterial
    private lateinit var tvExpiryStatus: TextView

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val smsGranted = results[Manifest.permission.RECEIVE_SMS] ?: false
        if (!smsGranted) {
            // Không có quyền SMS thì tắt lại switch data
            switchData.isChecked = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        switchBreakfast = findViewById(R.id.switchBreakfast)
        switchData = findViewById(R.id.switchData)
        tvExpiryStatus = findViewById(R.id.tvExpiryStatus)

        val prefs = Prefs.get(this)
        switchBreakfast.isChecked = prefs.getBoolean(Prefs.KEY_BREAKFAST_ON, false)
        switchData.isChecked = prefs.getBoolean(Prefs.KEY_DATA_ON, false)
        updateExpiryStatusText()

        switchBreakfast.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(Prefs.KEY_BREAKFAST_ON, isChecked).apply()
            if (isChecked) {
                AlarmScheduler.scheduleBreakfastAlarm(this)
            } else {
                AlarmScheduler.cancelBreakfastAlarm(this)
            }
        }

        switchData.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !hasSmsPermission()) {
                requestNeededPermissions()
            }
            prefs.edit().putBoolean(Prefs.KEY_DATA_ON, isChecked).apply()
            if (isChecked) {
                AlarmScheduler.rescheduleDataExpiryFromStoredValue(this)
            } else {
                AlarmScheduler.cancelDataExpiryCheck(this)
            }
        }

        if (switchData.isChecked && !hasSmsPermission()) {
            requestNeededPermissions()
        }
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        updateExpiryStatusText()
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

    private fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) ==
                PackageManager.PERMISSION_GRANTED

    private fun requestNeededPermissions() {
        val perms = mutableListOf(Manifest.permission.RECEIVE_SMS)
        permissionLauncher.launch(perms.toTypedArray())
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
