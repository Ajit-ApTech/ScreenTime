package com.screentime.parent

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parent_splash)

        val auth = FirebaseAuth.getInstance()
        val handler = Handler(Looper.getMainLooper())

        handler.postDelayed({
            val intent = if (auth.currentUser != null) {
                Intent(this, FamilyDashboardActivity::class.java)
            } else {
                Intent(this, LoginActivity::class.java)
            }
            startActivity(intent)
            finish()
        }, 1500)
    }
}
