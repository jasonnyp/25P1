package com.singhealth.enhance.activities.result


import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.singhealth.enhance.R
import com.singhealth.enhance.activities.MainActivity
import com.singhealth.enhance.activities.dashboard.SimpleDashboardActivity
import com.singhealth.enhance.activities.diagnosis.bpControlStatus
import com.singhealth.enhance.activities.diagnosis.colourSet
import com.singhealth.enhance.activities.diagnosis.hypertensionStatus
import com.singhealth.enhance.activities.diagnosis.showRecommendation
import com.singhealth.enhance.activities.error.firebaseErrorDialog
import com.singhealth.enhance.activities.history.HistoryActivity
import com.singhealth.enhance.activities.ocr.ScanActivity
import com.singhealth.enhance.activities.patient.ProfileActivity
import com.singhealth.enhance.databinding.ActivityRecommendationBinding
import com.singhealth.enhance.security.AESEncryption
import com.singhealth.enhance.security.SecureSharedPreferences

class RecommendationActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRecommendationBinding

    private var patientID: String? = null

    private var avgSysBP: Long = 0
    private var avgDiaBP: Long = 0
    private var clinicSysBP: Long = 0
    private var clinicDiaBP: Long = 0

    private val db = Firebase.firestore

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

        val avgBPBundle = intent.extras
        avgSysBP = avgBPBundle!!.getInt("avgSysBP").toLong()
        avgDiaBP = avgBPBundle.getInt("avgDiaBP").toLong()
        clinicSysBP = avgBPBundle.getInt("clinicSysBP").toLong()
        clinicDiaBP = avgBPBundle.getInt("clinicDiaBP").toLong()

        val docRef = db.collection("patients").document(patientID.toString())
        var patientTargetSys: Long
        var patientTargetDia: Long
        var clinicTargetSys: Long
        var clinicTargetDia: Long
        var hypertension: String

        docRef.get()
            .addOnSuccessListener { document ->
                docRef.collection("visits").get()
                if (AESEncryption().decrypt(document.getString("targetSys").toString()) == "" || AESEncryption().decrypt(document.getString("targetDia").toString()) == "") {
                    patientTargetSys = 0
                    patientTargetDia = 0
                    clinicTargetSys = 0
                    clinicTargetDia = 0
                } else {
                    patientTargetSys = AESEncryption().decrypt(document.getString("targetSys").toString()).toLong()
                    patientTargetDia = AESEncryption().decrypt(document.getString("targetDia").toString()).toLong()
                    clinicTargetSys = patientTargetSys + 5
                    clinicTargetDia = patientTargetDia + 5
                }

                binding.targetHomeSysBPTV.text = patientTargetSys.toString()
                binding.targetHomeDiaBPTV.text = patientTargetDia.toString()
                binding.targetOfficeSysBPTV.text = clinicTargetSys.toString()
                binding.targetOfficeDiaBPTV.text = clinicTargetDia.toString()

                binding.avgHomeSysBPTV.text = avgSysBP.toString()
                binding.avgHomeDiaBPTV.text = avgDiaBP.toString()
                binding.avgHomeSysBPTV.setTextColor(colourSet(this, avgSysBP, patientTargetSys))
                binding.avgHomeDiaBPTV.setTextColor(colourSet(this, avgDiaBP, patientTargetDia))
                binding.avgHomeBPControl.text = bpControlStatus(this, avgSysBP, avgDiaBP, patientTargetSys, patientTargetDia)

                binding.officeSysBPTV.text = clinicSysBP.toString()
                binding.officeDiaBPTV.text = clinicDiaBP.toString()
                binding.officeSysBPTV.setTextColor(colourSet(this, clinicSysBP, clinicTargetSys))
                binding.officeDiaBPTV.setTextColor(colourSet(this, clinicDiaBP, clinicTargetDia))
                binding.officeBPControl.text = bpControlStatus(this, clinicSysBP, clinicDiaBP, clinicTargetSys, clinicTargetDia)

                hypertension = hypertensionStatus(this, avgSysBP, avgDiaBP, clinicSysBP, clinicDiaBP, patientTargetSys, patientTargetDia)
                binding.recommendationBpPhenotype.text = hypertension
                binding.recommendationBpControl.text = bpControlStatus(this, hypertension)
                binding.recommendationDo.text = showRecommendation(this, bpControlStatus(this, hypertension))
            }
            .addOnFailureListener { e ->
                firebaseErrorDialog(this, e, docRef)
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