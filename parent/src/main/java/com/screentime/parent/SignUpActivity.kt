package com.screentime.parent

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.screentime.parent.databinding.ActivitySignupBinding
import java.util.Random

class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnSignup.setOnClickListener {
            val familyName = binding.etFamilyName.text?.toString()?.trim() ?: ""
            val email = binding.etEmail.text?.toString()?.trim() ?: ""
            val password = binding.etPassword.text?.toString()?.trim() ?: ""

            if (familyName.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            signUpParent(familyName, email, password)
        }

        binding.btnGoToLogin.setOnClickListener {
            finish()
        }
    }

    private fun signUpParent(familyName: String, email: String, password: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSignup.isEnabled = false

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val parentUid = authResult.user?.uid ?: ""
                val inviteCode = generateInviteCode()

                val familyData = mapOf(
                    "familyName" to familyName,
                    "parentEmail" to email,
                    "inviteCode" to inviteCode,
                    "createdAt" to System.currentTimeMillis()
                )

                db.collection("families")
                    .document(parentUid)
                    .set(familyData)
                    .addOnSuccessListener {
                        binding.progressBar.visibility = View.GONE
                        binding.btnSignup.isEnabled = true
                        
                        val intent = Intent(this, FamilyDashboardActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        binding.progressBar.visibility = View.GONE
                        binding.btnSignup.isEnabled = true
                        Toast.makeText(this, "Failed to save family details: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.btnSignup.isEnabled = true
                Toast.makeText(this, "Signup Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val sb = StringBuilder()
        val random = Random()
        for (i in 0 until 6) {
            sb.append(chars[random.nextInt(chars.length)])
        }
        return sb.toString()
    }
}
