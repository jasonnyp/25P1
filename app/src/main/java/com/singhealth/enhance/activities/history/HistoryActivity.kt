package com.singhealth.enhance.activities.history

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.singhealth.enhance.R
import com.singhealth.enhance.activities.MainActivity
import com.singhealth.enhance.activities.ocr.ScanActivity
import com.singhealth.enhance.activities.patient.ProfileActivity
import com.singhealth.enhance.activities.patient.RegistrationActivity
import com.singhealth.enhance.activities.settings.SettingsActivity
import com.singhealth.enhance.databinding.ActivityHistoryBinding
import com.singhealth.enhance.security.SecureSharedPreferences
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.github.mikephil.charting.charts.LineChart
import java.util.*
import com.singhealth.enhance.activities.DashboardActivity
import com.singhealth.enhance.activities.result.RecommendationActivity

class HistoryActivity : AppCompatActivity(), HistoryAdapter.OnItemClickListener {
    private lateinit var binding: ActivityHistoryBinding

    private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle

    private lateinit var patientID: String

    private lateinit var sortedHistory: List<HistoryData>

    private val db = Firebase.firestore
    private val history = ArrayList<HistoryData>()

    private lateinit var lineChart: LineChart
    private lateinit var diastolicLineChart: LineChart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHistoryBinding.inflate(layoutInflater)
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
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                    false
                }

                else -> {
                    false
                }
            }
        }

        // Check if patient information exist in session
        val patientSharedPreferences = SecureSharedPreferences.getSharedPreferences(applicationContext)
        if (patientSharedPreferences.getString("patientID", null).isNullOrEmpty()) {
            Toast.makeText(
                this,
                "Patient information could not be found in current session. Please try again.",
                Toast.LENGTH_LONG
            ).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } else {
            patientID = patientSharedPreferences.getString("patientID", null).toString()
        }

        db.collection("patients").document(patientID).collection("visits").get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    binding.noRecordsTV.visibility = View.VISIBLE
                } else {
                    binding.noRecordsTV.visibility = View.GONE

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
                        history.add(
                            HistoryData(
                                dateTime.toString(),
                                dateTimeFormatted,
                                avgSysBP,
                                avgDiaBP,
                                homeSysBPTarget,
                                homeDiaBPTarget,
                                clinicSysBPTarget,
                                clinicDiaBPTarget
                            )
                        )
                    }

                    sortedHistory = history.sortedByDescending { it.date }
                    println("Sorted History" + sortedHistory)
                    println("Sorted History 1st" + sortedHistory[0])
                    println("Sorted History 1st SYS DATA" + sortedHistory[0].avgSysBP)

                    val adapter = HistoryAdapter(sortedHistory, this)

                    binding.recyclerView.adapter = adapter
                    binding.recyclerView.layoutManager = LinearLayoutManager(this)
                    // This code was found in the amazon build with no inherent purpose
//                    lineChart = findViewById(R.id.syslineChart)
//                    setupLineChart()
//                    diastolicLineChart = findViewById(R.id.diastolicLineChart)
//                    setupDiastolicLineChart()
                }
            }
            .addOnFailureListener { e ->
                println("Error getting documents: $e")
            }
    }
