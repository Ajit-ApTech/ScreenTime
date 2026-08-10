package com.screentime.admin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.screentime.admin.databinding.ActivityAdminLockBinding

class AdminLockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminLockBinding

    private var currentPinInput: String = ""
    private var tempPinForSetup: String? = null
    private var isChangePinMode: Boolean = false
    private var changePinState: ChangePinStep = ChangePinStep.VERIFY_OLD

    private enum class ChangePinStep {
        VERIFY_OLD,
        ENTER_NEW,
        CONFIRM_NEW
    }

    private val prefs by lazy { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminLockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isChangePinMode = intent.getBooleanExtra(EXTRA_CHANGE_PIN, false)

        setupKeypadListeners()
        updateUIState()
    }

    private fun isPinSet(): Boolean {
        val savedPin = prefs.getString(KEY_PIN, null)
        return !savedPin.isNullOrEmpty()
    }

    private fun getSavedPin(): String? {
        return prefs.getString(KEY_PIN, null)
    }

    private fun updateUIState() {
        currentPinInput = ""
        updateDots()
        binding.tvLockError.visibility = View.INVISIBLE

        if (isChangePinMode) {
            when (changePinState) {
                ChangePinStep.VERIFY_OLD -> {
                    binding.tvLockTitle.text = "CHANGE PASSCODE"
                    binding.tvLockSubtitle.text = "Enter Current PIN"
                }
                ChangePinStep.ENTER_NEW -> {
                    binding.tvLockTitle.text = "CHANGE PASSCODE"
                    binding.tvLockSubtitle.text = "Set New 4-Digit PIN"
                }
                ChangePinStep.CONFIRM_NEW -> {
                    binding.tvLockTitle.text = "CHANGE PASSCODE"
                    binding.tvLockSubtitle.text = "Confirm New 4-Digit PIN"
                }
            }
        } else if (!isPinSet()) {
            if (tempPinForSetup == null) {
                binding.tvLockTitle.text = "FIRST TIME SETUP"
                binding.tvLockSubtitle.text = "Set Master 4-Digit PIN"
            } else {
                binding.tvLockTitle.text = "FIRST TIME SETUP"
                binding.tvLockSubtitle.text = "Confirm 4-Digit PIN"
            }
        } else {
            binding.tvLockTitle.text = "ADMIN SECURITY"
            binding.tvLockSubtitle.text = "Enter 4-Digit PIN"
        }
    }

    private fun setupKeypadListeners() {
        val numberButtons = listOf(
            binding.btnKey0 to "0",
            binding.btnKey1 to "1",
            binding.btnKey2 to "2",
            binding.btnKey3 to "3",
            binding.btnKey4 to "4",
            binding.btnKey5 to "5",
            binding.btnKey6 to "6",
            binding.btnKey7 to "7",
            binding.btnKey8 to "8",
            binding.btnKey9 to "9"
        )

        for ((btn, digit) in numberButtons) {
            btn.setOnClickListener { onDigitPressed(digit) }
        }

        binding.btnKeyClear.setOnClickListener {
            currentPinInput = ""
            updateDots()
            binding.tvLockError.visibility = View.INVISIBLE
        }

        binding.btnKeyBackspace.setOnClickListener {
            if (currentPinInput.isNotEmpty()) {
                currentPinInput = currentPinInput.dropLast(1)
                updateDots()
                binding.tvLockError.visibility = View.INVISIBLE
            }
        }
    }

    private fun onDigitPressed(digit: String) {
        if (currentPinInput.length < 4) {
            currentPinInput += digit
            updateDots()
            binding.tvLockError.visibility = View.INVISIBLE

            if (currentPinInput.length == 4) {
                // Delay slightly so 4th dot animates before processing
                binding.root.postDelayed({
                    processPinEntry()
                }, 150)
            }
        }
    }

    private fun updateDots() {
        val dots = listOf(
            binding.viewDot1,
            binding.viewDot2,
            binding.viewDot3,
            binding.viewDot4
        )

        for (i in dots.indices) {
            dots[i].setBackgroundResource(
                if (i < currentPinInput.length) R.drawable.bg_pin_dot_on else R.drawable.bg_pin_dot_off
            )
        }
    }

    private fun processPinEntry() {
        val enteredPin = currentPinInput

        if (isChangePinMode) {
            handleChangePinFlow(enteredPin)
        } else if (!isPinSet()) {
            handleSetupFlow(enteredPin)
        } else {
            handleVerificationFlow(enteredPin)
        }
    }

    private fun handleSetupFlow(enteredPin: String) {
        if (tempPinForSetup == null) {
            // Step 1: Save first entry and ask for confirmation
            tempPinForSetup = enteredPin
            updateUIState()
        } else {
            // Step 2: Confirm match
            if (enteredPin == tempPinForSetup) {
                savePin(enteredPin)
                Toast.makeText(this, "Master PIN set successfully ✓", Toast.LENGTH_SHORT).show()
                proceedToMain()
            } else {
                showError("PINs do not match. Try again.")
                tempPinForSetup = null
                updateUIState()
            }
        }
    }

    private fun handleVerificationFlow(enteredPin: String) {
        val savedPin = getSavedPin()
        if (enteredPin == savedPin) {
            prefs.edit().putLong(KEY_LAST_UNLOCKED, System.currentTimeMillis()).apply()
            proceedToMain()
        } else {
            showError("Incorrect PIN. Please try again.")
            currentPinInput = ""
            updateDots()
        }
    }

    private fun handleChangePinFlow(enteredPin: String) {
        when (changePinState) {
            ChangePinStep.VERIFY_OLD -> {
                val savedPin = getSavedPin()
                if (enteredPin == savedPin) {
                    changePinState = ChangePinStep.ENTER_NEW
                    updateUIState()
                } else {
                    showError("Incorrect current PIN")
                    currentPinInput = ""
                    updateDots()
                }
            }
            ChangePinStep.ENTER_NEW -> {
                tempPinForSetup = enteredPin
                changePinState = ChangePinStep.CONFIRM_NEW
                updateUIState()
            }
            ChangePinStep.CONFIRM_NEW -> {
                if (enteredPin == tempPinForSetup) {
                    savePin(enteredPin)
                    Toast.makeText(this, "Passcode updated successfully ✓", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    showError("New PINs do not match. Try again.")
                    changePinState = ChangePinStep.ENTER_NEW
                    tempPinForSetup = null
                    updateUIState()
                }
            }
        }
    }

    private fun showError(msg: String) {
        binding.tvLockError.text = msg
        binding.tvLockError.visibility = View.VISIBLE
    }

    private fun savePin(pin: String) {
        prefs.edit()
            .putString(KEY_PIN, pin)
            .putLong(KEY_LAST_UNLOCKED, System.currentTimeMillis())
            .apply()
    }

    private fun proceedToMain() {
        val isFromMain = intent.getBooleanExtra(EXTRA_FROM_MAIN, false)
        if (!isFromMain) {
            val intent = Intent(this, AdminMainActivity::class.java)
            startActivity(intent)
        }
        finish()
    }

    companion object {
        const val PREFS_NAME = "screentime_admin_lock_prefs"
        const val KEY_PIN = "master_pin"
        const val KEY_LAST_UNLOCKED = "last_unlocked_ts"
        const val EXTRA_CHANGE_PIN = "extra_change_pin"
        const val EXTRA_FROM_MAIN = "extra_from_main"

        fun isUnlockedRecently(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastUnlocked = prefs.getLong(KEY_LAST_UNLOCKED, 0L)
            // Consider unlocked if unlocked in the last 15 seconds
            return System.currentTimeMillis() - lastUnlocked < 15_000L
        }
    }
}
