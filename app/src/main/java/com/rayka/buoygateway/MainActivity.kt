package com.rayka.buoygateway

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.SubscriptionManager
import android.view.View
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.rayka.buoygateway.databinding.ActivityMainBinding
import com.rayka.buoygateway.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val simCheckboxes = mutableMapOf<Int, CheckBox>()

    private val requiredPermissions: Array<String>
        get() {
            val perms = mutableListOf(
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_PHONE_STATE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            return perms.toTypedArray()
        }

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            populateSimList()
            startGatewayService()
        } else {
            Toast.makeText(this, "بدون این مجوزها امکان کارکرد وجود ندارد", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Prefs.isConsentGiven(this)) {
            showMainSection()
        }

        binding.btnAgree.setOnClickListener {
            Prefs.setConsentGiven(this, true)
            showMainSection()
        }

        loadSettingsIntoUi()

        if (hasAllPermissions()) {
            populateSimList()
        }

        binding.btnSaveSettings.setOnClickListener { saveSettingsFromUi() }

        binding.btnStartStop.setOnClickListener {
            if (Prefs.isServiceOn(this)) {
                stopService(Intent(this, GatewayService::class.java))
                Prefs.setServiceOn(this, false)
                updateStatusUi()
            } else {
                if (Prefs.getCloudFunctionUrl(this).isNullOrBlank() || Prefs.getCloudSharedSecret(this).isNullOrBlank()) {
                    Toast.makeText(this, "اول آدرس و رمز Edge Function را ذخیره کنید", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                ensurePermissionsThenStart()
            }
        }

        updateStatusUi()
    }

    override fun onResume() {
        super.onResume()
        updateStatusUi()
    }

    private fun showMainSection() {
        binding.consentSection.visibility = View.GONE
        binding.mainSection.visibility = View.VISIBLE
    }

    private fun hasAllPermissions(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensurePermissionsThenStart() {
        if (hasAllPermissions()) {
            populateSimList()
            startGatewayService()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    private fun startGatewayService() {
        saveSettingsFromUi()
        val intent = Intent(this, GatewayService::class.java)
        ContextCompat.startForegroundService(this, intent)
        Prefs.setServiceOn(this, true)
        updateStatusUi()
    }

    /** لیست سیم‌کارت‌های فعال گوشی را می‌خواند و به‌صورت چک‌باکس نشان می‌دهد */
    private fun populateSimList() {
        binding.simListContainer.removeAllViews()
        simCheckboxes.clear()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val subscriptionManager = getSystemService(SubscriptionManager::class.java)
        val activeSubs = try {
            subscriptionManager?.activeSubscriptionInfoList
        } catch (e: SecurityException) {
            null
        } ?: emptyList()

        val monitored = Prefs.getMonitoredSubIds(this)

        if (activeSubs.isEmpty()) {
            val cb = CheckBox(this)
            cb.text = "سیمی پیدا نشد — همه‌ی پیامک‌ها بررسی می‌شوند"
            cb.isEnabled = false
            binding.simListContainer.addView(cb)
            return
        }

        activeSubs.forEach { sub ->
            val cb = CheckBox(this)
            val label = "${sub.displayName ?: "SIM"} (${sub.number ?: "?"}) — slot ${sub.simSlotIndex + 1}"
            cb.text = label
            cb.isChecked = monitored.isEmpty() || sub.subscriptionId in monitored
            binding.simListContainer.addView(cb)
            simCheckboxes[sub.subscriptionId] = cb
        }
    }

    private fun loadSettingsIntoUi() {
        binding.editSmsPrefix.setText(Prefs.getSmsPrefix(this))
        binding.editCloudUrl.setText(Prefs.getCloudFunctionUrl(this) ?: "")
        binding.editCloudSecret.setText(Prefs.getCloudSharedSecret(this) ?: "")
        binding.editLocalUrl.setText(Prefs.getLocalServerUrl(this) ?: "")
        binding.editLocalSecret.setText(Prefs.getLocalSharedSecret(this) ?: "")
        binding.switchActAsLocalServer.isChecked = Prefs.getActAsLocalServer(this)
        binding.editLocalPort.setText(Prefs.getLocalServerPort(this).toString())
    }

    private fun saveSettingsFromUi() {
        val prefix = binding.editSmsPrefix.text.toString().ifBlank { "RAYKA:" }
        Prefs.setSmsPrefix(this, prefix)

        val selectedSubs = simCheckboxes.filterValues { it.isChecked }.keys
        Prefs.setMonitoredSubIds(this, selectedSubs)

        Prefs.setCloudFunctionUrl(this, binding.editCloudUrl.text.toString())
        Prefs.setCloudSharedSecret(this, binding.editCloudSecret.text.toString())
        Prefs.setLocalServerUrl(this, binding.editLocalUrl.text.toString())
        Prefs.setLocalSharedSecret(this, binding.editLocalSecret.text.toString())
        Prefs.setActAsLocalServer(this, binding.switchActAsLocalServer.isChecked)
        Prefs.setLocalServerPort(this, binding.editLocalPort.text.toString().toIntOrNull() ?: 8787)

        Toast.makeText(this, "ذخیره شد", Toast.LENGTH_SHORT).show()
    }

    private fun updateStatusUi() {
        val on = Prefs.isServiceOn(this)
        binding.textStatus.text = getString(if (on) R.string.status_on else R.string.status_off)
        binding.btnStartStop.text = getString(if (on) R.string.btn_stop_service else R.string.btn_start_service)

        val lastSync = Prefs.getLastCloudSyncTime(this)
        binding.textLastSync.text = if (lastSync > 0) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            "${getString(R.string.label_last_sync)} ${sdf.format(Date(lastSync))}"
        } else {
            "${getString(R.string.label_last_sync)} هنوز انجام نشده"
        }

        CoroutineScope(Dispatchers.IO).launch {
            val count = AppDatabase.get(applicationContext).pendingMessageDao().countUnsent()
            withContext(Dispatchers.Main) {
                binding.textPendingCount.text = "${getString(R.string.label_pending_count)} $count"
            }
        }
    }
}
