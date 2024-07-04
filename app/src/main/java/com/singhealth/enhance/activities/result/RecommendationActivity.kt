package com.singhealth.enhance.activities.result


import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.singhealth.enhance.R
import com.singhealth.enhance.activities.DashboardActivity
import com.singhealth.enhance.activities.MainActivity
import com.singhealth.enhance.activities.diagnosis.bpControlStatus
import com.singhealth.enhance.activities.diagnosis.diagnosePatient
import com.singhealth.enhance.activities.diagnosis.hypertensionStatus
import com.singhealth.enhance.activities.diagnosis.showControlStatus
import com.singhealth.enhance.activities.diagnosis.showRecommendation
import com.singhealth.enhance.activities.history.HistoryActivity
import com.singhealth.enhance.activities.ocr.ScanActivity
import com.singhealth.enhance.activities.patient.ProfileActivity
import com.singhealth.enhance.databinding.ActivityRecommendationBinding
import com.singhealth.enhance.security.AESEncryption
import com.singhealth.enhance.security.SecureSharedPreferences
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter

class RecommendationActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRecommendationBinding

    private var patientID: String? = null

    private var avgSysBP: Long = 0
    private var avgDiaBP: Long = 0
    private var patientAge: Int = 0

    private val db = Firebase.firestore
    @SuppressLint("SetTextI18n")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityRecommendationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                startActivity(Intent(this@RecommendationActivity, HistoryActivity::class.java))
                finish()
            }
        })

        val patientSharedPreferences = SecureSharedPreferences.getSharedPreferences(applicationContext)
        if (patientSharedPreferences.getString("patientID", null).isNullOrEmpty()) {
            val mainIntent = Intent(this, MainActivity::class.java)
            Toast.makeText(this, getString(R.string.patient_info_not_found), Toast.LENGTH_LONG).show()
            startActivity(mainIntent)
            finish()
        } else {
            patientID= patientSharedPreferences.getString("patientID", null)
        }

        binding.bottomNavigationView.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.item_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    finish()
                    false
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

        val avgBPBundle = intent.extras
        avgSysBP = avgBPBundle!!.getInt("avgSysBP").toLong()
        avgDiaBP = avgBPBundle.getInt("avgDiaBP").toLong()

        // Determine BP Stage
        val bpStage = diagnosePatient(this, avgSysBP, avgDiaBP)

        // Display average BP
        binding.avgHomeSysBPTV.text = avgSysBP.toString()
        binding.avgHomeDiaBPTV.text = avgDiaBP.toString()

        // Calculate patient's age
        val docRef = db.collection("patients").document(patientID.toString())
        docRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                    val birthDate = LocalDate.parse(
                        AESEncryption().decrypt(
                            document.getString("dateOfBirth").toString()
                        ), formatter
                    )
                    val currentDate = LocalDate.now()
                    val period = Period.between(birthDate, currentDate)
                    patientAge = period.years

                    val collectionRef = docRef.collection("visits")
                    collectionRef.get()
                        .addOnSuccessListener { documents ->
                            // Display BP Stage and correct Control Status based on the Source Activity
                            println(avgBPBundle.getString("Source"))
                            if (avgBPBundle.getString("Source") == "History") {
                                val date = avgBPBundle.getString("date").toString()
                                binding.bpStage.text = bpControlStatus(this, avgSysBP, avgDiaBP, 0, 0)
                                binding.controlStatusTV.text = showControlStatus(documents, patientAge, date)
                                // "(${bpStage})"
                            }
                            else if (avgBPBundle.getString("Source") == "Scan") {
                                // Display BP Stage
                                binding.bpStage.text = bpControlStatus(this, avgSysBP, avgDiaBP, 0, 0)
                                binding.controlStatusTV.text = showControlStatus(documents, patientAge, null)
                            }
                            val recoList = showRecommendation(this, hypertensionStatus(this, avgSysBP, avgDiaBP))
                            binding.dietTV.text = recoList[0]
                            binding.lifestyleTV.text = recoList[1]
                            binding.medTV.text = recoList[2]

                            // If / When Statement for setting image
                            //binding.IV.setImageResource(R.drawable.ic_error) //Change to Image id
                            val imageResource = when (hypertensionStatus(this, avgSysBP, avgDiaBP)) {
                                getString(R.string.well_controlled_hypertension) -> R.drawable.excellent
                                getString(R.string.white_coat_uncontrolled_hypertension) -> R.drawable.good // Not Used for now
                                getString(R.string.masked_hypertension) -> R.drawable.neutral // Not Used for now
                                getString(R.string.uncontrolled_hypertension) -> R.drawable.poor
                                else -> R.drawable.ic_error // Default image if the stage is not recognized
                            }
                            binding.statusIV.setImageResource(imageResource)
                        }
                }


            }
            .addOnFailureListener { e ->
                MaterialAlertDialogBuilder(this)
                    .setIcon(R.drawable.ic_error)
                    .setTitle(getString(R.string.firebase_error_header))
                    .setMessage(getString(R.string.firebase_error_body, e))
                    .setPositiveButton(resources.getString(R.string.ok_dialog)) { dialog, _ -> dialog.dismiss() }
                    .show()
            }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                startActivity(Intent(this, HistoryActivity::class.java))
                finish()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }
}