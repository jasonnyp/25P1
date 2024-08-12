package com.singhealth.enhance.activities.ocr

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.isDigitsOnly
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.singhealth.enhance.R
import com.singhealth.enhance.activities.validation.errorDialogBuilder
import com.singhealth.enhance.activities.validation.firebaseErrorDialog
import com.singhealth.enhance.activities.validation.patientNotFoundInSessionErrorDialog
import com.singhealth.enhance.activities.result.RecommendationActivity
import com.singhealth.enhance.databinding.ActivityVerifyScanBinding
import com.singhealth.enhance.security.AESEncryption
import com.singhealth.enhance.security.SecureSharedPreferences
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.roundToInt

object ResourcesHelper {
    // Retrieves the string from the res/values/strings.xml file, the same as getString() on activity files
    fun getString(context: Context, resId: Int): String {
        return context.getString(resId)
    }

    // Same as above, but includes a single input of any type
    fun getString(context: Context, @StringRes resId: Int, vararg formatArgs: Any): String {
        return context.getString(resId, *formatArgs)
    }

    // Same as above, but includes 2 integer values
    fun getString(context: Context, @StringRes resId: Int, int1: Int, int2: Int): String {
        return context.getString(resId, int1, int2)
    }
}

class VerifyScanActivity : AppCompatActivity() {
    private lateinit var binding: ActivityVerifyScanBinding

    private lateinit var progressDialog: ProgressDialog

    private lateinit var patientID: String

    private var homeSysBPTarget = 0
    private var homeDiaBPTarget = 0
    private var clinicSysBPTarget = 0
    private var clinicDiaBPTarget = 0
    private var clinicSysBP = 0
    private var clinicDiaBP = 0
    private lateinit var targetSysBP: String
    private lateinit var targetDiaBP: String
    private var sevenDay: Boolean = false

    private var sysBPList: MutableList<String> = mutableListOf()
    private var diaBPList: MutableList<String> = mutableListOf()
    private var sysBPListHistory: MutableList<String> = mutableListOf()
    private var diaBPListHistory: MutableList<String> = mutableListOf()

    private val sysBPFields = mutableListOf<TextInputEditText>()
    private val diaBPFields = mutableListOf<TextInputEditText>()

    private var totalSysBP = 0
    private var totalDiaBP = 0
    private var avgSysBP = 0
    private var avgDiaBP = 0

    private val undoStack = mutableListOf<Pair<MutableList<String>, MutableList<String>>>()
    private val maxUndoStackSize = 1

