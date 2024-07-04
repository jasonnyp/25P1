package com.singhealth.enhance.activities.patient

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.singhealth.enhance.R
import com.singhealth.enhance.activities.DashboardActivity
import com.singhealth.enhance.activities.MainActivity
import com.singhealth.enhance.activities.diagnosis.diagnosePatient
import com.singhealth.enhance.activities.diagnosis.sortPatientVisits
import com.singhealth.enhance.activities.history.HistoryActivity
import com.singhealth.enhance.activities.ocr.ScanActivity
import com.singhealth.enhance.activities.settings.SettingsActivity
import com.singhealth.enhance.databinding.ActivityProfileBinding
import com.singhealth.enhance.security.AESEncryption
import com.singhealth.enhance.security.SecureSharedPreferences

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
            Toast.makeText(
                this,
                "Patient information could not be found in current session. Please try again.",
                Toast.LENGTH_LONG
            ).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
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
        progressDialog.setTitle("Retrieving patient data")
        progressDialog.setMessage("Please wait a moment...")
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
                                var sortedArr = sortPatientVisits(documents)
                                // Get the first item in the array (most recent reading)
                                val recentSys = sortedArr[0].avgSysBP as Long
                                val recentDia = sortedArr[0].avgDiaBP as Long
                                val recentDate = sortedArr[0].date as String
                                // Determine BP Stage based on most recent readings
                                var bpStage = diagnosePatient(this, recentSys, recentDia, recentDate)

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
                        .addOnFailureListener{
                                e -> println("Error getting documents: $e")
                        }

                    binding.profileLL.visibility = View.VISIBLE
                    progressDialog.dismiss()
                }
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                showErrorDialog(
                    "Error accessing Firestore Database",
                    "The app is having trouble communicating with the Firestore Database.",
                    e.message.toString()
                )
            }
    }

    @SuppressLint("SetTextI18n")
    private fun updateUIWithPatientData(document: DocumentSnapshot, patientID: String) {
        val imageUrl = document.getString("photoUrl")
        if (imageUrl != null) {
            loadImageFromUrl(imageUrl)
        }

        //binding.legalNameTV.text = document.getString("legalName").toString()
        val legalName = document.getString("legalName")
        if (legalName != null) {
            val decryptedLegalName = AESEncryption().decrypt(legalName)
            binding.legalNameTV.text = decryptedLegalName

            // Store the decrypted legalName in SecureSharedPreferences
            SecureSharedPreferences.getSharedPreferences(applicationContext)
                .edit()
                .putString("legalName", decryptedLegalName)
                .apply()
            }


        binding.nricTV.text = AESEncryption().decrypt(patientID)

        //binding.dobTV.text = document.getString("dateOfBirth").toString()
        binding.dobTV.text = AESEncryption().decrypt(document.getString("dateOfBirth").toString())

        // Comment out when database info is decrypted
        when (document.getLong("gender")?.toInt()) {
            1 -> binding.genderTV.text = getString(R.string.profile_gender_male)
            2 -> binding.genderTV.text = getString(R.string.profile_gender_female)
        }

        // Comment out when database info is encrypted
        //binding.genderTV.text = document.getString("gender")

        //binding.addressTV.text = document.getString("address").toString()
        binding.addressTV.text = AESEncryption().decrypt(document.getString("address").toString())

        //binding.weightTV.text = "${document.getString("weight").toString()} kg"
        binding.weightTV.text = "${AESEncryption().decrypt(document.getString("weight").toString())} kg"

        //binding.heightTV.text = "${document.getString("height").toString()} cm"
        binding.heightTV.text = "${AESEncryption().decrypt(document.getString("height").toString())} cm"
    }

    private fun loadImageFromUrl(imageUrl: String) {
        val imageRef = storage.getReferenceFromUrl(imageUrl)

        imageRef.getBytes(10 * 1024 * 1024)
            .addOnSuccessListener {
                val bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
                binding.photoIV.setImageBitmap(bitmap)
            }
            .addOnFailureListener { e ->
                showErrorDialog(
                    "Error accessing Firebase Storage",
                    "The app is having trouble communicating with the Firebase Storage.",
                    e.message.toString()
                )
            }
    }

    private fun showErrorDialog(title: String, message: String, error: String) {
        MaterialAlertDialogBuilder(this)
            .setIcon(R.drawable.ic_error)
            .setTitle(title)
            .setMessage("$message\n\nContact IT support with the following error code if issue persists: $error")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}