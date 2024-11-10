package com.singhealth.enhance.activities.settings

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.auth
import android.Manifest
import android.widget.Toast
import com.singhealth.enhance.activities.authentication.LoginActivity
import com.singhealth.enhance.databinding.ActivityContactHelpdeskBinding
import com.singhealth.enhance.security.LogOutTimerUtil

class ContactHelpdesk : AppCompatActivity(), LogOutTimerUtil.LogOutListener {
    private lateinit var binding: ActivityContactHelpdeskBinding

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
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_contact_helpdesk)
//
//        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        super.onCreate(savedInstanceState)
        binding = ActivityContactHelpdeskBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                startActivity(Intent(this@ContactHelpdesk, SettingsActivity::class.java))
                finish()
            }
        })

        binding.enhanceCallMrJason.setOnClickListener {
            val phoneNumber = "tel:+6565501712"  // Replace with the actual number
            val callIntent = Intent(Intent.ACTION_CALL)
            callIntent.data = Uri.parse(phoneNumber)

            // Check if the permission to make a call is granted
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                // Permission is granted, initiate the call
                startActivity(callIntent)
            } else {
                // Request the permission if not already granted
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), 1)
            }
        }

        binding.enhanceCallMrLoh.setOnClickListener {
            val phoneNumber = "tel:+6565500737"  // Replace with the actual number
            val callIntent = Intent(Intent.ACTION_CALL)
            callIntent.data = Uri.parse(phoneNumber)

            // Check if the permission to make a call is granted
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                // Permission is granted, initiate the call
                startActivity(callIntent)
            } else {
                // Request the permission if not already granted
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), 2)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Permission was granted, initiate the call
            val phoneNumber = "tel:+6565501712"
            val callIntent = Intent(Intent.ACTION_CALL)
            callIntent.data = Uri.parse(phoneNumber)

            // Ensure the permission is granted before starting the activity
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                startActivity(callIntent)
            }
        } else if (requestCode == 2 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Permission was granted, initiate the call
            val phoneNumber = "tel:+6565500737"
            val callIntent = Intent(Intent.ACTION_CALL)
            callIntent.data = Uri.parse(phoneNumber)

            // Ensure the permission is granted before starting the activity
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                startActivity(callIntent)
            }
        } else {
            // Permission denied, show Toast display
            Toast.makeText(this, "Unable to make call due to permission disabled. Please enable it in settings.", Toast.LENGTH_LONG).show()
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
}