    private val db = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityVerifyScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                startActivity(Intent(this@VerifyScanActivity, ScanActivity::class.java))
                finish()
            }
        })

        val patientSharedPreferences =
            SecureSharedPreferences.getSharedPreferences(applicationContext)
        if (patientSharedPreferences.getString("patientID", null).isNullOrEmpty()) {
            patientNotFoundInSessionErrorDialog(this)
        } else {
            patientID = patientSharedPreferences.getString("patientID", null).toString()
            binding.patientIDTV.text = AESEncryption().decrypt(patientID)
        }

        val scanBundle = intent.extras

        progressDialog = ProgressDialog(this)
        progressDialog.setCanceledOnTouchOutside(false)

        if (intent.getBooleanExtra("showProgressDialog", true)) {
            progressDialog.setTitle("Processing image")
            progressDialog.setMessage("Please wait a moment...")
            progressDialog.show()
        }

        targetSysBP = patientSharedPreferences.getString("targetSysBP", null).toString()
        targetDiaBP = patientSharedPreferences.getString("targetDiaBP", null).toString()

        sevenDay = scanBundle?.getBoolean("sevenDay", false)!!

        sysBPList = scanBundle?.getStringArrayList("sysBPList")?.toMutableList()!!
        diaBPList = scanBundle?.getStringArrayList("diaBPList")?.toMutableList()!!

        val docRef = db.collection("patients").document(patientID)

        docRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    if (AESEncryption().decrypt(
                            document.getString("targetSys").toString()
                        ) == "" || AESEncryption().decrypt(
                            document.getString("targetDia").toString()
                        ) == ""
                    ) {
                        homeSysBPTarget = 0
                        homeDiaBPTarget = 0
                    } else {
                        homeSysBPTarget =
                            AESEncryption().decrypt(document.getString("targetSys").toString())
                                .toInt()
                        homeDiaBPTarget =
                            AESEncryption().decrypt(document.getString("targetDia").toString())
                                .toInt()
                    }

                    clinicSysBPTarget = if (homeSysBPTarget == 0) {
                        0
                    } else {
                        homeSysBPTarget + 5
                    }
                    clinicDiaBPTarget = if (homeDiaBPTarget == 0) {
                        0
                    } else {
                        homeDiaBPTarget + 5
                    }

                    binding.verifyHomeSys.text = homeSysBPTarget.toString()
                    binding.verifyHomeDia.text = homeDiaBPTarget.toString()
                    binding.homeBPTargetTV.text = String.format(
                        "%s / %s",
                        homeSysBPTarget.toString(),
                        homeDiaBPTarget.toString()
                    )
                    binding.verifyClinicTargetSys.text = clinicSysBPTarget.toString()
                    binding.verifyClinicTargetDia.text = clinicDiaBPTarget.toString()
                    binding.clinicBPTargetTV.text = String.format(
                        "%s / %s",
                        clinicSysBPTarget.toString(),
                        clinicDiaBPTarget.toString()
                    )
                }
            }
            .addOnFailureListener { e ->
                firebaseErrorDialog(this, e, docRef)
            }

        // Retrieve previous data
        if (scanBundle != null) {
            if (scanBundle.containsKey("homeSysBPTarget")) {
                binding.verifyHomeSys.text = scanBundle.getString("homeSysBPTarget")
            }
            if (scanBundle.containsKey("homeDiaBPTarget")) {
                binding.verifyHomeDia.text = scanBundle.getString("homeDiaBPTarget")
            }
            if (scanBundle.containsKey("clinicSysBPTarget")) {
                binding.verifyClinicTargetSys.text = scanBundle.getString("clinicSysBPTarget")
            }
            if (scanBundle.containsKey("clinicDiaBPTarget")) {
                binding.verifyClinicTargetDia.text = scanBundle.getString("clinicDiaBPTarget")
            }

            if (scanBundle.containsKey("homeSysBPTarget") && scanBundle.containsKey("homeDiaBPTarget")) {
                binding.homeBPTargetTV.text = String.format(
                    "%s / %s",
                    scanBundle.getString("homeSysBPTarget"),
                    scanBundle.getString("homeDiaBPTarget")
                )
            }
            if (scanBundle.containsKey("clinicSysBPTarget") && scanBundle.containsKey("clinicDiaBPTarget")) {
                binding.homeBPTargetTV.text = String.format(
                    "%s / %s",
                    scanBundle.getString("clinicSysBPTarget"),
                    scanBundle.getString("clinicDiaBPTarget")
                )
            }

            if (scanBundle.containsKey("sysBPListHistory") && scanBundle.containsKey("diaBPListHistory")) {
                sysBPListHistory =
                    scanBundle.getStringArrayList("sysBPListHistory")?.toMutableList()!!
                diaBPListHistory =
                    scanBundle.getStringArrayList("diaBPListHistory")?.toMutableList()!!

                for (i in 0 until minOf(sysBPListHistory.size, diaBPListHistory.size)) {
                    addRow(sysBPListHistory[i], diaBPListHistory[i])
                }

                addDivider()

                println("sysBPListHistory: $sysBPListHistory")
                println("diaBPListHistory: $diaBPListHistory")
            }
        }

        if (sevenDay) {
            findViewById<Button>(R.id.addRowBtn).visibility = View.GONE
            sevenDayCheck()
        } else {
            println("Not seven day check")

            // Add newly scanned records
            if (scanBundle != null) {
                if (scanBundle.containsKey("sysBPList") && scanBundle.containsKey("diaBPList")) {
                    sysBPList = scanBundle.getStringArrayList("sysBPList")?.toMutableList()!!
                    diaBPList = scanBundle.getStringArrayList("diaBPList")?.toMutableList()!!

                    for (i in 0 until minOf(sysBPList.size, diaBPList.size)) {
                        addRow(sysBPList[i], diaBPList[i])
                    }

                }
            }
        }
        println("sysBPList after adding new scans: $sysBPList")
        println("diaBPList after adding new scans: $diaBPList")

        val validSysBPList = sysBPList.filter { it != "-1" }
        val validDiaBPList = diaBPList.filter { it != "-1" }

        var totalRows = maxOf(validSysBPList.size, validDiaBPList.size)
        if (sysBPListHistory.isNotEmpty() && diaBPListHistory.isNotEmpty()) {
            val validSysBPListHistory = sysBPListHistory.filter { it != "-1" }
            val validDiaBPListHistory = diaBPListHistory.filter { it != "-1" }
            totalRows += maxOf(validSysBPListHistory.size, validDiaBPListHistory.size)
        }

        val currentRows = maxOf(validSysBPList.size, validDiaBPList.size)

        println("Total rows: $totalRows")
        println("Current rows: $currentRows")

        println("sysBPFields: ${sysBPFields.size}")
        println("diaBPFields: ${diaBPFields.size}")
        println("sysBPFields after adding new scans: $sysBPFields")
        println("diaBPFields after adding new scans: $diaBPFields")

        // Display toast for current number of records detected
        if (currentRows > 1 && totalRows <= 1) {
            MaterialAlertDialogBuilder(this)
                .setMessage(
                    ResourcesHelper.getString(
                        this,
                        R.string.verify_scan_rows,
                        currentRows,
                        totalRows
                    )
                )
                .setPositiveButton(
                    ResourcesHelper.getString(
                        this,
                        R.string.ok_dialog
                    )
                ) { dialog, _ -> dialog.dismiss() }
                .show()
        } else if (currentRows > 1 && totalRows > 1) {
            MaterialAlertDialogBuilder(this)
                .setMessage(
                    ResourcesHelper.getString(
                        this,
                        R.string.verify_scan_rows,
                        currentRows,
                        totalRows
                    )
                )
                .setPositiveButton(
                    ResourcesHelper.getString(
                        this,
                        R.string.ok_dialog
                    )
                ) { dialog, _ -> dialog.dismiss() }
                .show()
        } else if (currentRows == 1 && totalRows <= 1) {
            MaterialAlertDialogBuilder(this)
                .setMessage(
                    ResourcesHelper.getString(
                        this,
                        R.string.verify_scan_rows,
                        currentRows,
                        totalRows
                    )
                )
                .setPositiveButton(
                    ResourcesHelper.getString(
                        this,
                        R.string.ok_dialog
                    )
                ) { dialog, _ -> dialog.dismiss() }
                .show()
        } else if (currentRows == 1 && totalRows > 1) {
            MaterialAlertDialogBuilder(this)
                .setMessage(
                    ResourcesHelper.getString(
                        this,
                        R.string.verify_scan_rows,
                        currentRows,
                        totalRows
                    )
                )
                .setPositiveButton(
                    ResourcesHelper.getString(
                        this,
                        R.string.ok_dialog
                    )
                ) { dialog, _ -> dialog.dismiss() }
                .show()
        } else {
            MaterialAlertDialogBuilder(this)
                .setMessage(ResourcesHelper.getString(this, R.string.verify_scan_no_records))
                .setPositiveButton(
                    ResourcesHelper.getString(
                        this,
                        R.string.ok_dialog
                    )
                ) { dialog, _ -> dialog.dismiss() }
                .show()
        }

        // Prompt user if total records captured is less than 12
        if (totalRows < 12) {
            errorDialogBuilder(
                this,
                getString(R.string.verify_scan_inadequate_reading_header),
                getString(R.string.verify_scan_inadequate_reading_body),
                ScanActivity::class.java
            )
        }

        // Check BP records for errors
        postScanValidation()

        // Add new row
        binding.addRowBtn.setOnClickListener { addRow(null, null) }

        // More options button
        binding.moreOptionsBtn.setOnClickListener {
            val modalBottomSheet = ModalBottomSheet(this, sevenDay)
            modalBottomSheet.show(supportFragmentManager, ModalBottomSheet.TAG)
        }

        // Calculate and save average BP, home BP and clinic BP targets, then display outcome and recommendation (separate activity)
        binding.calculateAvgBPBtn.setOnClickListener {
            if (validateFields()) {
                getBPTarget()
                val finalRows = maxOf(sysBPList.size, diaBPList.size)
                if (sevenDay){
                    calcSevenDayAvgBP()
                } else {
                    calcAvgBP()
                }
                clinicSysBP = binding.verifyClinicSys.text.toString().toInt()
                clinicDiaBP = binding.verifyClinicDia.text.toString().toInt()

                // TODO: Save record into database
                val visit = hashMapOf(
                    "date" to LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")),
                    "homeSysBPTarget" to homeSysBPTarget,
                    "homeDiaBPTarget" to homeDiaBPTarget,
                    "clinicSysBPTarget" to clinicSysBPTarget,
                    "clinicDiaBPTarget" to clinicDiaBPTarget,
                    "averageSysBP" to avgSysBP,
                    "averageDiaBP" to avgDiaBP,
                    "clinicSysBP" to clinicSysBP,
                    "clinicDiaBP" to clinicDiaBP,
                    "scanRecordCount" to finalRows
                )

                db.collection("patients").document(patientID).collection("visits").add(visit)
                    .addOnSuccessListener {
                        Toast.makeText(
                            this,
                            ResourcesHelper.getString(
                                this,
                                R.string.verify_scan_calculation_successful
                            ),
                            Toast.LENGTH_SHORT
                        )
                            .show()

                        val bundle = Bundle()

                        bundle.putInt("avgSysBP", avgSysBP)
                        bundle.putInt("avgDiaBP", avgDiaBP)
                        bundle.putInt("clinicSysBP", clinicSysBP)
                        bundle.putInt("clinicDiaBP", clinicDiaBP)
                        bundle.putInt("scanRecordCount", finalRows)
                        bundle.putString("Source", "Scan")

                        val recommendationIntent = Intent(this, RecommendationActivity::class.java)

                        recommendationIntent.putExtras(bundle)

                        startActivity(recommendationIntent)
                    }
                    .addOnFailureListener { e ->
                        errorDialogBuilder(
                            this,
                            ResourcesHelper.getString(this, R.string.verify_scan_saving_header),
                            ResourcesHelper.getString(this, R.string.verify_scan_saving_body, e)
                        )
                    }
            } else {
                errorDialogBuilder(
                    this,
                    ResourcesHelper.getString(this, R.string.verify_scan_error_header),
                    ResourcesHelper.getString(this, R.string.verify_scan_error_body)
                )
            }
        }

        binding.verifyClinicSys.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s?.toString()?.toIntOrNull()
                if (input != null && input in 91..209) {
                    // Valid range for verifyClinicSys (91 to 209)
                    setError(binding.verifyClinicSysBox, null)
                } else {
                    // Invalid range, set an error message and icon
                    setError(
                        binding.verifyClinicSysBox,
                        ResourcesHelper.getString(
                            this@VerifyScanActivity,
                            R.string.verify_scan_valid_value,
                            91,
                            209
                        )
                    )
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.verifyClinicDia.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s?.toString()?.toIntOrNull()
                if (input != null && input in 61..119) {
                    // Valid range for verifyClinicDia (61 to 119)
                    setError(binding.verifyClinicDiaBox, null)
                } else {
                    // Invalid range, set an error message and icon
                    setError(
                        binding.verifyClinicDiaBox,
                        ResourcesHelper.getString(
                            this@VerifyScanActivity,
                            R.string.verify_scan_valid_value,
                            61,
                            119
                        )
                    )
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        progressDialog.dismiss()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                startActivity(Intent(this, ScanActivity::class.java))
                finish()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    fun continueScan() {
        sysBPListHistory.clear()
        diaBPListHistory.clear()

        for (field in sysBPFields) {
            sysBPListHistory.add(field.text.toString())
        }
        for (field in diaBPFields) {
            diaBPListHistory.add(field.text.toString())
        }

        val scanIntent = Intent(this, ScanActivity::class.java)

        binding.verifyHomeSys.let {
            scanIntent.putExtra("homeSysBPTarget", binding.verifyHomeSys.text.toString())
        }
        binding.verifyHomeDia.let {
            scanIntent.putExtra("homeDiaBPTarget", binding.verifyHomeDia.text.toString())
        }
        binding.verifyClinicTargetSys?.let {
            scanIntent.putExtra("clinicSysBPTarget", binding.verifyClinicTargetSys.text.toString())
        }
        binding.verifyClinicTargetDia?.let {
            scanIntent.putExtra("clinicDiaBPTarget", binding.verifyClinicTargetDia.text.toString())
        }
        binding.verifyClinicSys?.let {
            scanIntent.putExtra("clinicSysBPTarget", binding.verifyClinicSys.text.toString())
        }
        binding.verifyClinicDia?.let {
            scanIntent.putExtra("clinicDiaBPTarget", binding.verifyClinicDia.text.toString())
        }

        if (!sysBPListHistory.isNullOrEmpty()) {
            scanIntent.putStringArrayListExtra("sysBPListHistory", ArrayList(sysBPListHistory))
        }
        if (!diaBPListHistory.isNullOrEmpty()) {
            scanIntent.putStringArrayListExtra("diaBPListHistory", ArrayList(diaBPListHistory))
        }

        scanIntent.putExtra("showContinueScan", true)

        startActivity(scanIntent)
        finish()
    }

    fun rescanRecords() {
        MaterialAlertDialogBuilder(this)
            .setTitle(ResourcesHelper.getString(this, R.string.verify_scan_rescan_header))
            .setMessage(ResourcesHelper.getString(this, R.string.verify_scan_rescan_body))
            .setNegativeButton(
                ResourcesHelper.getString(
                    this,
                    R.string.no_dialog
                )
            ) { dialog, _ -> dialog.dismiss() }
            .setPositiveButton(ResourcesHelper.getString(this, R.string.yes_dialog)) { _, _ ->
                val scanIntent = Intent(this, ScanActivity::class.java)

                binding.verifyHomeSys?.let {
                    scanIntent.putExtra(
                        "homeSysBPTarget",
                        binding.verifyHomeSys.text.toString()
                    )
                }
                binding.verifyHomeDia?.let {
                    scanIntent.putExtra(
                        "homeDiaBPTarget",
                        binding.verifyHomeDia.text.toString()
                    )
                }
                binding.verifyClinicTargetSys?.let {
                    scanIntent.putExtra(
                        "clinicSysBPTarget",
                        binding.verifyClinicTargetSys.text.toString()
                    )
                }
                binding.verifyClinicTargetDia?.let {
                    scanIntent.putExtra(
                        "clinicDiaBPTarget",
                        binding.verifyClinicTargetDia.text.toString()
                    )
                }
                binding.verifyClinicSys?.let {
                    scanIntent.putExtra(
                        "clinicSysBP",
                        binding.verifyClinicSys.text.toString()
                    )
                }
                binding.verifyClinicDia?.let {
                    scanIntent.putExtra(
                        "clinicDiaBP",
                        binding.verifyClinicDia.text.toString()
                    )
                }

                if (!sysBPListHistory.isNullOrEmpty()) {
                    scanIntent.putStringArrayListExtra(
                        "sysBPListHistory",
                        ArrayList(sysBPListHistory)
                    )
                }
                if (!diaBPListHistory.isNullOrEmpty()) {
                    scanIntent.putStringArrayListExtra(
                        "diaBPListHistory",
                        ArrayList(diaBPListHistory)
                    )
                }

                startActivity(scanIntent)
                finish()
            }
            .show()
    }

    fun discardProgress() {
        errorDialogBuilder(
            this,
            ResourcesHelper.getString(this, R.string.verify_scan_discard_header),
            ResourcesHelper.getString(this, R.string.verify_scan_discard_body),
            ScanActivity::class.java,
            R.drawable.ic_delete
        )
    }

    private fun saveStateForUndo() {
        if (undoStack.isEmpty() || undoStack.last().first != sysBPList || undoStack.last().second != diaBPList) {
            if (undoStack.size >= maxUndoStackSize) {
                undoStack.removeAt(0)
            } else {
                undoStack.add(Pair(sysBPList.toMutableList(), diaBPList.toMutableList()))
            }
        }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            println("Performing undo")
            val lastState = undoStack[undoStack.size - 1]
            sysBPList.clear()
            sysBPList.addAll(lastState.first)
            diaBPList.clear()
            diaBPList.addAll(lastState.second)
            println("Restored sysBPList: $sysBPList")
            println("Restored diaBPList: $diaBPList")
            refreshViews()
        } else {
            println("Undo stack is empty")
        }
    }

    private fun refreshViews() {
        println("Refreshing views")
        binding.rowBPRecordLL.removeAllViews()
        sysBPFields.clear()
        diaBPFields.clear()
        if (sevenDay) {
            sevenDayCheck()
        } else {
            for (i in 0 until maxOf(sysBPList.size, diaBPList.size)) {
                addRow(sysBPList[i], diaBPList[i])
            }
        }
        postScanValidation()
        println("sysBPList after adding updating scans: $sysBPList")
        println("diaBPList after adding updating scans: $diaBPList")
    }

    private fun postScanValidation() {
        var errorCount = 0

        if (sevenDay) {
            println("sysBPlist size is ${sysBPList.size}")
            println("sysBPFields size is ${sysBPFields.size}")

            for (i in 0 until sysBPFields.size) {

                if (sysBPFields[i].text.isNullOrEmpty()) {
                    continue
                } else if (!sysBPFields[i].text!!.isDigitsOnly()) {
                    errorCount += 1
                    setError(
                        sysBPFields[i].parent.parent as TextInputLayout,
                        ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
                    )
                } else if (sysBPFields[i].text!!.toString().toInt() == 999) {
                    errorCount++
                    sysBPFields[i].error = "Replace value."
                }
            }

            for (i in 0 until diaBPFields.size) {

                if (diaBPFields[i].text.isNullOrEmpty()) {
                    continue
                } else if (!diaBPFields[i].text!!.isDigitsOnly()) {
                    errorCount += 1
                    setError(
                        diaBPFields[i].parent.parent as TextInputLayout,
                        ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
                    )
                } else if (diaBPFields[i].text!!.toString().toInt() == 999) {
                    errorCount++
                    diaBPFields[i].error = "Replace value."
                }
            }

        } else {

            // With reference from MOH clinical practice guidelines 1/2017 @ https://www.moh.gov.sg/docs/librariesprovider4/guidelines/cpg_hypertension-booklet---nov-2017.pdf
            for (i in 0 until sysBPList.size) {
                val currentValueLength = sysBPFields[i].text.toString().length


                if (sysBPFields[i].text.isNullOrEmpty()) {
                    errorCount += 1
                    setError(
                        sysBPFields[i].parent.parent as TextInputLayout,
                        ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
                    )
                } else if (!sysBPFields[i].text!!.isDigitsOnly()) {
                    errorCount += 1
                    setError(
                        sysBPFields[i].parent.parent as TextInputLayout,
                        ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
                    )
                } else if (sysBPFields[i].text!!.toString().toInt() == 999) {
                    errorCount++
                    sysBPFields[i].error = "Replace value."
                } else if (currentValueLength !in 2..3) {
                    errorCount += 1
                    setError(
                        sysBPFields[i].parent.parent as TextInputLayout,
                        ResourcesHelper.getString(this, R.string.verify_scan_invalid_value)
                    )
                } else if (sysBPFields[i].text.toString().toInt() !in 80..230) {
                    errorCount += 1
                    setError(
                        sysBPFields[i].parent.parent as TextInputLayout,
                        ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
                    )
                }
            }

            for (i in 0 until diaBPList.size) {
                val currentValueLength = diaBPFields[i].text.toString().length

                if (diaBPFields[i].text.isNullOrEmpty()) {
                    errorCount += 1
                    setError(
                        diaBPFields[i].parent.parent as TextInputLayout,
                        ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
                    )
                } else if (!diaBPFields[i].text!!.isDigitsOnly()) {
                    errorCount += 1
                    setError(
                        diaBPFields[i].parent.parent as TextInputLayout,
                        ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
                    )
                } else if (diaBPFields[i].text!!.toString().toInt() == 999) {
                    errorCount++
                    diaBPFields[i].error = "Replace value."
                } else if (currentValueLength !in 2..3) {
                    errorCount += 1
                    setError(
                        diaBPFields[i].parent.parent as TextInputLayout,
                        ResourcesHelper.getString(this, R.string.verify_scan_invalid_value)
                    )
                } else if (diaBPFields[i].text.toString().toInt() !in 45..135) {
                    errorCount += 1
                    setError(
                        diaBPFields[i].parent.parent as TextInputLayout,
                        ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
                    )
                }
            }

            // Show error text but doesn't add to error count of current records
            for (i in 0 until sysBPListHistory.size) {
                val currentValueLength = sysBPFields[i].text.toString().length

                if (sysBPFields[i].text.isNullOrEmpty()) {
                    setError(
                        sysBPFields[i].parent.parent as TextInputLayout,
                        ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
                    )
                } else if (!sysBPFields[i].text!!.isDigitsOnly()) {
                    setError(
                        sysBPFields[i].parent.parent as TextInputLayout,
                        ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
                    )
                } else if (sysBPFields[i].text!!.toString().toInt() == 999) {
                    errorCount++
                    setError(
                        sysBPFields[i].parent.parent as TextInputLayout,
                        ResourcesHelper.getString(this, R.string.verify_scan_replace_value)
                    )
                } else if (currentValueLength !in 2..3) {
                    setError(
                        sysBPFields[i].parent.parent as TextInputLayout,
                        ResourcesHelper.getString(this, R.string.verify_scan_invalid_value)
                    )
                } else if (sysBPFields[i].text.toString().toInt() !in 80..230) {
                    setError(
                        sysBPFields[i].parent.parent as TextInputLayout,
                        ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
                    )
                }
            }

            for (i in 0 until diaBPListHistory.size) {
                val currentValueLength = diaBPFields[i].text.toString().length

                if (diaBPFields[i].text.isNullOrEmpty()) {
                    setError(
                        diaBPFields[i].parent.parent as TextInputLayout,
                        ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
                    )
                } else if (!diaBPFields[i].text!!.isDigitsOnly()) {
                    setError(
                        diaBPFields[i].parent.parent as TextInputLayout,
                        ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
                    )
                } else if (diaBPFields[i].text!!.toString().toInt() == 999) {
                    errorCount++
                    setError(
                        diaBPFields[i].parent.parent as TextInputLayout,
                        ResourcesHelper.getString(this, R.string.verify_scan_replace_value)
                    )
                } else if (currentValueLength !in 2..3) {
                    setError(
                        diaBPFields[i].parent.parent as TextInputLayout,
                        ResourcesHelper.getString(this, R.string.verify_scan_invalid_value)
                    )
                } else if (diaBPFields[i].text.toString().toInt() !in 45..135) {
                    setError(
                        diaBPFields[i].parent.parent as TextInputLayout,
                        ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
                    )
                }
            }
        }

        if (errorCount > 6) {
            MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_error)
                .setTitle(ResourcesHelper.getString(this, R.string.verify_scan_erroneous_header))
                .setMessage(ResourcesHelper.getString(this, R.string.verify_scan_erroneous_body))
                .setNegativeButton(
                    ResourcesHelper.getString(
                        this,
                        R.string.no_dialog
                    )
                ) { dialog, _ -> dialog.dismiss() }
                .setPositiveButton(ResourcesHelper.getString(this, R.string.yes_dialog)) { _, _ ->
                    val scanIntent = Intent(this, ScanActivity::class.java)

                    binding.verifyHomeSys?.let {
                        scanIntent.putExtra(
                            "homeSysBPTarget",
                            binding.verifyHomeSys.text.toString()
                        )
                    }
                    binding.verifyHomeDia?.let {
                        scanIntent.putExtra(
                            "homeDiaBPTarget",
                            binding.verifyHomeDia.text.toString()
                        )
                    }
                    binding.verifyClinicTargetSys?.let {
                        scanIntent.putExtra(
                            "clinicSysBPTarget",
                            binding.verifyClinicTargetSys.text.toString()
                        )
                    }
                    binding.verifyClinicTargetDia?.let {
                        scanIntent.putExtra(
                            "clinicDiaBPTarget",
                            binding.verifyClinicTargetDia.text.toString()
                        )
                    }
                    binding.verifyClinicSys?.let {
                        scanIntent.putExtra(
                            "clinicSysBP",
                            binding.verifyClinicSys.text.toString()
                        )
                    }
                    binding.verifyClinicDia?.let {
                        scanIntent.putExtra(
                            "clinicDiaBP",
                            binding.verifyClinicDia.text.toString()
                        )
                    }

                    if (!sysBPListHistory.isNullOrEmpty()) {
                        scanIntent.putStringArrayListExtra(
                            "sysBPListHistory",
                            ArrayList(sysBPListHistory)
                        )
                    }
                    if (!diaBPListHistory.isNullOrEmpty()) {
                        scanIntent.putStringArrayListExtra(
                            "diaBPListHistory",
                            ArrayList(diaBPListHistory)
                        )
                    }

                    startActivity(scanIntent)
                    finish()
                }
                .show()
        }
    }

    private fun setError(inputLayout: TextInputLayout, message: String?) {
        if (message != null) {
            inputLayout.error = message
            inputLayout.isErrorEnabled = true
        } else {
            inputLayout.error = null
            inputLayout.isErrorEnabled = false
        }
    }

    private fun validateFields(): Boolean {
        var valid = true

        if (binding.verifyClinicSys.text.isNullOrEmpty()) {
            valid = false
            setError(
                binding.verifyClinicSys.parent.parent as TextInputLayout,
                ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
            )
        } else if (!binding.verifyClinicSys.text!!.isDigitsOnly()) {
            valid = false
            setError(
                binding.verifyClinicSys.parent.parent as TextInputLayout,
                ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
            )
        } else if (binding.verifyClinicSys.text.toString()
                .toInt() > 209 || binding.verifyClinicSys.text.toString().toInt() < 91
        ) {
            valid = false
        }

        if (binding.verifyClinicDia.text.isNullOrEmpty()) {
            valid = false
            setError(
                binding.verifyClinicDia.parent.parent as TextInputLayout,
                ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
            )
        } else if (!binding.verifyClinicDia.text!!.isDigitsOnly()) {
            valid = false
            setError(
                binding.verifyClinicDia.parent.parent as TextInputLayout,
                ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
            )
        } else if (binding.verifyClinicDia.text.toString()
                .toInt() > 119 || binding.verifyClinicDia.text.toString().toInt() < 61
        ) {
            valid = false
        }

        if (!sevenDay) {

            for (sysField in sysBPFields) {
                if (sysField.text.isNullOrEmpty()) {
                    valid = false
                    setError(
                        sysField.parent.parent as TextInputLayout,
                        ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
                    )
                } else if (sysField.text!!.toString().toInt() == 999) {
                    valid = false
                    setError(
                        sysField.parent.parent as TextInputLayout,
                        ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
                    )
                } else if (!sysField.text!!.isDigitsOnly()) {
                    valid = false
                    setError(
                        sysField.parent.parent as TextInputLayout,
                        ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
                    )
                } else if (sysField.text!!.length !in 2..3) {
                    valid = false
                    setError(
                        sysField.parent.parent as TextInputLayout,
                        ResourcesHelper.getString(this, R.string.verify_scan_invalid_value)
                    )
                } else if (sysField.text.toString().toInt() !in 50..230) {
                    setError(
                        sysField.parent.parent as TextInputLayout,
                        ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
                    )
                }
            }

            for (diaField in diaBPFields) {
                if (diaField.text.isNullOrEmpty()) {
                    valid = false
                    setError(
                        diaField.parent.parent as TextInputLayout,
                        ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
                    )
                } else if (diaField.text!!.toString().toInt() == 999) {
                    valid = false
                    setError(
                        diaField.parent.parent as TextInputLayout,
                        ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
                    )
                } else if (!diaField.text!!.isDigitsOnly()) {
                    valid = false
                    setError(
                        diaField.parent.parent as TextInputLayout,
                        ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
                    )
                } else if (diaField.text!!.length !in 2..3) {
                    valid = false
                    setError(
                        diaField.parent.parent as TextInputLayout,
                        ResourcesHelper.getString(this, R.string.verify_scan_invalid_value)
                    )
                } else if (diaField.text.toString().toInt() !in 35..135) {
                    setError(
                        diaField.parent.parent as TextInputLayout,
                        ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
                    )
                }
            }
        } else {
            for (sysField in sysBPFields) {
                val sysText = sysField.text!!.toString()
                println("Checking sysField: $sysText")
                if (sysText.isEmpty()) {
                    println("sysText is empty")
                    continue
                } else if (!sysText.isDigitsOnly()) {
                    println("sysText is not digits only")
                    valid = false
                    setError(
                        sysField.parent.parent as TextInputLayout,
                        ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
                    )
                } else if (sysText.toInt() == 999) {
                    println("sysText value is 999")
                    valid = false
                    setError(
                        sysField.parent.parent as TextInputLayout,
                        ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
                    )
                }
            }

            for (diaField in diaBPFields) {
                var diaText = diaField.text!!.toString()
                println("Checking diaField: $diaText")
                if (diaText.isEmpty() || diaText == "nul") {
                    println("diaText is empty")
                    continue
                } else if (!diaText.isDigitsOnly()) {
                    println("diaText is not digits only")
                    valid = false
                    setError(
                        diaField.parent.parent as TextInputLayout,
                        ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
                    )
                } else if (diaText.toInt() == 999) {
                    println("diaText value is 999")
                    valid = false
                    setError(
                        diaField.parent.parent as TextInputLayout,
                        ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
                    )
                }
            }
        }


        return valid
    }

    private fun getBPTarget() {
        homeSysBPTarget = if (binding.verifyHomeSys.text.toString().isEmpty()) {
            0
        } else {
            binding.verifyHomeSys.text.toString().toInt()
        }

        homeDiaBPTarget = if (binding.verifyHomeDia.text.toString().isEmpty()) {
            0
        } else {
            binding.verifyHomeDia.text.toString().toInt()
        }

        clinicSysBPTarget = if (binding.verifyClinicTargetSys.text.toString().isEmpty()) {
            0
        } else {
            binding.verifyClinicTargetSys.text.toString().toInt()
        }

        clinicDiaBPTarget = if (binding.verifyClinicTargetDia.text.toString().isEmpty()) {
            0
        } else {
            binding.verifyClinicTargetDia.text.toString().toInt()
        }
    }

    private fun calcAvgBP() {
        totalSysBP = 0
        totalDiaBP = 0

        // Convert the values from sysBPFields and diaBPFields into lists
        val sysBPValues = sysBPFields.map { it.text.toString() }
        val diaBPValues = diaBPFields.map { it.text.toString() }

        // Print the lists
        println("sysBPFields values: $sysBPValues")
        println("diaBPFields values: $diaBPValues")

        for (field in sysBPFields) {
            totalSysBP += field.text.toString().toInt()
        }

        for (field in diaBPFields) {
            totalDiaBP += field.text.toString().toInt()
        }


        avgSysBP = (totalSysBP.toFloat() / sysBPFields.size).roundToInt()
        avgDiaBP = (totalDiaBP.toFloat() / diaBPFields.size).roundToInt()
    }

    private fun calcSevenDayAvgBP() {
        val dayReadings = mutableListOf<List<Pair<String, String>>>()
        var currentDayReadings = mutableListOf<Pair<String, String>>()

        sysBPList = sysBPList.subList(0, 28).toMutableList()
        diaBPList = diaBPList.subList(0, 28).toMutableList()

        for (i in sysBPList.indices) {
            if (sysBPList[i].isNotBlank() && sysBPList[i] != "-1" && diaBPList[i].isNotBlank() && diaBPList[i] != "-1") {
                currentDayReadings.add(Pair(sysBPList[i], diaBPList[i]))
            }

            // Each day should have 4 readings (2 morning + 2 evening)
            if (currentDayReadings.size == 4) {
                dayReadings.add(currentDayReadings.toList())
                currentDayReadings.clear()
            } else if ((i + 1) % 4 == 0) {
                // If it's the end of the day (4 readings) but incomplete
                currentDayReadings.clear()
            }
        }

        println("Grouped Readings: $dayReadings")

        val filteredSysBPList = mutableListOf<String>()
        val filteredDiaBPList = mutableListOf<String>()

        for (day in dayReadings) {
            val (morningSysBP1, morningDiaBP1) = day[0]
            val (morningSysBP2, morningDiaBP2) = day[1]
            val (eveningSysBP1, eveningDiaBP1) = day[2]
            val (eveningSysBP2, eveningDiaBP2) = day[3]

            println("Processing Day Readings:")
            println("Morning Readings: $morningSysBP1, $morningDiaBP1; $morningSysBP2, $morningDiaBP2")
            println("Evening Readings: $eveningSysBP1, $eveningDiaBP1; $eveningSysBP2, $eveningDiaBP2")

            val chosenMorningSysBP =
                if (morningSysBP1.toIntOrNull() ?: 0 >= targetSysBP.toInt() || morningDiaBP1.toIntOrNull() ?: 0 >= targetDiaBP.toInt()) {
                    morningSysBP2
                } else {
                    morningSysBP1
                }
            val chosenMorningDiaBP =
                if (morningSysBP1.toIntOrNull() ?: 0 >= targetSysBP.toInt() || morningDiaBP1.toIntOrNull() ?: 0 >= targetDiaBP.toInt()) {
                    morningDiaBP2
                } else {
                    morningDiaBP1
                }

            val chosenEveningSysBP =
                if (eveningSysBP1.toIntOrNull() ?: 0 >= targetSysBP.toInt() || eveningDiaBP1.toIntOrNull() ?: 0 >= targetDiaBP.toInt()) {
                    eveningSysBP2
                } else {
                    eveningSysBP1
                }
            val chosenEveningDiaBP =
                if (eveningSysBP1.toIntOrNull() ?: 0 >= targetSysBP.toInt() || eveningDiaBP1.toIntOrNull() ?: 0 >= targetDiaBP.toInt()) {
                    eveningDiaBP2
                } else {
                    eveningDiaBP1
                }

            filteredSysBPList.add(chosenMorningSysBP)
            filteredDiaBPList.add(chosenMorningDiaBP)
            filteredSysBPList.add(chosenEveningSysBP)
            filteredDiaBPList.add(chosenEveningDiaBP)

            println("Chosen Morning Readings: $chosenMorningSysBP, $chosenMorningDiaBP")
            println("Chosen Evening Readings: $chosenEveningSysBP, $chosenEveningDiaBP")
        }
        println("BEFORE")
        println("Final SysBPList: $filteredSysBPList")
        println("Final DiaBPList: $filteredDiaBPList")

        val finalSysBPList = filteredSysBPList.drop(2).takeLast(6).toMutableList()
        val finalDiaBPList = filteredDiaBPList.drop(2).takeLast(6).toMutableList()

        println("AFTER")
        println("Final SysBPList: $finalSysBPList")
        println("Final DiaBPList: $finalDiaBPList")


        sysBPList = finalSysBPList
        diaBPList = finalDiaBPList

        totalSysBP = 0
        totalDiaBP = 0

        for (field in finalSysBPList) {
            totalSysBP += field.toInt()
        }

        for (field in finalDiaBPList) {
            totalDiaBP += field.toInt()
        }

        avgSysBP = (totalSysBP.toFloat() / finalSysBPList.size).roundToInt()
        avgDiaBP = (totalDiaBP.toFloat() / finalDiaBPList.size).roundToInt()
    }

    private fun sevenDayCheck() {
        // Ensure sysBPList and diaBPList have at least 28 elements
        ensureListSize(sysBPList, 28)
        ensureListSize(diaBPList, 28)

        var recordIndex = 0

        for (day in 1..7) {
            // Morning
            addRow(
                sysBPList.getOrElse(recordIndex) { "-1" },
                diaBPList.getOrElse(recordIndex) { "-1" },
                true,
                day,
                0,
                showHeader = true
            ) // Morning BP1 with header

            addRow(
                sysBPList.getOrElse(recordIndex + 1) { "-1" },
                diaBPList.getOrElse(recordIndex + 1) { "-1" },
                true,
                day,
                0
            ) // Morning BP2

            // Evening
            addRow(
                sysBPList.getOrElse(recordIndex + 2) { "-1" },
                diaBPList.getOrElse(recordIndex + 2) { "-1" },
                true,
                day,
                1,
                showHeader = true
            ) // Evening BP1 with header

            addRow(
                sysBPList.getOrElse(recordIndex + 3) { "-1" },
                diaBPList.getOrElse(recordIndex + 3) { "-1" },
                true,
                day,
                1
            ) // Evening BP2

            recordIndex += 4
        }
    }

    private fun ensureListSize(list: MutableList<String>, targetSize: Int) {
        while (list.size < targetSize) {
            list.add("-1")
        }
    }

    @SuppressLint("SetTextI18n")
    private fun addRow(
        sysBP: String?,
        diaBP: String?,
        isSevenDayCheck: Boolean = false,
        day: Int = -1,
        time: Int = -1,
        showHeader: Boolean = false
    ) {
        println("Adding row: sysBP=$sysBP, diaBP=$diaBP, isSevenDayCheck=$isSevenDayCheck, day=$day, time=$time, showHeader=$showHeader")

        val rowBPRecordLayout = layoutInflater.inflate(R.layout.row_bp_record, null, false)

        val sysBPTIET = rowBPRecordLayout.findViewById<View>(R.id.sysBPTIET) as TextInputEditText
        val diaBPTIET = rowBPRecordLayout.findViewById<View>(R.id.diaBPTIET) as TextInputEditText
        val dayTV = rowBPRecordLayout.findViewById<View>(R.id.headerTextView) as TextView
        val headerRowContainer =
            rowBPRecordLayout.findViewById<View>(R.id.headerRowContainer) as LinearLayout
        val bpRowContainer =
            rowBPRecordLayout.findViewById<View>(R.id.bpRowContainer) as LinearLayout
        val addOneRowBtn = rowBPRecordLayout.findViewById<View>(R.id.addOneRowBtn) as Button

        if (isSevenDayCheck) {
            if ((sysBP == null || sysBP == "-1") && (diaBP == null || diaBP == "-1")) {
                bpRowContainer.visibility = View.GONE
            } else {
                bpRowContainer.visibility = View.VISIBLE
                addOneRowBtn.visibility = View.GONE
                sysBPTIET.setText(sysBP)
                diaBPTIET.setText(diaBP)
            }
            if (day != -1 && time != -1 && showHeader) {
                headerRowContainer.visibility = View.VISIBLE
                dayTV.text = if (time == 0) "Day $day - Morning" else "Day $day - Evening"
            } else {
                dayTV.visibility = View.GONE
            }
        } else {
            bpRowContainer.visibility = View.VISIBLE
            sysBPTIET.setText(sysBP)
            diaBPTIET.setText(diaBP)
            if (sysBP == null && diaBP == null) {
                sysBPList.add("")
                diaBPList.add("")
            }

        }

        // Add TextWatchers to update the lists
        sysBPTIET.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                saveStateForUndo()
                val index = sysBPFields.indexOf(sysBPTIET)
                if (index != -1) {
                    sysBPList[index] = s.toString()
                }
            }
        })

        diaBPTIET.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                saveStateForUndo()
                val index = diaBPFields.indexOf(diaBPTIET)
                if (index != -1) {
                    diaBPList[index] = s.toString()
                }
            }
        })

        // Add to fields lists
        sysBPFields.add(sysBPTIET)
        diaBPFields.add(diaBPTIET)

        val swapValuesIV = rowBPRecordLayout.findViewById<View>(R.id.swapValuesIV) as ImageView
        swapValuesIV.setOnClickListener {
            saveStateForUndo()
            val tempValue = sysBPTIET.text.toString()
            sysBPTIET.setText(diaBPTIET.text.toString())
            diaBPTIET.setText(tempValue)
            val toast = Toast.makeText(this, "Values swapped", Toast.LENGTH_SHORT)
            toast.show()
        }

        val removeRowIV = rowBPRecordLayout.findViewById<View>(R.id.removeRowIV) as ImageView
        removeRowIV.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_remove_circle)
                .setTitle(ResourcesHelper.getString(this, R.string.verify_scan_remove_row_header))
                .setMessage(ResourcesHelper.getString(this, R.string.verify_scan_remove_row_body))
                .setNegativeButton(
                    ResourcesHelper.getString(
                        this,
                        R.string.cancel_dialog
                    )
                ) { dialog, _ -> dialog.dismiss() }
                .setPositiveButton(
                    ResourcesHelper.getString(
                        this,
                        R.string.ok_dialog
                    )
                ) { dialog, _ ->
                    saveStateForUndo()

                    // Print lists before removal
                    println("sysBPList before removal: $sysBPList")
                    println("diaBPList before removal: $diaBPList")

                    val diaIndex = diaBPFields.indexOf(diaBPTIET)
                    if (diaIndex != -1) {
                        diaBPList.removeAt(diaIndex)
                    }
                    val sysIndex = sysBPFields.indexOf(sysBPTIET)
                    if (sysIndex != -1) {
                        sysBPList.removeAt(sysIndex)
                    }
                    sysBPFields.remove(sysBPTIET)
                    diaBPFields.remove(diaBPTIET)

                    if (sevenDay) {
                        binding.rowBPRecordLL.removeAllViews()
                        sysBPFields.clear()
                        diaBPFields.clear()
                        sevenDayCheck()
                    } else {
                        binding.rowBPRecordLL.removeView(rowBPRecordLayout)
                    }

                    // Print lists after removal
                    println("sysBPList after removal: $sysBPList")
                    println("diaBPList after removal: $diaBPList")

                    dialog.dismiss()
                    val toast = Toast.makeText(this, "Row removed", Toast.LENGTH_SHORT)
                    toast.show()
                }
                .show()
        }


        addOneRowBtn.setOnClickListener {
            saveStateForUndo()

            val currentRowIndex = binding.rowBPRecordLL.indexOfChild(rowBPRecordLayout)
            val newRow = layoutInflater.inflate(R.layout.row_bp_record, null, false)
            binding.rowBPRecordLL.addView(newRow, currentRowIndex)
            sysBPList[currentRowIndex] = ""
            diaBPList[currentRowIndex] = ""

            binding.rowBPRecordLL.removeAllViews()
            sysBPFields.clear()
            diaBPFields.clear()
            refreshViews()

            val toast = Toast.makeText(this, "Row added", Toast.LENGTH_SHORT)
            toast.show()
        }

        val addRowBtn = rowBPRecordLayout.findViewById<View>(R.id.addRowIV) as ImageView
        addRowBtn.setOnClickListener {
            saveStateForUndo()
            if (!sevenDay) {
                addRow(null, null)
            } else {
                val currentRowIndex = binding.rowBPRecordLL.indexOfChild(rowBPRecordLayout)
                val nextRowIndex = currentRowIndex + 1

                val validSysBPCount = sysBPList.count { it != "-1" }
                val validDiaBPCount = diaBPList.count { it != "-1" }

                if (validSysBPCount >= 28 && validDiaBPCount >= 28) {
                    val toast = Toast.makeText(this, "Unable to add more rows", Toast.LENGTH_SHORT)
                    toast.show()
                    return@setOnClickListener
                }

                println("addRowIV clicked. Current Row Index: $currentRowIndex")
                println("addRowIV SysBPList before modification: $sysBPList\n")

                // Check if the next row index is within bounds
                if (nextRowIndex < binding.rowBPRecordLL.childCount) {
                    // Get the next row from the LinearLayout
                    val nextRowLayout = binding.rowBPRecordLL.getChildAt(nextRowIndex)

                    // Find the TextInputEditText elements in the next row
                    val nextSysBPTIET =
                        nextRowLayout.findViewById<TextInputEditText>(R.id.sysBPTIET)
                    val nextDiaBPTIET =
                        nextRowLayout.findViewById<TextInputEditText>(R.id.diaBPTIET)

                    if (nextSysBPTIET.text.toString() != "-1" && nextDiaBPTIET.text.toString() != "-1") {
                        sysBPList.add(currentRowIndex, "")
                        diaBPList.add(currentRowIndex, "")
                        if (sysBPList.size > 28) {
                            removeFirstOccurrenceFromEnd(sysBPList, "-1")
                        }
                        if (diaBPList.size > 28) {
                            removeFirstOccurrenceFromEnd(diaBPList, "-1")
                        }
                    } else {
                        sysBPList[currentRowIndex + 1] = ""
                        diaBPList[currentRowIndex + 1] = ""
                    }
                }

                // Refresh the view
                binding.rowBPRecordLL.removeAllViews()
                sysBPFields.clear()
                diaBPFields.clear()
                refreshViews()

                val toast = Toast.makeText(this, "Row added", Toast.LENGTH_SHORT)
                toast.show()
            }
        }

        val sysLeftIV = rowBPRecordLayout.findViewById<View>(R.id.sysLeftIV) as ImageView
        sysLeftIV.setOnClickListener {
            saveStateForUndo()
            val currentRowIndex = binding.rowBPRecordLL.indexOfChild(rowBPRecordLayout)

            // Print the current index of the row
            println("sysLeftIV clicked. Current Row Index: $currentRowIndex")

            println("sysLeftIV SysBPList before modification: $sysBPList\n")
            println("sysLeftIV DiaBPList before modification: $diaBPList\n")

            // Insert an empty string at the systolic index
            sysBPList.add(currentRowIndex, "")

            // Print lists after adding an empty string
            println("sysLeftIV SysBPList after adding empty string: $sysBPList\n")

            if (currentRowIndex != -1 && currentRowIndex < sysBPList.size && currentRowIndex < diaBPList.size) {
                // Extract parts to swap
                val systolicPartToSwap =
                    sysBPList.subList(currentRowIndex + 1, sysBPList.size).toMutableList()
                val diastolicPartToSwap =
                    diaBPList.subList(currentRowIndex, diaBPList.size).toMutableList()

                // Print parts to swap
                println("sysLeftIV Systolic part to swap: $systolicPartToSwap")
                println("sysLeftIV Diastolic part to swap: $diastolicPartToSwap")

                // Update lists with swapped elements
                sysBPList.subList(currentRowIndex + 1, sysBPList.size).clear()
                diaBPList.subList(currentRowIndex, diaBPList.size).clear()
                println("cleared sysBPList $sysBPList")
                println("cleared diabplist $diaBPList")


                sysBPList.addAll(currentRowIndex + 1, diastolicPartToSwap)
                diaBPList.addAll(currentRowIndex, systolicPartToSwap)

                // Print lists after swapping
                println("sysLeftIV SysBPList after swapping: $sysBPList\n")
                println("sysLeftIV DiaBPList after swapping: $diaBPList\n")
            } else {
                println("sysLeftIV Invalid index or lists size mismatch. No swapping performed.")
            }

            while (diaBPList.size != sysBPList.size) {
                if (diaBPList.size > sysBPList.size) {
                    sysBPList.add("")
                } else {
                    diaBPList.add("")
                }
            }
            // Clear the views and fields, then refresh
            binding.rowBPRecordLL.removeAllViews()
            sysBPFields.clear()
            diaBPFields.clear()
            refreshViews()

            // Show toast message
            val toast = Toast.makeText(this, "Empty Systolic Added", Toast.LENGTH_SHORT)
            toast.show()
        }


        val diaRightIV = rowBPRecordLayout.findViewById<View>(R.id.diaRightIV) as ImageView
        diaRightIV.setOnClickListener {
            saveStateForUndo()
            val currentRowIndex = binding.rowBPRecordLL.indexOfChild(rowBPRecordLayout)
            println("diaRightIV clicked. Current Row Index: $currentRowIndex")
            println("diaLeftIV SysBPList: $sysBPList\n")
            println("diaLeftIV DiaBPList: $diaBPList\n")

            diaBPList.add(currentRowIndex, "")

            if (currentRowIndex != -1 && currentRowIndex < diaBPList.size && currentRowIndex < sysBPList.size) {
                val systolicPartToSwap =
                    sysBPList.subList(currentRowIndex + 1, sysBPList.size).toMutableList()
                val diastolicPartToSwap =
                    diaBPList.subList(currentRowIndex + 1, diaBPList.size).toMutableList()

                // Print parts to swap
                println("diaLeftIV Systolic part to swap: $systolicPartToSwap")
                println("diaLeftIV Diastolic part to swap: $diastolicPartToSwap")

                sysBPList.subList(currentRowIndex + 1, sysBPList.size).clear()
                diaBPList.subList(currentRowIndex + 1, diaBPList.size).clear()
                println("cleared sysBPList $sysBPList")
                println("cleared diabplist $diaBPList")

                sysBPList.addAll(currentRowIndex + 1, diastolicPartToSwap)
                diaBPList.addAll(currentRowIndex + 1, systolicPartToSwap)
            }
            println("diaBPsize: ${diaBPList.size}")
            println("sysBPsize: ${sysBPList.size}")

            while (diaBPList.size != sysBPList.size) {
                if (diaBPList.size > sysBPList.size) {
                    sysBPList.add("")
                } else {
                    diaBPList.add("")
                }
            }

            println("diaLeftIV SysBPList after swapping: $sysBPList\n")
            println("diaLeftIV DiaBPList after swapping: $diaBPList\n")

            binding.rowBPRecordLL.removeAllViews()
            sysBPFields.clear()
            diaBPFields.clear()
            refreshViews()

            val toast = Toast.makeText(this, "Empty Diastolic Added", Toast.LENGTH_SHORT)
            toast.show()
        }

        binding.rowBPRecordLL.addView(rowBPRecordLayout)
    }

    private fun addDivider() {
        val dividerOldNewRecordLayout =
            layoutInflater.inflate(R.layout.divider_old_new_record, null, false)
        binding.rowBPRecordLL.addView(dividerOldNewRecordLayout)
    }

    private fun removeFirstOccurrenceFromEnd(list: MutableList<String>, value: String) {
        for (i in list.indices.reversed()) {
            if (list[i] == value) {
                list.removeAt(i)
                break
            }
        }
    }
}


class ModalBottomSheet(
    private val activity: VerifyScanActivity,
    private val isSevenDay: Boolean = false
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.bottom_sheet_verify_scan, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val continueBS = view.findViewById<LinearLayout>(R.id.continueBS)
        val rescanBS = view.findViewById<LinearLayout>(R.id.rescanBS)
        val undoBS = view.findViewById<LinearLayout>(R.id.undoBS)

        if (isSevenDay) {
            continueBS.visibility = View.GONE
            rescanBS.visibility = View.GONE

            undoBS.setOnClickListener {
                activity.undo()
                dismiss()
            }

        } else {
            continueBS.setOnClickListener {
                activity.continueScan()
                dismiss()
            }

            rescanBS.setOnClickListener {
                activity.rescanRecords()
                dismiss()
            }

            undoBS.setOnClickListener {
                activity.undo()
                dismiss()
            }
        }

        val discardBS = view.findViewById<LinearLayout>(R.id.discardBS)
        discardBS.setOnClickListener {
            activity.discardProgress()
            dismiss()
        }
    }

    companion object {
        const val TAG = "ModalBottomSheet"
    }
}
