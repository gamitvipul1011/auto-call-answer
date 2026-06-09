package com.autocallanswer.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.autocallanswer.AutoCallApp
import com.autocallanswer.R
import com.autocallanswer.databinding.ActivityMainBinding
import com.autocallanswer.service.CallMonitorService
import com.autocallanswer.util.CallEventLogger
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val preferences by lazy { (application as AutoCallApp).preferences }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        if (granted) {
            Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.permissions_required, Toast.LENGTH_LONG).show()
        }
        refreshUi()
    }

    private val defaultDialerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshUi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        createNotificationChannel()
        bindSettings()
        setupActions()
        observeLogs()
        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun bindSettings() {
        binding.enableSwitch.isChecked = preferences.autoAnswerEnabled
        binding.apiKeyInput.setText(preferences.openAiApiKey)
        binding.greetingInput.setText(preferences.greetingMessage)
    }

    private fun setupActions() {
        binding.enableSwitch.setOnCheckedChangeListener { _, isChecked ->
            preferences.autoAnswerEnabled = isChecked
            if (isChecked) {
                if (hasCorePermissions()) {
                    startCallMonitorService()
                } else {
                    requestCorePermissions()
                    binding.enableSwitch.isChecked = false
                    preferences.autoAnswerEnabled = false
                }
            } else {
                stopService(Intent(this, CallMonitorService::class.java))
            }
            refreshUi()
        }

        binding.permissionsButton.setOnClickListener { requestCorePermissions() }
        binding.defaultDialerButton.setOnClickListener { requestDefaultDialerRole() }
        binding.saveButton.setOnClickListener {
            preferences.openAiApiKey = binding.apiKeyInput.text?.toString().orEmpty()
            preferences.greetingMessage = binding.greetingInput.text?.toString().orEmpty()
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            refreshUi()
        }
    }

    private fun observeLogs() {
        lifecycleScope.launch {
            CallEventLogger.events.collect { events ->
                binding.logText.text = events
            }
        }
    }

    private fun refreshUi() {
        val enabled = preferences.autoAnswerEnabled
        binding.statusText.setText(if (enabled) R.string.status_enabled else R.string.status_disabled)

    }

    private fun requestCorePermissions() {
        val permissions = buildList {
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.ANSWER_PHONE_CALLS)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                add(Manifest.permission.READ_CALL_LOG)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun hasCorePermissions(): Boolean {
        val required = listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.RECORD_AUDIO
        )
        return required.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startCallMonitorService() {
        val intent = Intent(this, CallMonitorService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun requestDefaultDialerRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
            ) {
                defaultDialerLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
                return
            }
        }

        val telecomManager = getSystemService(TelecomManager::class.java)
        if (packageName != telecomManager.defaultDialerPackage) {
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            }
            defaultDialerLauncher.launch(intent)
            return
        }

        Toast.makeText(this, "Already set as default phone app", Toast.LENGTH_SHORT).show()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CallMonitorService.CHANNEL_ID,
            "Call monitor",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
