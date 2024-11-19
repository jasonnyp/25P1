package com.singhealth.enhance.activities.settings.guide

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.singhealth.enhance.activities.settings.SettingsActivity
import com.singhealth.enhance.activities.validation.firebaseErrorDialog
import com.singhealth.enhance.databinding.ActivityUserGuideBinding


class UserGuideActivity : AppCompatActivity() {
    private lateinit var binding: ActivityUserGuideBinding

    private val storage = Firebase.storage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                startActivity(Intent(this@UserGuideActivity, SettingsActivity::class.java))
                finish()
            }
        })

        binding.userManualDoc.setOnClickListener{
            val manualUrl = "https://firebasestorage.googleapis.com/v0/b/enhance-bdc3f.appspot.com/o/user-manual%2FENHANCe%20User%20Manual%20v3.pdf?alt=media&token=36cd0677-6b0a-48a7-95fa-b97c481384cc2"
            downloadUserManual(manualUrl)
        }
    }

    private fun downloadUserManual(manualUrl:String) {
        val manualRef = storage.getReferenceFromUrl(manualUrl)
        manualRef.downloadUrl.addOnSuccessListener {
            val fileUri = it
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(fileUri, contentResolver.getType(fileUri))
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, "No app found to open this file", Toast.LENGTH_SHORT).show()
            }
        } .addOnFailureListener { e ->
            firebaseErrorDialog(this, e, ::downloadUserManual, manualUrl)
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