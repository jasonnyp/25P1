package com.singhealth.enhance.activities// MainActivity.kt

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.utils.EntryXComparator
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.singhealth.enhance.R
import com.singhealth.enhance.activities.history.HistoryActivity
import com.singhealth.enhance.activities.history.HistoryData
import com.singhealth.enhance.activities.ocr.ScanActivity
import com.singhealth.enhance.activities.patient.ProfileActivity
import com.singhealth.enhance.activities.patient.RegistrationActivity
import com.singhealth.enhance.activities.settings.SettingsActivity
import com.singhealth.enhance.databinding.DashboardBinding
import com.singhealth.enhance.security.SecureSharedPreferences
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.ArrayList
import java.util.Collections
import java.util.Locale


class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: DashboardBinding

    private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle

    private lateinit var patientID: String

    private lateinit var sortedHistory: List<HistoryData>

    private val db = Firebase.firestore
    private val history = ArrayList<HistoryData>()

    private lateinit var lineChart: LineChart
    private lateinit var diastolicLineChart: LineChart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        binding = DashboardBinding.inflate(layoutInflater)
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
        binding.bottomNavigationView.selectedItemId = R.id.item_dashboard

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
                    true
                }

                else -> {
                    false
                }
            }
        }

        // Reload WebView
        binding.reloadBtn.setOnClickListener(View.OnClickListener {
            binding.WB.reload()
        })

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
                    println("Empty Collection: 'visits'")
                } else {


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

                    /*
                    lineChart = findViewById(R.id.syslineChart)
                    setupLineChart()
                    diastolicLineChart = findViewById(R.id.diastolicLineChart)
                    setupDiastolicLineChart()
                    */

                    // WebView (Does not work in emulator, but works on physical device)
                    val myWebView : WebView = binding.WB
                    myWebView.webViewClient = WebViewClient()
                    myWebView.webChromeClient = WebChromeClient()
                    myWebView.settings.javaScriptEnabled = true
                    myWebView.settings.allowContentAccess = true
                    myWebView.settings.domStorageEnabled = true
                    myWebView.settings.loadsImagesAutomatically = true
                    myWebView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    myWebView.settings.setSupportMultipleWindows(true)
                    myWebView.loadUrl("https://enhance-bdc3f.web.app/?params=%7B%22ds14.documentid%22%3A%22${patientID}%22%7D")
                    println("https://enhance-bdc3f.web.app/?params={'ds14.documentid':'${patientID}'}")

                }
            }
            .addOnFailureListener { e ->
                println("Error getting documents: $e")
            }


    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (actionBarDrawerToggle.onOptionsItemSelected(item)) {
            true
        } else super.onOptionsItemSelected(item)
    }
    private fun setupLineChart() {
        val systolicEntries = ArrayList<Entry>()
        val systolicTargetEntries = ArrayList<Entry>()

        val inputDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
        val correctlySortedHistory = sortedHistory.sortedBy { historyData ->
            inputDateFormat.parse(historyData.date)
        }
// Initialize a map to hold date strings to indices
        val dateToIndexMap = correctlySortedHistory.map { it.date }.distinct().withIndex().associate { it.value to it.index.toFloat() }

        sortedHistory.forEach { historyData ->
            val systolicValue = historyData.avgSysBP?.toFloat() ?: 0f
            val systolicTargetValue = historyData.homeSysBPTarget?.toFloat() ?: 0f

            /*val dateString = historyData.date // Assuming this is a String
            val date = inputDateFormat.parse(dateString) // Parse the date string
            val dateFloat = date.time.toFloat() // Convert date to float for the x-axis*/

            val index = dateToIndexMap[historyData.date] ?: 0f // Get the index for the date

            systolicEntries.add(Entry(index, systolicValue))
            systolicTargetEntries.add(Entry(index, systolicTargetValue))
        }
        Collections.sort(systolicEntries, EntryXComparator())
        Collections.sort(systolicTargetEntries, EntryXComparator())

        // Create data sets for systolic and diastolic values
        val systolicDataSet = LineDataSet(systolicEntries, "Systolic BP")
        val systolicTargetDataSet = LineDataSet(systolicTargetEntries, "Systolic Target BP")
        lineChart.description.text = ""
        lineChart.xAxis.labelRotationAngle = -90f

        // Customize the data sets appearance (Optional)
        systolicDataSet.color = Color.BLUE
        systolicDataSet.valueTextSize = 15f // Set your desired text size
        systolicTargetDataSet.color = Color.RED
        systolicTargetDataSet.valueTextSize = 15f // Set your desired text size

        // Create LineData with the data sets
        val lineData = LineData(systolicDataSet, systolicTargetDataSet)

        lineChart.xAxis.textSize = 13f // Set your desired text size
        lineChart.axisLeft.textSize = 15f // Set your desired text size
        lineChart.axisRight.textSize = 15f // Set your desired text size
        lineChart.legend.textSize = 15f // Set your desired t

        //lineChart.xAxis.labelRotationAngle = -45f

        // Set the custom ValueFormatter for x-axis
        lineChart.xAxis.valueFormatter = object : ValueFormatter() {
            private val indexToDateMap = dateToIndexMap.entries.associateBy({ it.value }) { it.key }

            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                return indexToDateMap[value]?.let {
                    val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault()).parse(it)
                    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)
                } ?: ""
            }
        }
        lineChart.xAxis.granularity = 1f
        lineChart.xAxis.labelCount = dateToIndexMap.size

        lineChart.xAxis.position = XAxis.XAxisPosition.BOTTOM

        // Set the data on the chart
        lineChart.data = lineData

        // Refresh the chart
        lineChart.invalidate()
    }

    private fun setupDiastolicLineChart() {
        // Similar code as setupSystolicLineChart() but for diastolic data
        // Use diastolicLineChart for this setup

        val diastolicTargetEntries = ArrayList<Entry>()
        val diastolicEntries = ArrayList<Entry>()
        val inputDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
        val correctlySortedHistory = sortedHistory.sortedBy { historyData ->
            inputDateFormat.parse(historyData.date)
        }

        val dateToIndexMap = correctlySortedHistory.map { it.date }.distinct().withIndex().associate { it.value to it.index.toFloat() }

        sortedHistory.forEach { historyData ->
            val diastolicTargetValue = historyData.homeDiaBPTarget?.toFloat() ?: 0f
            val diastolicValue = historyData.avgDiaBP?.toFloat() ?: 0f

//            val dateString = historyData.date // Assuming this is a String
//            val date = inputDateFormat.parse(dateString) // Parse the date string
//            val dateFloat = date.time.toFloat() // Convert date to float for the x-axis
            val index = dateToIndexMap[historyData.date] ?: 0f // Get the index for the date


            println("Target" + diastolicTargetValue)
            diastolicEntries.add(Entry(index, diastolicValue))
            diastolicTargetEntries.add(Entry(index, diastolicTargetValue))
        }

        Collections.sort(diastolicEntries, EntryXComparator())
        Collections.sort(diastolicTargetEntries, EntryXComparator())

        // Create data sets for systolic and diastolic values
        val diastolicTargetDataSet = LineDataSet(diastolicTargetEntries, "Diastolic Target BP")
        val diastolicDataSet = LineDataSet(diastolicEntries, "Diastolic BP")
        diastolicLineChart.description.text = ""
        diastolicLineChart.xAxis.labelRotationAngle = -90f

        // Customize the data sets appearance (Optional)
        diastolicTargetDataSet.color = Color.RED
        diastolicLineChart.axisLeft.textSize = 15f // Set your desired text size
        diastolicDataSet.color = Color.BLUE
        diastolicDataSet.valueTextSize = 15f // Set your desired text size

        // Create LineData with the data sets
        val lineData = LineData(diastolicDataSet, diastolicTargetDataSet)

        diastolicLineChart.xAxis.textSize = 13f // Set your desired text size
        diastolicLineChart.axisLeft.textSize = 15f // Set your desired text size
        diastolicLineChart.axisRight.textSize = 15f // Set your desired text size
        diastolicLineChart.legend.textSize = 15f // Set your desired t
        // Set the custom ValueFormatter for x-axis
        diastolicLineChart.xAxis.valueFormatter = object : ValueFormatter() {
            private val indexToDateMap = dateToIndexMap.entries.associateBy({ it.value }) { it.key }

            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                return indexToDateMap[value]?.let {
                    val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault()).parse(it)
                    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)
                } ?: ""
            }
        }
        diastolicLineChart.xAxis.granularity = 1f
        diastolicLineChart.xAxis.labelCount = dateToIndexMap.size
        diastolicLineChart.xAxis.position = XAxis.XAxisPosition.BOTTOM

        // Set the data on the chart
        diastolicLineChart.data = lineData

        // Refresh the chart
        diastolicLineChart.invalidate()
    }
}

