package com.singhealth.enhance.activities.settings

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.auth
import com.singhealth.enhance.security.LogOutTimerUtil
import com.singhealth.enhance.activities.authentication.LoginActivity
import com.singhealth.enhance.databinding.ActivityPrivacyStatementBinding

class PrivacyStatementActivity : AppCompatActivity(), LogOutTimerUtil.LogOutListener {
    private lateinit var binding: ActivityPrivacyStatementBinding

    // Used for Session Timeout
    override fun onUserInteraction() {
        super.onUserInteraction()
        LogOutTimerUtil.startLogoutTimer(this, this)
    }

    override fun doLogout() {
        com.google.firebase.Firebase.auth.signOut()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPrivacyStatementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                startActivity(Intent(this@PrivacyStatementActivity, SettingsActivity::class.java))
                finish()
            }
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                finish()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }
}