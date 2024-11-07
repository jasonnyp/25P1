package com.singhealth.enhance.activities.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.auth
import com.singhealth.enhance.R
import com.singhealth.enhance.security.LogOutTimerUtil
import com.singhealth.enhance.activities.authentication.LoginActivity
import com.singhealth.enhance.activities.validation.errorDialogBuilder
import com.singhealth.enhance.databinding.ActivityLanguageBinding
import com.yariksoffice.lingver.Lingver

class LanguageActivity : AppCompatActivity(), LogOutTimerUtil.LogOutListener {
    private lateinit var binding: ActivityLanguageBinding

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
        val curLanguage: String = Lingver.getInstance().getLocale().toString()

        super.onCreate(savedInstanceState)
        binding = ActivityLanguageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                startActivity(Intent(this@LanguageActivity, SettingsActivity::class.java))
                finish()
            }
        })

        binding.enhanceEnglish.setOnClickListener {
            languageBuilder(this, curLanguage, "en")
        }

        binding.enhanceChineseSimplified.setOnClickListener {
            languageBuilder(this, curLanguage, "zh", "CN")
        }

        binding.enhanceChineseTraditional.setOnClickListener {
            languageBuilder(this, curLanguage, "zh", "TW")
        }

        binding.enhanceMalay.setOnClickListener {
            // languageBuilder(this, curLanguage, "ms", "MY")
            errorDialogBuilder(this, getString(R.string.language_unsupported_header), getString(R.string.language_unsupported_body))
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

    private fun languageBuilder(context: Context, curLanguage: String, locale: String) {
        println(curLanguage)
        println(locale)
        if (curLanguage != locale) {
            Lingver.getInstance().setLocale(context, locale)
            startActivity(Intent(context, SettingsActivity::class.java))
            finish()
        } else {
            println(curLanguage)
            errorDialogBuilder(
                context,
                getString(R.string.language_current_error_header),
                getString(R.string.language_current_error_body)
            )
        }
    }

    private fun languageBuilder(context: Context, curLanguage: String, locale: String, region: String) {
        println(curLanguage)
        println(locale)
        println(region)

        var langSplit = ""

        if (curLanguage.contains('_')) {
            langSplit = curLanguage.substring(3, 4).lowercase()
        }

        if (curLanguage != locale + "_" + region || curLanguage == locale && langSplit != region) {
            Lingver.getInstance().setLocale(context, locale, region)
            startActivity(Intent(context, SettingsActivity::class.java))
            finish()
        } else {
            println(curLanguage + "Test")
            errorDialogBuilder(
                context,
                getString(R.string.language_current_error_header),
                getString(R.string.language_current_error_body)
            )
        }
    }
}
