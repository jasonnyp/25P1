package com.singhealth.enhance.activities.patient

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.singhealth.enhance.R
import com.singhealth.enhance.security.LogOutTimerUtil
import com.singhealth.enhance.activities.MainActivity
import com.singhealth.enhance.activities.authentication.LoginActivity
import com.singhealth.enhance.activities.dashboard.SimpleDashboardActivity
import com.singhealth.enhance.activities.diagnosis.hypertensionStatus
import com.singhealth.enhance.activities.diagnosis.sortPatientVisits
import com.singhealth.enhance.activities.validation.errorDialogBuilder
import com.singhealth.enhance.activities.validation.firebaseErrorDialog
import com.singhealth.enhance.activities.validation.patientNotFoundInSessionErrorDialog
import com.singhealth.enhance.activities.history.HistoryActivity
import com.singhealth.enhance.activities.ocr.ScanActivity
import com.singhealth.enhance.activities.settings.SettingsActivity
import com.singhealth.enhance.databinding.ActivityProfileBinding
import com.singhealth.enhance.security.AESEncryption
import com.singhealth.enhance.security.SecureSharedPreferences

object ResourcesHelper {
    fun getString(context: Context, @StringRes resId: Int, string1: String, string2: String): String {
        return context.getString(resId, string1, string2)
    }
}

class ProfileActivity : AppCompatActivity(), LogOutTimerUtil.LogOutListener {
    private lateinit var binding: ActivityProfileBinding

    private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle

    private val db = Firebase.firestore
    private val storage = Firebase.storage

