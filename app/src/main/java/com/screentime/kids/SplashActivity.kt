package com.screentime.kids

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.screentime.kids.helpers.FirebaseHelper

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val firebaseHelper = FirebaseHelper(this)
        val handler = Handler(Looper.getMainLooper())

        handler.postDelayed({
            val intent = if (firebaseHelper.isSetupDone()) {
                Intent(this, HomeActivity::class.java)
            } else {
                Intent(this, SetupActivity::class.java)
            }
            startActivity(intent)
            finish()
        }, 1500)
    }
}