//    This code was found in the amazon build with no inherent purpose
//    private fun setupLineChart() {
//        val systolicEntries = ArrayList<Entry>()
//        val systolicTargetEntries = ArrayList<Entry>()
//        val inputDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
//
//        sortedHistory.forEach { historyData ->
//            val systolicValue = historyData.avgSysBP?.toFloat() ?: 0f
//            val systolicTargetValue = historyData.homeSysBPTarget?.toFloat() ?: 0f
//
//            val dateString = historyData.date // Assuming this is a String
//            val date = inputDateFormat.parse(dateString) // Parse the date string
//            val dateFloat = date.time.toFloat() // Convert date to float for the x-axis
//
//            systolicEntries.add(Entry(dateFloat, systolicValue))
//            systolicTargetEntries.add(Entry(dateFloat, systolicTargetValue))
//        }
//        Collections.sort(systolicEntries, EntryXComparator())
//        Collections.sort(systolicTargetEntries, EntryXComparator())
//
//        // Create data sets for systolic and diastolic values
//        val systolicDataSet = LineDataSet(systolicEntries, "Systolic BP")
//        val systolicTargetDataSet = LineDataSet(systolicTargetEntries, "Systolic Target BP")
//
//        // Customize the data sets appearance (Optional)
//        systolicDataSet.color = Color.BLUE
//        systolicTargetDataSet.color = Color.RED
//
//        // Create LineData with the data sets
//        val lineData = LineData(systolicDataSet, systolicTargetDataSet)
//
//        // Set the custom ValueFormatter for x-axis
//        lineChart.xAxis.valueFormatter = object : ValueFormatter() {
//            private val outputDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
//
//            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
//                return outputDateFormat.format(Date(value.toLong()))
//            }
//        }
//
//        // Set the data on the chart
//        lineChart.data = lineData
//
//        // Refresh the chart
//        lineChart.invalidate()
//    }
//
//    private fun setupDiastolicLineChart() {
//        // Similar code as setupSystolicLineChart() but for diastolic data
//        // Use diastolicLineChart for this setup
//
//        val diastolicTargetEntries = ArrayList<Entry>()
//        val diastolicEntries = ArrayList<Entry>()
//        val inputDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
//
//        sortedHistory.forEach { historyData ->
//            val diastolicTargetValue = historyData.homeDiaBPTarget?.toFloat() ?: 0f
//            val diastolicValue = historyData.avgDiaBP?.toFloat() ?: 0f
//
//            val dateString = historyData.date // Assuming this is a String
//            val date = inputDateFormat.parse(dateString) // Parse the date string
//            val dateFloat = date.time.toFloat() // Convert date to float for the x-axis
//
//
//            println("Target" + diastolicTargetValue)
//            diastolicEntries.add(Entry(dateFloat, diastolicValue))
//            diastolicTargetEntries.add(Entry(dateFloat, diastolicTargetValue))
//        }
//
//        Collections.sort(diastolicEntries, EntryXComparator())
//        Collections.sort(diastolicTargetEntries, EntryXComparator())
//
//        // Create data sets for systolic and diastolic values
//        val diastolicTargetDataSet = LineDataSet(diastolicTargetEntries, "Diastolic Target BP")
//        val diastolicDataSet = LineDataSet(diastolicEntries, "Diastolic BP")
//
//        // Customize the data sets appearance (Optional)
//        diastolicTargetDataSet.color = Color.RED
//        diastolicDataSet.color = Color.BLUE
//
//        // Create LineData with the data sets
//        val lineData = LineData(diastolicTargetDataSet, diastolicDataSet)
//
//        // Set the custom ValueFormatter for x-axis
//        diastolicLineChart.xAxis.valueFormatter = object : ValueFormatter() {
//            private val outputDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
//
//            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
//                return outputDateFormat.format(Date(value.toLong()))
//            }
//        }
//
//        // Set the data on the chart
//        diastolicLineChart.data = lineData
//
//        // Refresh the chart
//        diastolicLineChart.invalidate()
//    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (actionBarDrawerToggle.onOptionsItemSelected(item)) {
            true
        } else super.onOptionsItemSelected(item)
    }

    override fun onItemClick(position: Int) {
        val clickedItem = sortedHistory[position]
        val avgSysBP = clickedItem.avgSysBP.toString()
        val avgDiaBP = clickedItem.avgDiaBP.toString()
        val date = clickedItem.date.toString()

        val bundle = Bundle()

        bundle.putInt("avgSysBP", avgSysBP.toInt())
        bundle.putInt("avgDiaBP", avgDiaBP.toInt())
        bundle.putString("date", date)
        bundle.putString("Source", "History")

        val recommendationIntent = Intent(this, RecommendationActivity::class.java)

        recommendationIntent.putExtras(bundle)

        startActivity(recommendationIntent)
    }
}