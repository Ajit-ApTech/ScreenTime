package com.screentime.kids

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.screentime.kids.databinding.ActivityLinkFamilyBinding
import com.screentime.kids.helpers.FirebaseHelper

class LinkFamilyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLinkFamilyBinding
    private val firebaseHelper by lazy { FirebaseHelper(this) }
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLinkFamilyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnLink.setOnClickListener {
            val code = binding.etInviteCode.text?.toString()?.trim()?.uppercase() ?: ""
            if (code.length != 6) {
                Toast.makeText(this, "Please enter a valid 6-character code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            linkFamily(code)
        }
    }

    private fun linkFamily(code: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnLink.isEnabled = false

        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            com.google.firebase.auth.FirebaseAuth.getInstance().signInAnonymously()
                .addOnSuccessListener {
                    performSearch(code)
                }
                .addOnFailureListener { e ->
                    binding.progressBar.visibility = View.GONE
                    binding.btnLink.isEnabled = true
                    Toast.makeText(this, "Authentication failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } else {
            performSearch(code)
        }
    }

    private fun performSearch(code: String) {
        db.collection("families")
            .whereEqualTo("inviteCode", code)
            .get()
            .addOnSuccessListener { querySnapshot ->
                binding.progressBar.visibility = View.GONE
                binding.btnLink.isEnabled = true

                if (querySnapshot.isEmpty) {
                    Toast.makeText(this, "Invalid invite code. Please check and try again.", Toast.LENGTH_LONG).show()
                } else {
                    val familyDocument = querySnapshot.documents[0]
                    val familyId = familyDocument.id
                    firebaseHelper.saveFamilyId(familyId)
                    Toast.makeText(this, "Successfully linked to Family!", Toast.LENGTH_SHORT).show()
                    startHomeActivity()
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.btnLink.isEnabled = true
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun startHomeActivity() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
