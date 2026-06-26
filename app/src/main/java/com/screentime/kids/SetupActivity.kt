package com.screentime.kids

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.screentime.kids.databinding.ActivitySetupBinding
import com.screentime.kids.helpers.FirebaseHelper

class SetupActivity : AppCompatActivity() {

    private var _binding: ActivitySetupBinding? = null
    private val binding get() = _binding!!

    private val firebaseHelper by lazy { FirebaseHelper(this) }

    private val permissionAppUsage = "android.permission.PACKAGE_USAGE_STATS"
    private val permissionCallLog = Manifest.permission.READ_CALL_LOG
    private val permissionContacts = Manifest.permission.READ_CONTACTS
    private val permissionSms = Manifest.permission.READ_SMS

    private var isAppUsageGranted = false
    private var isCallLogGranted = false
    private var isContactsGranted = false
    private var isSmsGranted = false

    private val permissions = arrayOf(
        permissionCallLog,
        permissionContacts,
        permissionSms
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        updatePermissionButtons()
    }

    private fun setupListeners() {
        binding.btnAppUsage.setOnClickListener {
            openAppUsageSettings()
        }

        binding.btnCallLog.setOnClickListener {
            requestPermission(permissionCallLog, REQUEST_CALL_LOG)
        }

        binding.btnContacts.setOnClickListener {
            requestPermission(permissionContacts, REQUEST_CONTACTS)
        }

        binding.btnSms.setOnClickListener {
            requestPermission(permissionSms, REQUEST_SMS)
        }

        binding.etName.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                checkAllConditions()
            }
        })

        binding.btnGetStarted.setOnClickListener {
            startHomeActivity()
        }
    }

    private fun openAppUsageSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        startActivity(intent)
    }

    private fun requestPermission(permission: String, requestCode: Int) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                Snackbar.make(binding.root, "Please grant this permission", Snackbar.LENGTH_SHORT).show()
            }
            ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
        } else {
            onPermissionGranted(permission)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            onPermissionGranted(permissions[0])
        }
    }

    override fun onResume() {
        super.onResume()
        checkAppUsagePermission()
    }

    private fun checkAppUsagePermission() {
        val appOps = getSystemService(android.content.Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        }
        isAppUsageGranted = mode == android.app.AppOpsManager.MODE_ALLOWED
        updatePermissionButtons()
        checkAllConditions()
    }

    private fun onPermissionGranted(permission: String) {
        when (permission) {
            permissionCallLog -> {
                isCallLogGranted = true
                binding.btnCallLog.text = "Allow Call Log Access ✅"
            }
            permissionContacts -> {
                isContactsGranted = true
                binding.btnContacts.text = "Allow Contacts Access ✅"
            }
            permissionSms -> {
                isSmsGranted = true
                binding.btnSms.text = "Allow SMS Access ✅"
            }
        }
        updatePermissionButtons()
        checkAllConditions()
    }

    private fun updatePermissionButtons() {
        // Always check the ACTUAL system permission state, not just in-memory flags.
        // This ensures the UI is correct even after the app is restarted.
        isCallLogGranted = ContextCompat.checkSelfPermission(this, permissionCallLog) == PackageManager.PERMISSION_GRANTED
        isContactsGranted = ContextCompat.checkSelfPermission(this, permissionContacts) == PackageManager.PERMISSION_GRANTED
        isSmsGranted = ContextCompat.checkSelfPermission(this, permissionSms) == PackageManager.PERMISSION_GRANTED

        if (isAppUsageGranted) binding.btnAppUsage.text = "Allow App Usage Access ✅"
        if (isCallLogGranted) binding.btnCallLog.text = "Allow Call Log Access ✅"
        if (isContactsGranted) binding.btnContacts.text = "Allow Contacts Access ✅"
        if (isSmsGranted) binding.btnSms.text = "Allow SMS Access ✅"
    }

    private fun checkAllConditions() {
        val nameEntered = binding.etName.text?.isNotBlank() == true
        val allPermissions = isAppUsageGranted && isCallLogGranted && isContactsGranted && isSmsGranted
        binding.btnGetStarted.isEnabled = nameEntered && allPermissions
    }

    private fun startHomeActivity() {
        val name = binding.etName.text?.toString()?.trim() ?: ""
        firebaseHelper.saveChildName(name)
        firebaseHelper.markSetupDone()

        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }

    companion object {
        private const val REQUEST_APP_USAGE = 1001
        private const val REQUEST_CALL_LOG = 1002
        private const val REQUEST_CONTACTS = 1003
        private const val REQUEST_SMS = 1004
    }
}
