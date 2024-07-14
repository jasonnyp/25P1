package com.singhealth.enhance.activities.patient

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.singhealth.enhance.R
import com.singhealth.enhance.activities.DashboardActivity
import com.singhealth.enhance.activities.MainActivity
import com.singhealth.enhance.activities.diagnosis.hypertensionStatus
import com.singhealth.enhance.activities.diagnosis.sortPatientVisits
import com.singhealth.enhance.activities.error.errorDialogBuilder
import com.singhealth.enhance.activities.error.firebaseErrorDialog
import com.singhealth.enhance.activities.error.patientNotFoundInSessionErrorDialog
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

class ProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileBinding

    private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle

    private val db = Firebase.firestore
    private val storage = Firebase.storage

    private lateinit var progressDialog: ProgressDialog

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
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                    false
                }

                else -> {
                    false
                }
            }
        }

        binding.profileLL.visibility = View.INVISIBLE

        binding.profileLL.visibility = View.INVISIBLE

        progressDialog = ProgressDialog(this)
        progressDialog.setCanceledOnTouchOutside(false)

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

    // P1 2024: Determine the Patient's BP Stage (Seemingly unused)
//    private fun diagnosePatient(patientID: String){
//        var BPStage : String
//        val db = Firebase.firestore
//
//        var docRef = db.collection("patients").document(patientID)
//
//        // Gets all past bp records for corresponding patient from db
//        db.collection("patients").document(patientID).collection("visits").get()
//            .addOnSuccessListener{ documents ->
//                if (documents.isEmpty) {
//                    BPStage = "N/A"
//                    binding.bpStage.text = BPStage
//                }
//                else {
//                    // Add all BP readings into array
//                    val arr = ArrayList<Diag>()
//                    val inputDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
//                    for (document in documents) {
//                        val dateTimeString = document.get("date") as? String
//                        val dateTime = LocalDateTime.parse(dateTimeString, inputDateFormatter)
//                        val SysBP = document.get("averageSysBP") as? Long
//                        val DiaBP = document.get("averageDiaBP") as? Long
//                        arr.add(Diag(
//                            dateTime.toString(),
//                            SysBP,
//                            DiaBP)
//                        )
//                    }
//                    // Sort array by date in descending order
//                    val sortedArr = arr.sortedByDescending { it.date }
//
//                    // Get the first item in the array (most recent reading)
//                    val recentSys = sortedArr[0].avgSysBP
//                    val recentDia = sortedArr[0].avgDiaBP
//                    val recentDate = sortedArr[0].date
//
//                    // Determine BP Stage
//                    if (recentSys != null && recentDia != null) {
//                        println("Date: $recentDate, Most Recent Sys: $recentSys, Most Recent Dia: $recentDia")
//                        if (recentSys <= 120 && recentDia <= 80) {
//                            BPStage = "(Normal BP)"
//                        }
//                        else if (recentSys >= 160 || recentDia >= 100) {
//                            BPStage = "(Stage 2 Hypertension)"
//                        }
//                        else if (recentSys >= 140 || recentDia >= 90) {
//                            BPStage = "(Stage 1 Hypertension)"
//                        }
//                        else if (recentSys > 120 || recentDia > 80) {
//                            BPStage = "(High Normal BP)"
//                        }
//                        else {
//                            BPStage = "N/A"
//                        }
//                        binding.bpStage.text = BPStage
//
//                        // Update db to store recent BP Stage
//                        val data = hashMapOf("bpStage" to BPStage)
//                        docRef.set(data, SetOptions.merge())
//                    }
//                    else {
//                        println("Sys or Dia is null")
//                    }
//                }
//            }
//            .addOnFailureListener{
//                    e -> println("Error getting documents: $e")
//            }
//    }

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
                        .addOnSuccessListener{ documents ->
                            // For new patients with no records, set the BP Stage as N/A
                            if (documents.isEmpty) {
                                binding.bpStage.text = "N/A"
                            }
                            else {
                                // Call sorting function to sort previous visits
                                val sortedArr = sortPatientVisits(documents)
                                // Get the first item in the array (most recent reading)
                                val recentSys = sortedArr[0].avgSysBP as Long
                                val recentDia = sortedArr[0].avgDiaBP as Long
                                val recentClinicSys = sortedArr[0].clinicSys as Long
                                val recentClinicDia = sortedArr[0].clinicDia as Long
                                val targetHomeSys = sortedArr[0].targetHomeSys as Long
                                val targetHomeDia = sortedArr[0].targetHomeDia as Long
                                val recentDate = sortedArr[0].date as String
                                // Determine BP Stage based on most recent readings
                                val bpStage = hypertensionStatus(this, recentSys, recentDia, recentClinicSys, recentClinicDia, targetHomeSys, targetHomeDia)

                                // Set UI BP Stage
                                binding.bpStage.text = bpStage

                                // If current BP stage is not the same as stored BP stage, update db
                                if (binding.bpStage.text != document.getString("bpStage")){
                                    // Update db to store recent BP Stage
                                    val data = hashMapOf("bpStage" to bpStage)
                                    docRef.set(data, SetOptions.merge())
                                }
                            }

                        }
                        .addOnFailureListener{ e ->
                            errorDialogBuilder(this, getString(R.string.profile_document_error_header), getString(R.string.profile_document_error_body, e))
                            println("Error getting documents: $e")
                        }

                    binding.profileLL.visibility = View.VISIBLE
                    progressDialog.dismiss()
                }
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                firebaseErrorDialog(this, e, docRef)
            }
    }

    private fun updateUIWithPatientData(document: DocumentSnapshot, patientID: String) {
        val imageUrl = document.getString("photoUrl")
        if (imageUrl != null) {
            loadImageFromUrl(imageUrl)
        }

        val legalName = document.getString("legalName")
        if (legalName != null) {
            val decryptedLegalName = AESEncryption().decrypt(legalName)
            binding.legalNameTV.text = decryptedLegalName

            SecureSharedPreferences.getSharedPreferences(applicationContext)
                .edit()
                .putString("legalName", decryptedLegalName)
                .apply()
        }

        binding.nricTV.text = AESEncryption().decrypt(patientID)

        binding.dobTV.text = AESEncryption().decrypt(document.getString("dateOfBirth").toString())

        when (document.getLong("gender")?.toInt()) {
            1 -> binding.genderTV.text = getString(R.string.profile_gender_male)
            2 -> binding.genderTV.text = getString(R.string.profile_gender_female)
        }

        binding.weightTV.text = getString(
            R.string.profile_patient_weight,
            AESEncryption().decrypt(document.getString("weight").toString())
        )

        binding.heightTV.text = getString(
            R.string.profile_patient_height,
            AESEncryption().decrypt(document.getString("height").toString())
        )

        // Decrypt targetSys and targetDia, and handle empty values
        val targetSys = document.getString("targetSys")?.let {
            AESEncryption().decrypt(it)
        } ?: "0"

        val targetDia = document.getString("targetDia")?.let {
            AESEncryption().decrypt(it)
        } ?: "0"

        SecureSharedPreferences.getSharedPreferences(applicationContext)
            .edit()
            .putString("targetSysBP", targetSys)
            .putString("targetDiaBP", targetDia)
            .apply()

        binding.profileTargetBP.text = ResourcesHelper.getString(
            this,
            R.string.profile_patient_target_bp,
            targetSys,
            targetDia
        )
    }


    private fun loadImageFromUrl(imageUrl: String) {
        val imageRef = storage.getReferenceFromUrl(imageUrl)

        imageRef.getBytes(10 * 1024 * 1024)
            .addOnSuccessListener {
                val bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
                binding.photoIV.setImageBitmap(bitmap)
            }
            .addOnFailureListener { e ->
                firebaseErrorDialog(this, e, ::loadImageFromUrl, imageUrl)
            }
    }
}