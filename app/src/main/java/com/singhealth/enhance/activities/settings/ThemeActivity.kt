package com.singhealth.enhance.activities.settings

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.auth.auth
import com.singhealth.enhance.security.LogOutTimerUtil
import com.singhealth.enhance.activities.authentication.LoginActivity
import com.singhealth.enhance.databinding.ActivityThemeBinding


class ThemeActivity : AppCompatActivity(), LogOutTimerUtil.LogOutListener {
    private lateinit var binding: ActivityThemeBinding

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
        binding = ActivityThemeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                startActivity(Intent(this@ThemeActivity, SettingsActivity::class.java))
                finish()
            }
        })

        binding.enhanceLight.setOnClickListener {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            saveThemePreference(false)
            startActivity(Intent(this@ThemeActivity, SettingsActivity::class.java))
            finish()
        }

        binding.enhanceDark.setOnClickListener {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            saveThemePreference(true)
            startActivity(Intent(this@ThemeActivity, SettingsActivity::class.java))
            finish()
        }
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

    private fun isDarkMode(): Boolean {
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return when (nightModeFlags) {
            Configuration.UI_MODE_NIGHT_YES -> true
            Configuration.UI_MODE_NIGHT_NO -> false
            Configuration.UI_MODE_NIGHT_UNDEFINED -> false
            else -> false
        }
    }

    private fun saveThemePreference(isDarkMode: Boolean) {
        val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("is_dark_mode", isDarkMode)
            apply()
        }
    }
}