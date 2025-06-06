package com.singhealth.enhance.activities.history

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.singhealth.enhance.R
import com.singhealth.enhance.security.LogOutTimerUtil
import com.singhealth.enhance.activities.MainActivity
import com.singhealth.enhance.activities.authentication.LoginActivity
import com.singhealth.enhance.activities.dashboard.SimpleDashboardActivity
import com.singhealth.enhance.activities.ocr.ScanActivity
import com.singhealth.enhance.activities.patient.ProfileActivity
import com.singhealth.enhance.activities.patient.RegistrationActivity
import com.singhealth.enhance.activities.result.RecommendationActivity
import com.singhealth.enhance.activities.settings.SettingsActivity
import com.singhealth.enhance.activities.validation.internetConnectionCheck
import com.singhealth.enhance.activities.validation.patientNotFoundInSessionErrorDialog
import com.singhealth.enhance.databinding.ActivityHistoryBinding
import com.singhealth.enhance.security.SecureSharedPreferences
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class HistoryActivity : AppCompatActivity(), LogOutTimerUtil.LogOutListener, HistoryAdapter.OnItemClickListener {
    private lateinit var binding: ActivityHistoryBinding

    private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle

    private lateinit var patientID: String

    private lateinit var sortedHistory: List<HistoryData>

    private val db = Firebase.firestore
    private var history = ArrayList<HistoryData>()

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

        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        internetConnectionCheck(this)

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
        binding.bottomNavigationView.selectedItemId = R.id.item_history

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
                    true
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

        // Check if patient information exist in session
        val patientSharedPreferences =
            SecureSharedPreferences.getSharedPreferences(applicationContext)
        if (patientSharedPreferences.getString("patientID", null).isNullOrEmpty()) {
            patientNotFoundInSessionErrorDialog(this)
        } else {
            patientID = patientSharedPreferences.getString("patientID", null).toString()

            db.collection("patients").document(patientID).collection("visits").get()
                .addOnSuccessListener { documents ->
                    val inputDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
                    val outputDateFormatter = DateTimeFormatter.ofPattern(getString(R.string.date_format))

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
                        val validDayIndices = document.get("validDayIndices") as? List<*>
                        var scanRecordCount = document.get("scanRecordCount") as? Long
                        if (scanRecordCount == null) {
                            scanRecordCount = 0
                        }
                        val sevenDay = document.get("sevenDay") as? Boolean ?: false
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
                                scanRecordCount,
                                validDayIndices,
                                sevenDay
                            )
                        )
                    }

                    if (history.isEmpty()) {
                        binding.noHistoryWarningTV.visibility = View.VISIBLE
                    }
                    else {
                        sortedHistory = history.sortedByDescending { it.date }

                        println("Sorted History$sortedHistory")
                        println("Sorted History 1st" + sortedHistory[0])
                        println("Sorted History 1st SYS DATA" + sortedHistory[0].avgSysBP)

                        val adapter = HistoryAdapter(sortedHistory, this)

                        binding.recyclerView.adapter = adapter
                        binding.recyclerView.layoutManager = LinearLayoutManager(this)
                    }
                }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (actionBarDrawerToggle.onOptionsItemSelected(item)) {
            true
        } else super.onOptionsItemSelected(item)
    }

    override fun onItemClick(position: Int) {
        val clickedItem = sortedHistory[position]
        val avgSysBP = clickedItem.avgSysBP.toString()
        val avgDiaBP = clickedItem.avgDiaBP.toString()
        val clinicSysBP = clickedItem.clinicSysBP.toString()
        val clinicDiaBP = clickedItem.clinicDiaBP.toString()
        val date = clickedItem.date.toString()
        val recordCount = clickedItem.scanRecordCount.toString()

        val bundle = Bundle()

        bundle.putInt("avgSysBP", avgSysBP.toInt())
        bundle.putInt("avgDiaBP", avgDiaBP.toInt())
        bundle.putInt("clinicSysBP", clinicSysBP.toInt())
        bundle.putInt("clinicDiaBP", clinicDiaBP.toInt())
        bundle.putString("date", date)
        bundle.putInt("historyItemPosition", position)
        bundle.putInt("scanRecordCount", recordCount.toInt())
        println(date)
        bundle.putString("Source", "History")

        val recommendationIntent = Intent(this, RecommendationActivity::class.java)

        recommendationIntent.putExtras(bundle)

        startActivity(recommendationIntent)
    }
}