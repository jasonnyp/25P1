package com.singhealth.enhance.activities.result


import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.singhealth.enhance.R
import com.singhealth.enhance.activities.MainActivity
import com.singhealth.enhance.activities.diagnosis.bpControlStatus
import com.singhealth.enhance.activities.diagnosis.colourSet
import com.singhealth.enhance.activities.diagnosis.hypertensionStatus
import com.singhealth.enhance.activities.diagnosis.showRecommendation
import com.singhealth.enhance.activities.history.HistoryActivity
import com.singhealth.enhance.activities.history.HistoryData
import com.singhealth.enhance.databinding.ActivityRecommendationBinding
import com.singhealth.enhance.security.SecureSharedPreferences
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object ResourcesHelper {
    fun getString(context: Context, @StringRes resId: Int, vararg formatArgs: Any): String {
        return context.getString(resId, *formatArgs)
    }
}

class RecommendationActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRecommendationBinding

    private var patientID: String? = null

    private var avgSysBP: Long = 0
    private var avgDiaBP: Long = 0
    private var clinicSysBP: Long = 0
    private var clinicDiaBP: Long = 0
    private var bundlePosition: Int = 0
    private var bundleDate: String = ""
    private var bundleRecordCount: Int = 0
    private var history = ArrayList<HistoryData>()
    private lateinit var sortedHistory: List<HistoryData>

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

        val patientSharedPreferences =
            SecureSharedPreferences.getSharedPreferences(applicationContext)
        if (patientSharedPreferences.getString("patientID", null).isNullOrEmpty()) {
            val mainIntent = Intent(this, MainActivity::class.java)
            Toast.makeText(this, getString(R.string.patient_info_not_found), Toast.LENGTH_LONG)
                .show()
            startActivity(mainIntent)
            finish()
        } else {
            patientID = patientSharedPreferences.getString("patientID", null)
        }

        val avgBPBundle = intent.extras
        avgSysBP = avgBPBundle!!.getInt("avgSysBP").toLong()
        avgDiaBP = avgBPBundle.getInt("avgDiaBP").toLong()
        clinicSysBP = avgBPBundle.getInt("clinicSysBP").toLong()
        clinicDiaBP = avgBPBundle.getInt("clinicDiaBP").toLong()
        bundlePosition = avgBPBundle.getInt("historyItemPosition")
        bundleDate = avgBPBundle.getString("date").toString()

        var hypertension: String

        db.collection("patients").document(patientID.toString()).collection("visits").get()
            .addOnSuccessListener { documents ->
                val inputDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
                val outputDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss")

                for (document in documents) {
                    val dateTimeString = document.get("date") as? String
                    val dateTime = LocalDateTime.parse(dateTimeString, inputDateFormatter)
                    val dateTimeFormatted = dateTime.format(outputDateFormatter)
                    val avgSysBP = document.get("averageSysBP") as? Long
                    val avgDiaBP = document.get("averageDiaBP") as? Long
                    val homeSysBPTarget = document.get("homeSysBPTarget") as? Long
                    val homeDiaBPTarget = document.get("homeDiaBPTarget") as? Long
                    val clinicSysBPTarget = document.get("clinicSysBPTarget") as? Long
                    val clinicDiaBPTarget = document.get("clinicDiaBPTarget") as? Long
                    val clinicSysBP = document.get("clinicSysBP") as? Long
                    val clinicDiaBP = document.get("clinicDiaBP") as? Long
                    var scanRecordCount = document.get("scanRecordCount") as? Long
                    if (scanRecordCount == null) {
                        scanRecordCount = 0
                    }
                    history.add(
                        HistoryData(
                            dateTime.toString(),
                            dateTimeFormatted,
                            avgSysBP,
                            avgDiaBP,
                            homeSysBPTarget,
                            homeDiaBPTarget,
                            clinicSysBPTarget,
                            clinicDiaBPTarget,
                            clinicSysBP,
                            clinicDiaBP,
                            scanRecordCount
                        )
                    )
                }

                sortedHistory = history.sortedByDescending { it.date }

                val patientTargetSys: Long = sortedHistory[bundlePosition].homeSysBPTarget!!
                val patientTargetDia: Long = sortedHistory[bundlePosition].homeDiaBPTarget!!
                val clinicTargetSys: Long = sortedHistory[bundlePosition].clinicSysBPTarget!!
                val clinicTargetDia: Long = sortedHistory[bundlePosition].clinicDiaBPTarget!!
                bundleRecordCount = avgBPBundle.getInt("scanRecordCount")

                binding.recommendationHeader.text =
                    ResourcesHelper.getString(this, R.string.enhance_recco_header, bundleRecordCount)
                binding.insufficientRecordMessage.text = when {
                    bundleRecordCount == 0 -> getString(R.string.old_scan_records)
                    bundleRecordCount < 12 -> getString(R.string.insufficient_records)
                    else -> ""
                }
                if (binding.insufficientRecordMessage.text == "") {
                    binding.insufficientRecordMessage.visibility = View.GONE
                }

                binding.targetHomeSysBPTV.text = patientTargetSys.toString()
                binding.targetHomeDiaBPTV.text = patientTargetDia.toString()
                binding.targetClinicSysBPTV.text = clinicTargetSys.toString()
                binding.targetClinicDiaBPTV.text = clinicTargetDia.toString()

                binding.avgHomeSysBPTV.text = avgSysBP.toString()
                binding.avgHomeDiaBPTV.text = avgDiaBP.toString()
                binding.avgHomeSysBPTV.setTextColor(colourSet(this, avgSysBP, patientTargetSys))
                binding.avgHomeDiaBPTV.setTextColor(colourSet(this, avgDiaBP, patientTargetDia))
                binding.avgHomeBPControl.text =
                    bpControlStatus(this, avgSysBP, avgDiaBP, patientTargetSys, patientTargetDia)

                binding.clinicSysBPTV.text = clinicSysBP.toString()
                binding.clinicDiaBPTV.text = clinicDiaBP.toString()
                binding.clinicSysBPTV.setTextColor(colourSet(this, clinicSysBP, clinicTargetSys))
                binding.clinicDiaBPTV.setTextColor(colourSet(this, clinicDiaBP, clinicTargetDia))
                binding.clinicBPControl.text =
                    bpControlStatus(this, clinicSysBP, clinicDiaBP, clinicTargetSys, clinicTargetDia)

                hypertension = hypertensionStatus(
                    this,
                    avgSysBP,
                    avgDiaBP,
                    clinicSysBP,
                    clinicDiaBP,
                    patientTargetSys,
                    patientTargetDia
                )
                binding.recommendationBpPhenotype.text = hypertension
                binding.recommendationBpControl.text = bpControlStatus(this, hypertension)
                binding.recommendationDo.text =
                    showRecommendation(this, bpControlStatus(this, hypertension))
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