    private lateinit var progressDialog: ProgressDialog

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

        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Navigation drawer
        actionBarDrawerToggle = ActionBarDrawerToggle(this, binding.drawerLayout, 0, 0)
        binding.drawerLayout.addDrawerListener(actionBarDrawerToggle)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        actionBarDrawerToggle.syncState()

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
                    startActivity(Intent(this, SettingsActivity::class.java))
                    finish()
                    true
                }

                else -> {
                    false
                }
            }
        }

        // Navigation bar
        binding.bottomNavigationView.selectedItemId = R.id.item_profile

        binding.bottomNavigationView.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.item_profile -> {
                    true
                }

                R.id.item_scan -> {
                    startActivity(Intent(this, ScanActivity::class.java))
                    finish()
                    false
                }

                R.id.item_history -> {
                    startActivity(Intent(this, HistoryActivity::class.java))
                    finish()
                    false
                }

                R.id.item_dashboard -> {
                    startActivity(Intent(this, SimpleDashboardActivity::class.java))
                    finish()
                    false
                }

                else -> {
                    false
                }
            }
        }

        binding.profileLL.visibility = View.INVISIBLE

        progressDialog = ProgressDialog(this)
        progressDialog.setCanceledOnTouchOutside(false)

        binding.editProfileSourceBtn.setOnClickListener {
            if (SecureSharedPreferences.getSharedPreferences(applicationContext)
                    .getString("patientID", null)
                    .isNullOrEmpty()
            ) {
                patientNotFoundInSessionErrorDialog(this)
            } else {
                startActivity(Intent(this, EditProfileActivity::class.java))
            }
        }

        binding.deleteProfileSourceBtn.setOnClickListener {
            showDeleteConfirmationDialog()
        }

        val patientID = SecureSharedPreferences.getSharedPreferences(applicationContext).getString(
            "patientID",
            null
        )
        if (patientID.isNullOrEmpty()) {
            patientNotFoundInSessionErrorDialog(this)
        } else {
            retrievePatient(patientID)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (actionBarDrawerToggle.onOptionsItemSelected(item)) {
            true
        } else super.onOptionsItemSelected(item)
    }

    private fun retrievePatient(patientID: String) {
        progressDialog.setTitle(getString(R.string.profile_retrieve_data))
        progressDialog.setMessage(getString(R.string.profile_retrieve_data_caption))
        progressDialog.show()


        val docRef = db.collection("patients").document(patientID)

        docRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    updateUIWithPatientData(document, patientID)

                    // P1 2024 Addition: Determine BP Stage
                    // Gets all past bp records for corresponding patient from db
                    db.collection("patients").document(patientID).collection("visits").get()
                        .addOnSuccessListener { documents ->
                            // For new patients with no records, set the BP Stage as N/A
                            if (documents.isEmpty) {
                                binding.bpStage.text = "Not yet determined"
                            } else {
                                // Call sorting function to sort previous visits
                                val sortedArr = sortPatientVisits(documents)
                                // Get the first item in the array (most recent reading)
                                val recentHomeSys = sortedArr[0].avgSysBP as Long
                                val recentHomeDia = sortedArr[0].avgDiaBP as Long
                                val recentClinicSys = sortedArr[0].clinicSys as Long
                                val recentClinicDia = sortedArr[0].clinicDia as Long
                                val targetHomeSys = sortedArr[0].targetHomeSys as Long
                                val targetHomeDia = sortedArr[0].targetHomeDia as Long
                                val targetClinicSys = sortedArr[0].targetClinicSys as Long
                                val targetClinicDia = sortedArr[0].targetClinicDia as Long
                                val recentDate = sortedArr[0].date as String
                                // Determine BP Stage based on most recent readings
                                val bpStage = hypertensionStatus(
                                    this,
                                    recentHomeSys,
                                    recentHomeDia,
                                    recentClinicSys,
                                    recentClinicDia,
                                    targetHomeSys,
                                    targetHomeDia,
                                    targetClinicSys,
                                    targetClinicDia
                                )

                                // Set UI BP Stage
                                binding.bpStage.text = bpStage

                                // If current BP stage is not the same as stored BP stage, update db
                                if (binding.bpStage.text != document.getString("bpStage")) {
                                    // Update db to store recent BP Stage
                                    val data = hashMapOf("bpStage" to bpStage)
                                    docRef.set(data, SetOptions.merge())
                                }
                            }
                            binding.profileLL.visibility = View.VISIBLE
                            progressDialog.dismiss()
                        }
                        .addOnFailureListener { e ->
                            errorDialogBuilder(
                                this,
                                getString(R.string.profile_document_error_header),
                                getString(R.string.profile_document_error_body, e)
                            )
                            println("Error getting documents: $e")
                        }
                }
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                firebaseErrorDialog(this, e, docRef)
            }
    }

    private fun updateUIWithPatientData(document: DocumentSnapshot, patientID: String) {
//        val imageUrl = document.getString("photoUrl")
//        if (imageUrl != null) {
//            loadImageFromUrl(imageUrl)
//        }

//        val legalName = document.getString("legalName")
//        if (legalName != null) {
//            val decryptedLegalName = AESEncryption().decrypt(legalName)
//            binding.legalNameTV.text = decryptedLegalName
//
//            SecureSharedPreferences.getSharedPreferences(applicationContext)
//                .edit()
//                .putString("legalName", decryptedLegalName)
//                .apply()
//        }

        binding.nricTV.text = AESEncryption().decrypt(patientID)

//        binding.dobTV.text = AESEncryption().decrypt(document.getString("dateOfBirth").toString())

//        when (document.getLong("gender")?.toInt()) {
//            1 -> binding.genderTV.text = getString(R.string.profile_gender_male)
//            2 -> binding.genderTV.text = getString(R.string.profile_gender_female)
//        }

//        binding.weightTV.text = getString(
//            R.string.profile_patient_weight,
//            AESEncryption().decrypt(document.getString("weight").toString())
//        )

//        binding.heightTV.text = getString(
//            R.string.profile_patient_height,
//            AESEncryption().decrypt(document.getString("height").toString())
//        )

        // Decrypt targetSys and targetDia, and handle empty values
        val targetHomeSys = document.getString("targetHomeSys")?.let {
            AESEncryption().decrypt(it)
        } ?: "0"

        val targetHomeDia = document.getString("targetHomeDia")?.let {
            AESEncryption().decrypt(it)
        } ?: "0"

        SecureSharedPreferences.getSharedPreferences(applicationContext)
            .edit()
            .putString("targetHomeSysBP", targetHomeSys)
            .putString("targetHomeDiaBP", targetHomeDia)
            .apply()

        binding.profileTargetHomeBP.text = ResourcesHelper.getString(
            this,
            R.string.profile_patient_target_bp,
            targetHomeSys,
            targetHomeDia
        )

        val targetClinicSys = document.getString("targetClinicSys")?.let {
            AESEncryption().decrypt(it)
        } ?: "0"

        val targetClinicDia = document.getString("targetClinicDia")?.let {
            AESEncryption().decrypt(it)
        } ?: "0"

        SecureSharedPreferences.getSharedPreferences(applicationContext)
            .edit()
            .putString("targetClinicSysBP", targetClinicSys)
            .putString("targetClinicDiaBP", targetClinicDia)
            .apply()

        binding.profileTargetClinicBP.text = ResourcesHelper.getString(
            this,
            R.string.profile_patient_target_bp,
            targetClinicSys,
            targetClinicDia
        )

//        binding.clinicId.text = document.getString("clinicId").toString()
    }


