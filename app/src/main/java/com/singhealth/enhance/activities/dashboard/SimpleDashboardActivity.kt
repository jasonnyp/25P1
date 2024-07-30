package com.singhealth.enhance.activities.dashboard

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MenuItem
import android.view.View
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
import com.github.mikephil.charting.utils.Utils.drawMultilineText
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.singhealth.enhance.R
import com.singhealth.enhance.activities.MainActivity
import com.singhealth.enhance.activities.diagnosis.bpControlStatus
import com.singhealth.enhance.activities.diagnosis.hypertensionStatus
import com.singhealth.enhance.activities.diagnosis.showRecommendation
import com.singhealth.enhance.activities.history.HistoryActivity
import com.singhealth.enhance.activities.history.HistoryData
import com.singhealth.enhance.activities.ocr.ScanActivity
import com.singhealth.enhance.activities.patient.ProfileActivity
import com.singhealth.enhance.activities.patient.RegistrationActivity
import com.singhealth.enhance.activities.settings.SettingsActivity
import com.singhealth.enhance.databinding.ActivitySimpleDashboardBinding
import com.singhealth.enhance.security.AESEncryption
import com.singhealth.enhance.security.SecureSharedPreferences
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.Locale

class SimpleDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySimpleDashboardBinding

    private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle

    private lateinit var patientID: String
    private lateinit var decryptedPatientID: String
    private lateinit var bpHypertensionStatus: String
    private lateinit var bpHomeControlStatus: String
    private lateinit var bpReccomendation: String

    private lateinit var sortedHistory: List<HistoryData>

    private val db = Firebase.firestore
    private val history = ArrayList<HistoryData>()

    private lateinit var lineChart: LineChart
    private lateinit var diastolicLineChart: LineChart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySimpleDashboardBinding.inflate(layoutInflater)
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
            decryptedPatientID = AESEncryption().decrypt(patientID)
        }

        binding.printSourceBtn.setOnClickListener {
            printCharts()
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
                        val clinicSysBP = document.get("clinicSysBP") as? Long
                        val clinicDiaBP = document.get("clinicDiaBP") as? Long

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
                            )
                        )
                    }
                }

                sortedHistory = history.sortedByDescending { it.date }

                if (sortedHistory.isEmpty()) {
                    Toast.makeText(
                        this,
                        getString(R.string.dashboard_no_records_found),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {

                    println("Sorted History" + sortedHistory)
                    println("Sorted History 1st" + sortedHistory[0])
                    println("Sorted History 1st SYS DATA" + sortedHistory[0].avgSysBP)
                    bpHypertensionStatus = hypertensionStatus(
                        this,
                        sortedHistory[0].avgSysBP ?: 0L,
                        sortedHistory[0].avgDiaBP ?: 0L,
                        sortedHistory[0].clinicSysBP ?: 0L,
                        sortedHistory[0].clinicDiaBP ?: 0L,
                        sortedHistory[0].homeSysBPTarget ?: 0L,
                        sortedHistory[0].homeDiaBPTarget ?: 0L
                    )
                    bpHomeControlStatus = bpControlStatus(
                        this,
                        bpHypertensionStatus
                    )
                    bpReccomendation = showRecommendation(
                        this,
                        bpHomeControlStatus
                    )

                    println("Hypertension Status: $bpHypertensionStatus")
                    println("Home Control Status: $bpHomeControlStatus")
                    println("Recommendation: $bpReccomendation")

                    lineChart = findViewById(R.id.syslineChart)
                    setupLineChart()
                    diastolicLineChart = findViewById(R.id.diastolicLineChart)
                    setupDiastolicLineChart()
                }
            }
    }

    private fun setupLineChart() {
        val systolicEntries = ArrayList<Entry>()
        val systolicTargetEntries = ArrayList<Entry>()

        val inputDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val outputDateFormat = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
        val limitedHistory = sortedHistory
            .sortedBy { historyData -> inputDateFormat.parse(historyData.date) }
            .takeLast(3)

        val dateToIndexMap = limitedHistory.map { it.date }
            .distinct()
            .withIndex()
            .associate { it.value to it.index.toFloat() }

        limitedHistory.forEach { historyData ->
            val systolicValue = historyData.avgSysBP?.toFloat() ?: 0f
            val systolicTargetValue = historyData.homeSysBPTarget?.toFloat() ?: 0f
            val index = dateToIndexMap[historyData.date] ?: 0f

            systolicEntries.add(Entry(index, systolicValue))
            systolicTargetEntries.add(Entry(index, systolicTargetValue))
        }

        systolicEntries.sortBy { it.x }
        systolicTargetEntries.sortBy { it.x }

        val systolicDataSet = LineDataSet(systolicEntries, "Systolic BP").apply {
            color = Color.BLUE
            valueTextSize = 15f
        }

        val systolicTargetDataSet = LineDataSet(systolicTargetEntries, "Systolic Target BP").apply {
            color = Color.RED
            valueTextSize = 15f
        }

        lineChart.apply {
            description.text = ""
            xAxis.apply {
                labelRotationAngle = 0f
                textSize = 13f
                granularity = 1f
                labelCount = dateToIndexMap.size
                position = XAxis.XAxisPosition.BOTTOM
                setAvoidFirstLastClipping(true)
                axisMinimum = -0.2f // Add padding to the left
                axisMaximum = (dateToIndexMap.size - 1).toFloat() + 0.2f // Add padding to the right
                valueFormatter = object : ValueFormatter() {
                    private val indexToDateMap = dateToIndexMap.entries.associateBy({ it.value }) { it.key }

                    override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                        return indexToDateMap[value]?.let {
                            outputDateFormat.format(inputDateFormat.parse(it))
                        } ?: ""
                    }
                }
            }
            axisLeft.apply {
                textSize = 15f
                setDrawGridLines(false)
            }
            axisRight.apply {
                textSize = 15f
                setDrawGridLines(false)
            }
            legend.textSize = 15f
            extraBottomOffset = 10f // Add extra offset for spacing
            data = LineData(systolicDataSet, systolicTargetDataSet)
            invalidate()
        }
    }

    private fun setupDiastolicLineChart() {
        val diastolicEntries = ArrayList<Entry>()
        val diastolicTargetEntries = ArrayList<Entry>()

        val inputDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
        val outputDateFormat = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
        val limitedHistory = sortedHistory
            .sortedBy { historyData -> inputDateFormat.parse(historyData.date) }
            .takeLast(3)

        val dateToIndexMap = limitedHistory.map { it.date }
            .distinct()
            .withIndex()
            .associate { it.value to it.index.toFloat() }

        limitedHistory.forEach { historyData ->
            val diastolicValue = historyData.avgDiaBP?.toFloat() ?: 0f
            val diastolicTargetValue = historyData.homeDiaBPTarget?.toFloat() ?: 0f
            val index = dateToIndexMap[historyData.date] ?: 0f

            diastolicEntries.add(Entry(index, diastolicValue))
            diastolicTargetEntries.add(Entry(index, diastolicTargetValue))
        }

        diastolicEntries.sortBy { it.x }
        diastolicTargetEntries.sortBy { it.x }

        val diastolicDataSet = LineDataSet(diastolicEntries, "Diastolic BP").apply {
            color = Color.BLUE
            valueTextSize = 15f
        }

        val diastolicTargetDataSet = LineDataSet(diastolicTargetEntries, "Diastolic Target BP").apply {
            color = Color.RED
            valueTextSize = 15f
        }

        diastolicLineChart.apply {
            description.text = ""
            xAxis.apply {
                labelRotationAngle = 0f
                textSize = 13f
                granularity = 1f
                labelCount = dateToIndexMap.size
                position = XAxis.XAxisPosition.BOTTOM
                setAvoidFirstLastClipping(true)
                axisMinimum = -0.2f // Add padding to the left
                axisMaximum = (dateToIndexMap.size - 1).toFloat() + 0.2f // Add padding to the right
                valueFormatter = object : ValueFormatter() {
                    private val indexToDateMap = dateToIndexMap.entries.associateBy({ it.value }) { it.key }

                    override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                        return indexToDateMap[value]?.let {
                            outputDateFormat.format(inputDateFormat.parse(it))
                        } ?: ""
                    }
                }
            }
            axisLeft.apply {
                textSize = 15f
                setDrawGridLines(false)
            }
            axisRight.apply {
                textSize = 15f
                setDrawGridLines(false)
            }
            legend.textSize = 15f
            extraBottomOffset = 10f // Add extra offset for spacing
            data = LineData(diastolicDataSet, diastolicTargetDataSet)
            invalidate()
        }
    }

    private fun printCharts() {
        val printManager = getSystemService(PRINT_SERVICE) as PrintManager
        val formatter = DateTimeFormatter.ofPattern("ddMMyy_HHmmss")
        val current = LocalDateTime.now().format(formatter)
        val jobName = "${decryptedPatientID}_${current}_Charts"

        val printAdapter = object : PrintDocumentAdapter() {
            private var currentPrintAttributes: PrintAttributes? = null

            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                currentPrintAttributes = newAttributes
                val printInfo = PrintDocumentInfo.Builder(jobName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback?.onLayoutFinished(printInfo, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onWriteCancelled()
                    return
                }
                currentPrintAttributes?.let { printAttributes ->
                    val pdfDocument = PrintedPdfDocument(this@SimpleDashboardActivity, printAttributes)

                    // Create a page description
                    val totalHeight = lineChart.height / 2 + diastolicLineChart.height / 2 + 600 // Adding extra space for headings and text
                    val pageInfo = PdfDocument.PageInfo.Builder(lineChart.width, totalHeight, 1).create()

                    // Start a page
                    val page = pdfDocument.startPage(pageInfo)

                    // Draw the charts and text on the page
                    drawChartsAndTextOnPage(page, lineChart, diastolicLineChart)
                    pdfDocument.finishPage(page)

                    try {
                        destination?.fileDescriptor?.let {
                            FileOutputStream(it).use { fileOutputStream ->
                                pdfDocument.writeTo(fileOutputStream)
                            }
                        }
                    } catch (e: IOException) {
                        callback?.onWriteFailed(e.toString())
                        return
                    } finally {
                        pdfDocument.close()
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } ?: run {
                    // Handle the case where currentPrintAttributes is null
                    callback?.onWriteFailed("Print attributes are null")
                }
            }

            private fun drawChartsAndTextOnPage(page: PdfDocument.Page, chart1: LineChart, chart2: LineChart) {
                val canvas = page.canvas
                val paint = Paint().apply {
                    textSize = 45f
                    isFakeBoldText = true
                }
                val smallPaint = Paint().apply {
                    textSize = 35f
                }

                val pageInfo = page.info
                println("Page Width: ${pageInfo.pageWidth}")
                println("Page Height: ${pageInfo.pageHeight}")
                val headingHeight = 100
                val chartSpacing = 50
                val padding = 2f

                // Draw the text fields
                canvas.drawText("Today's Recommendation", padding, (80).toFloat(), paint)
                drawMultilineText(bpReccomendation, canvas, smallPaint, padding, (100).toFloat(), pageInfo.pageWidth)

                // Draw headings
                canvas.drawText("Systolic Comparison Chart", padding, (headingHeight + 150).toFloat(), paint)

                // Draw the first chart
                val bitmap1 = getBitmapFromView(chart1)
                val scaledBitmap1 = Bitmap.createScaledBitmap(bitmap1, chart1.width/5*4, chart1.height/5*4 , true)
                canvas.drawBitmap(scaledBitmap1, 101f, headingHeight.toFloat()+150, paint)

                // Draw the second heading
                canvas.drawText("Diastolic Comparison Chart", padding, (scaledBitmap1.height + headingHeight + 200 + chartSpacing - 20).toFloat(), paint)

                // Draw the second chart
                val bitmap2 = getBitmapFromView(chart2)
                println("chart width ${chart2.width/5*4}")
                val scaledBitmap2 = Bitmap.createScaledBitmap(bitmap2, chart2.width/5*4 , chart2.height/5*4 , true)
                val spacing = scaledBitmap1.height + headingHeight + chartSpacing + 200 - 20
                canvas.drawBitmap(scaledBitmap2, 101f, spacing.toFloat(), paint)
            }

            private fun getBitmapFromView(view: View): Bitmap {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                view.draw(canvas)
                return bitmap
            }
        }

        val printAttributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.NA_LETTER)
            .setResolution(PrintAttributes.Resolution("default", "default", 300, 300))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        printManager.print(jobName, printAdapter, printAttributes)
    }

    private fun drawMultilineText(text: String, canvas: Canvas, paint: Paint, x: Float, y: Float, pageWidth: Int) {
        val textPaint = TextPaint(paint)
        val textWidth = pageWidth - x.toInt()

        val staticLayout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .build()

        canvas.save()
        canvas.translate(x, y)
        staticLayout.draw(canvas)
        canvas.restore()
    }

}
