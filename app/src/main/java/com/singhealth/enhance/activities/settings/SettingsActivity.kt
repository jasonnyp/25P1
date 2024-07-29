package com.singhealth.enhance.activities.settings

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.singhealth.enhance.R
import com.singhealth.enhance.activities.MainActivity
import com.singhealth.enhance.activities.authentication.LoginActivity
import com.singhealth.enhance.activities.error.errorDialogBuilder
import com.singhealth.enhance.activities.patient.RegistrationActivity
import com.singhealth.enhance.activities.settings.guide.UserGuideActivity
import com.singhealth.enhance.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Navigation drawer
        actionBarDrawerToggle = ActionBarDrawerToggle(this, binding.drawerLayout, 0, 0)
        binding.drawerLayout.addDrawerListener(actionBarDrawerToggle)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        actionBarDrawerToggle.syncState()

        binding.navigationView.setCheckedItem(R.id.item_settings)

        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.item_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }

                R.id.item_patient_registration -> {
                    startActivity(Intent(this, RegistrationActivity::class.java))
                    finish()
                    true
                }

                R.id.item_settings -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }

                else -> {
                    false
                }
            }
        }

        // Logout
        binding.logoutRL.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_logout)
                .setTitle("Logout confirmation")
                .setMessage("Are you sure you want to logout?")
                .setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
                .setPositiveButton("Yes") { _, _ ->
                    Firebase.auth.signOut()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
                .show()
        }

        // User guide
        binding.userGuideRL.setOnClickListener {
            startActivity(Intent(this, UserGuideActivity::class.java))
            finish()
        }

        // Language options
        binding.languageRL.setOnClickListener {
            startActivity(Intent(this, LanguageActivity::class.java))
            finish()
        }

        // Contact Helpdesk
        binding.contactHelpdeskRL.setOnClickListener {
            Toast.makeText(
                this,
                getString(R.string.settings_contact_helpdesk),
                Toast.LENGTH_SHORT
            ).show()
        }

        // About ENHANCe
        binding.aboutAppRL.setOnClickListener {
            startActivity(Intent(this, AboutAppActivity::class.java))
            finish()
        }
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (actionBarDrawerToggle.onOptionsItemSelected(item)) {
            true
        } else super.onOptionsItemSelected(item)
    }
}