//    private fun loadImageFromUrl(imageUrl: String) {
//        val imageRef = storage.getReferenceFromUrl(imageUrl)
//
//        imageRef.getBytes(10 * 1024 * 1024)
//            .addOnSuccessListener {
//                val bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
//                binding.photoIV.setImageBitmap(bitmap)
//            }
//            .addOnFailureListener { e ->
//                firebaseErrorDialog(this, e, ::loadImageFromUrl, imageUrl)
//            }
//    }

    // Method to show the delete confirmation dialog
    private fun showDeleteConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_confirmation_title))
            .setMessage(getString(R.string.delete_confirmation_message))
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton(getString(R.string.delete)) { dialog, _ ->
                dialog.dismiss()
                deletePatient()
            }
            .show()
    }

    // Method to delete patient from Firebase
    private fun deletePatient() {
        val patientID = SecureSharedPreferences.getSharedPreferences(applicationContext).getString("patientID", null)
        if (patientID.isNullOrEmpty()) {
            patientNotFoundInSessionErrorDialog(this)
            return
        }

        progressDialog.setTitle(getString(R.string.deleting))
        progressDialog.setMessage(getString(R.string.please_wait))
        progressDialog.show()

        // Delete patient document from Firestore
        db.collection("patients").document(patientID)
            .delete()
            .addOnSuccessListener {
                db.collection("patients").document(patientID).collection("visits").get()
                    .addOnSuccessListener { documents ->
                        for(document in documents){
                            db.collection("patients").document(patientID).collection("visits").document(document.id).delete()
                        }

//                        val nricDecrypted = AESEncryption().decrypt(patientID)
//                        val storageRef = storage.reference.child("images/$nricDecrypted.jpg")
//                        storageRef.delete().addOnSuccessListener {
//                            progressDialog.dismiss()
//                            Toast.makeText(
//                                this,
//                                getString(R.string.patient_deleted_success),
//                                Toast.LENGTH_SHORT
//                            ).show()
//                        }


                        // Navigate to MainActivity after deletion
                        progressDialog.dismiss()
                        Toast.makeText(
                            this,
                            getString(R.string.patient_deleted_success),
                            Toast.LENGTH_SHORT
                        ).show()
                        val intent = Intent(this, MainActivity::class.java)
                        intent.flags =
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        progressDialog.dismiss()
                        firebaseErrorDialog(this, e, db.collection("patients").document(patientID))
                    }
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                firebaseErrorDialog(this, e, db.collection("patients").document(patientID))
            }
    }
}