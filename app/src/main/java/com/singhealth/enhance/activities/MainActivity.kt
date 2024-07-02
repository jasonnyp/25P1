package com.singhealth.enhance.activities

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.singhealth.enhance.R
import com.singhealth.enhance.activities.patient.ProfileActivity
import com.singhealth.enhance.activities.patient.RegistrationActivity
import com.singhealth.enhance.activities.settings.SettingsActivity
import com.singhealth.enhance.databinding.ActivityMainBinding
import com.singhealth.enhance.security.AESEncryption
import com.singhealth.enhance.security.SecureSharedPreferences

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle

    private val db = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Navigation drawer
        actionBarDrawerToggle = ActionBarDrawerToggle(this, binding.drawerLayout, 0, 0)
        binding.drawerLayout.addDrawerListener(actionBarDrawerToggle)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        actionBarDrawerToggle.syncState()

        binding.navigationView.setCheckedItem(R.id.item_home)

        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.item_home -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }

                R.id.item_patient_registration -> {
                    startActivity(Intent(this, RegistrationActivity::class.java))
                    finish()
                    true
                }

                R.id.item_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    finish()
                    true
                }

                else -> {
                    false
                }
            }
        }

        // Register new patient
        binding.newPatientRegistrationBtn.setOnClickListener {
            startActivity(
                Intent(
                    this,
                    RegistrationActivity::class.java
                )
            )
        }

        // Dismiss error messages
        binding.idTIET.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.idTIL.error = null
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Search patient
        binding.searchRecordBtn.setOnClickListener {
            if (validateFields()) {
                val nric =
                    AESEncryption().encrypt(binding.idTIET.text.toString().trim().uppercase())
                getPatientData(nric)
            }
        }
    }

    // Clear patient data in current session
    override fun onResume() {
        super.onResume()

        val patientSharedPreferences = SecureSharedPreferences.getSharedPreferences(applicationContext)
        if (!patientSharedPreferences.getString("patientID", null).isNullOrEmpty()) {
            val patientSPEditor = patientSharedPreferences.edit()
            patientSPEditor.clear()
            patientSPEditor.apply()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (actionBarDrawerToggle.onOptionsItemSelected(item)) {
            true
        } else super.onOptionsItemSelected(item)
    }

    // Handle search patient query
    private fun getPatientData(patientID: String) {
        val docRef = db.collection("patients").document(patientID)
        docRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    savePatientData(patientID)
                    startActivity(Intent(this, ProfileActivity::class.java))
                } else {
                    MaterialAlertDialogBuilder(this)
                        .setIcon(R.drawable.ic_error)
                        .setTitle("Patient is not found")
                        .setMessage("Ensure patient's information is entered correctly. Otherwise, kindly confirm if this is the patient's first visit and proceed to register their information.")
                        .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                        .show()
                }
            }
            .addOnFailureListener { e ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Error accessing Firestore Database")
                    .setMessage("The app is having trouble communicating with the Firestore Database.\n\nIf issue persists, contact IT support with the following error code: $e")
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                    .show()
            }
    }

    // Save patient data in current session
    private fun savePatientData(patientID: String) {
        val patientSharedPreferences = SecureSharedPreferences.getSharedPreferences(applicationContext)
        patientSharedPreferences.edit().putString("patientID", patientID).apply()
    }

    // Validate fields
    private fun validateFields(): Boolean {
        var valid = true

        if (binding.idTIET.editableText.isNullOrEmpty()) {
            binding.idTIL.error = "ID cannot be empty"
            valid = false
        }

        return valid
    }
}