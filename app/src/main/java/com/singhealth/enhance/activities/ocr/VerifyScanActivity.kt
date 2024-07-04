package com.singhealth.enhance.activities.ocr

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.isDigitsOnly
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.singhealth.enhance.R
import com.singhealth.enhance.activities.MainActivity
import com.singhealth.enhance.activities.result.RecommendationActivity
import com.singhealth.enhance.databinding.ActivityVerifyScanBinding
import com.singhealth.enhance.security.AESEncryption
import com.singhealth.enhance.security.SecureSharedPreferences
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class VerifyScanActivity : AppCompatActivity() {
    private lateinit var binding: ActivityVerifyScanBinding

    private lateinit var patientID: String

    private var homeSysBPTarget = 0
    private var homeDiaBPTarget = 0
    private var clinicSysBPTarget = 0
    private var clinicDiaBPTarget = 0

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

        val patientSharedPreferences = SecureSharedPreferences.getSharedPreferences(applicationContext)
        if (patientSharedPreferences.getString("patientID", null).isNullOrEmpty()) {
            Toast.makeText(this, "An error occurred. Please try again.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } else {
            patientID = patientSharedPreferences.getString("patientID", null).toString()
            binding.patientIDTV.text = AESEncryption().decrypt(patientID)
        }

        val scanBundle = intent.extras

        sysBPList = scanBundle?.getStringArrayList("sysBPList")?.toMutableList()!!
        diaBPList = scanBundle?.getStringArrayList("diaBPList")?.toMutableList()!!

        // Retrieve previous data
        if (scanBundle != null) {
            if (scanBundle.containsKey("homeSysBPTarget")) {
                binding.homeSysBPTargetTIET.setText(scanBundle.getString("homeSysBPTarget"))
            }
            if (scanBundle.containsKey("homeDiaBPTarget")) {
                binding.homeDiaBPTargetTIET.setText(scanBundle.getString("homeDiaBPTarget"))
            }
            if (scanBundle.containsKey("clinicSysBPTarget")) {
                binding.clinicSysBPTargetTIET.setText(scanBundle.getString("clinicSysBPTarget"))
            }
            if (scanBundle.containsKey("clinicDiaBPTarget")) {
                binding.clinicDiaBPTargetTIET.setText(scanBundle.getString("clinicDiaBPTarget"))
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
            }
        }

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
        println("sysBPList: $sysBPList")
        println("diaBPList: $diaBPList")

        var removal = ((sysBPList).count()/2)-1
        sysBPList = sysBPList.drop(removal).toMutableList()
        diaBPList = diaBPList.drop(removal).toMutableList()
        println("sysBPList: $sysBPList")
        println("diaBPList: $diaBPList")

        // Calculate total rows (inclusive of currently detected rows)
        var totalRows = maxOf(sysBPList.size, diaBPList.size)
        if (sysBPListHistory.isNotEmpty() && diaBPListHistory.isNotEmpty()) {
            totalRows += maxOf(sysBPListHistory.size, diaBPListHistory.size)
        }

        // Calculate currently detected rows
        var currentRows = maxOf(sysBPList.size, diaBPList.size)

        // Display toast for current number of records detected
        if (currentRows > 1 && totalRows <= 1) {
            MaterialAlertDialogBuilder(this)
                .setMessage("$currentRows records have been detected.\n\n(total record: $totalRows)")
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
        } else if (currentRows > 1 && totalRows > 1) {
            MaterialAlertDialogBuilder(this)
                .setMessage("$currentRows records have been detected.\n\n(total records: $totalRows)")
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
        } else if (currentRows == 1 && totalRows <= 1) {
            MaterialAlertDialogBuilder(this)
                .setMessage("$currentRows record has been detected.\n\n(total record: $totalRows)")
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
        } else if (currentRows == 1 && totalRows > 1) {
            MaterialAlertDialogBuilder(this)
                .setMessage("$currentRows record has been detected.\n\n(total records: $totalRows)")
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
        } else {
            MaterialAlertDialogBuilder(this)
                .setMessage("No records detected. Rescan or manually insert them by adding a new row.")
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
        }

        // Prompt user if total records captured is less than 12
        if (totalRows < 12) {
            MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_error)
                .setTitle("Inadequate number of readings")
                .setMessage("Do you want to proceed with the analysis?")
                .setNegativeButton("No") { _, _ ->
                    startActivity(Intent(this, ScanActivity::class.java))
                    finish()
                }
                .setPositiveButton("Yes") { dialog, _ -> dialog.dismiss() }
                .show()
        }

        // Check BP records for errors
        postScanValidation()

        // Add new row
        binding.addRowBtn.setOnClickListener { addRow(null, null) }

        // More options button
        binding.moreOptionsBtn.setOnClickListener {
            val modalBottomSheet = ModalBottomSheet(this)
            modalBottomSheet.show(supportFragmentManager, ModalBottomSheet.TAG)
        }

        // Calculate and save average BP, home BP and clinic BP targets, then display outcome and recommendation (separate activity)
        binding.calculateAvgBPBtn.setOnClickListener {
            if (validateFields()) {
                getBPTarget()
                calcAvgBP()

                // TODO: Save record into database
                val visit = hashMapOf(
                    "date" to LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")),
                    "homeSysBPTarget" to homeSysBPTarget,
                    "homeDiaBPTarget" to homeDiaBPTarget,
                    "clinicSysBPTarget" to clinicSysBPTarget,
                    "clinicDiaBPTarget" to clinicDiaBPTarget,
                    "averageSysBP" to avgSysBP,
                    "averageDiaBP" to avgDiaBP
                )

                db.collection("patients").document(patientID).collection("visits").add(visit)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Calculation saved successfully.", Toast.LENGTH_SHORT)
                            .show()

                        val bundle = Bundle()

                        bundle.putInt("avgSysBP", avgSysBP)
                        bundle.putInt("avgDiaBP", avgDiaBP)
                        bundle.putString("Source", "Scan")

                        val recommendationIntent = Intent(this, RecommendationActivity::class.java)

                        recommendationIntent.putExtras(bundle)

                        startActivity(recommendationIntent)
                    }
                    .addOnFailureListener { e ->
                        MaterialAlertDialogBuilder(this)
                            .setIcon(R.drawable.ic_error)
                            .setTitle("Error saving record")
                            .setMessage("The app wasn't able to save the current record.\n\nIf issue persists, contact IT support with the following error code: $e")
                            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                            .show()
                    }
            } else {
                MaterialAlertDialogBuilder(this)
                    .setIcon(R.drawable.ic_error)
                    .setTitle("Error(s) detected")
                    .setMessage("Correct all errors before continuing.")
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            }
        }
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

        binding.homeSysBPTargetTIET?.let {
            scanIntent.putExtra("homeSysBPTarget", binding.homeSysBPTargetTIET.text.toString())
        }
        binding.homeDiaBPTargetTIET?.let {
            scanIntent.putExtra("homeDiaBPTarget", binding.homeDiaBPTargetTIET.text.toString())
        }
        binding.clinicSysBPTargetTIET?.let {
            scanIntent.putExtra("clinicSysBPTarget", binding.clinicSysBPTargetTIET.text.toString())
        }
        binding.clinicDiaBPTargetTIET?.let {
            scanIntent.putExtra("clinicDiaBPTarget", binding.clinicDiaBPTargetTIET.text.toString())
        }

        if (!sysBPListHistory.isNullOrEmpty()) {
            scanIntent.putStringArrayListExtra("sysBPListHistory", ArrayList(sysBPListHistory))
        }
        if (!diaBPListHistory.isNullOrEmpty()) {
            scanIntent.putStringArrayListExtra("diaBPListHistory", ArrayList(diaBPListHistory))
        }

        startActivity(scanIntent)
        finish()
    }

    fun rescanRecords() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Rescan current records")
            .setMessage("Do you want to rescan the current records?\n\n(note: your previously scanned records will be saved)")
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .setPositiveButton("OK") { _, _ ->
                val scanIntent = Intent(this, ScanActivity::class.java)

                binding.homeSysBPTargetTIET?.let {
                    scanIntent.putExtra(
                        "homeSysBPTarget",
                        binding.homeSysBPTargetTIET.text.toString()
                    )
                }
                binding.homeDiaBPTargetTIET?.let {
                    scanIntent.putExtra(
                        "homeDiaBPTarget",
                        binding.homeDiaBPTargetTIET.text.toString()
                    )
                }
                binding.clinicSysBPTargetTIET?.let {
                    scanIntent.putExtra(
                        "clinicSysBPTarget",
                        binding.clinicSysBPTargetTIET.text.toString()
                    )
                }
                binding.clinicDiaBPTargetTIET?.let {
                    scanIntent.putExtra(
                        "clinicDiaBPTarget",
                        binding.clinicDiaBPTargetTIET.text.toString()
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
        MaterialAlertDialogBuilder(this)
            .setIcon(R.drawable.ic_delete)
            .setTitle("Discard progress")
            .setMessage("Do you want to discard all progress and start over?")
            .setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
            .setPositiveButton("Yes") { _, _ ->
                startActivity(Intent(this, ScanActivity::class.java))
                finish()
            }
            .show()
    }

    private fun postScanValidation() {
        var errorCount = 0

        // With reference from MOH clinical practice guidelines 1/2017 @ https://www.moh.gov.sg/docs/librariesprovider4/guidelines/cpg_hypertension-booklet---nov-2017.pdf
        for (i in sysBPListHistory.size until sysBPList.size + sysBPListHistory.size) {
            val currentValueLength = sysBPFields[i].text.toString().length

            if (sysBPFields[i].text.isNullOrEmpty()) {
                errorCount += 1
                sysBPFields[i].error = "Field is empty."
            } else if (!sysBPFields[i].text!!.isDigitsOnly()) {
                errorCount += 1
                sysBPFields[i].error = "Field must only contain whole number."
            } else if (currentValueLength !in 2..3) {
                errorCount += 1
                sysBPFields[i].error = "Invalid value."
            } else if (sysBPFields[i].text.toString().toInt() !in 90..150) {
                errorCount += 1
                sysBPFields[i].error = "Abnormal value."
            }
        }

        for (i in diaBPListHistory.size until diaBPList.size + diaBPListHistory.size) {
            val currentValueLength = diaBPFields[i].text.toString().length

            if (diaBPFields[i].text.isNullOrEmpty()) {
                errorCount += 1
                diaBPFields[i].error = "Field is empty."
            } else if (!diaBPFields[i].text!!.isDigitsOnly()) {
                errorCount += 1
                diaBPFields[i].error = "Field must only contain whole number."
            } else if (currentValueLength !in 2..3) {
                errorCount += 1
                diaBPFields[i].error = "Invalid value."
            } else if (diaBPFields[i].text.toString().toInt() !in 60..110) {
                errorCount += 1
                diaBPFields[i].error = "Abnormal value."
            }
        }

        // Show error text but doesn't add to error count of current records
        for (i in 0 until sysBPListHistory.size) {
            val currentValueLength = sysBPFields[i].text.toString().length

            if (sysBPFields[i].text.isNullOrEmpty()) {
                sysBPFields[i].error = "Field is empty."
            } else if (!sysBPFields[i].text!!.isDigitsOnly()) {
                sysBPFields[i].error = "Field must only contain whole number."
            } else if (currentValueLength !in 2..3) {
                sysBPFields[i].error = "Invalid value."
            } else if (sysBPFields[i].text.toString().toInt() !in 90..150) {
                sysBPFields[i].error = "Abnormal value."
            }
        }

        for (i in 0 until diaBPListHistory.size) {
            val currentValueLength = diaBPFields[i].text.toString().length

            if (diaBPFields[i].text.isNullOrEmpty()) {
                diaBPFields[i].error = "Field is empty."
            } else if (!diaBPFields[i].text!!.isDigitsOnly()) {
                diaBPFields[i].error = "Field must only contain whole number."
            } else if (currentValueLength !in 2..3) {
                diaBPFields[i].error = "Invalid value."
            } else if (diaBPFields[i].text.toString().toInt() !in 60..110) {
                diaBPFields[i].error = "Abnormal value."
            }
        }

        if (errorCount > 6) {
            MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_error)
                .setTitle("Too many erroneous results")
                .setMessage("Do you want to rescan the current records to achieve a better accuracy?\n\n(note: your previously scanned records will be saved)")
                .setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
                .setPositiveButton("Yes") { _, _ ->
                    val scanIntent = Intent(this, ScanActivity::class.java)

                    binding.homeSysBPTargetTIET?.let {
                        scanIntent.putExtra(
                            "homeSysBPTarget",
                            binding.homeSysBPTargetTIET.text.toString()
                        )
                    }
                    binding.homeDiaBPTargetTIET?.let {
                        scanIntent.putExtra(
                            "homeDiaBPTarget",
                            binding.homeDiaBPTargetTIET.text.toString()
                        )
                    }
                    binding.clinicSysBPTargetTIET?.let {
                        scanIntent.putExtra(
                            "clinicSysBPTarget",
                            binding.clinicSysBPTargetTIET.text.toString()
                        )
                    }
                    binding.clinicDiaBPTargetTIET?.let {
                        scanIntent.putExtra(
                            "clinicDiaBPTarget",
                            binding.clinicDiaBPTargetTIET.text.toString()
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

    private fun validateFields(): Boolean {
        var valid = true

        // TODO: Add back verification for the BP targets once algorithm has it
//        if (binding.homeSysBPTargetTIET.text.isNullOrEmpty()) {
//            valid = false
//            binding.homeSysBPTargetTIET.error = "Field cannot be empty."
//        } else if (!binding.homeSysBPTargetTIET.text!!.isDigitsOnly()) {
//            valid = false
//            binding.homeSysBPTargetTIET.error = "Field can only contain whole number."
//        }
//        if (binding.homeDiaBPTargetTIET.text.isNullOrEmpty()) {
//            valid = false
//            binding.homeDiaBPTargetTIET.error = "Field cannot be empty."
//        } else if (!binding.homeDiaBPTargetTIET.text!!.isDigitsOnly()) {
//            valid = false
//            binding.homeDiaBPTargetTIET.error = "Field can only contain whole number."
//        }
//        if (binding.clinicSysBPTargetTIET.text.isNullOrEmpty()) {
//            valid = false
//            binding.clinicSysBPTargetTIET.error = "Field cannot be empty."
//        } else if (!binding.clinicSysBPTargetTIET.text!!.isDigitsOnly()) {
//            valid = false
//            binding.clinicSysBPTargetTIET.error = "Field can only contain whole number."
//        }
//        if (binding.clinicDiaBPTargetTIET.text.isNullOrEmpty()) {
//            valid = false
//            binding.clinicDiaBPTargetTIET.error = "Field cannot be empty."
//        } else if (!binding.clinicDiaBPTargetTIET.text!!.isDigitsOnly()) {
//            valid = false
//            binding.clinicDiaBPTargetTIET.error = "Field can only contain whole number."
//        }

        for (sysField in sysBPFields) {
            if (sysField.text.isNullOrEmpty()) {
                valid = false
                sysField.error = "Field cannot be empty."
            } else if (!sysField.text!!.isDigitsOnly()) {
                valid = false
                sysField.error = "Field can only contain whole number."
            }
        }

        for (diaField in diaBPFields) {
            if (diaField.text.isNullOrEmpty()) {
                valid = false
                diaField.error = "Field cannot be empty."
            } else if (!diaField.text!!.isDigitsOnly()) {
                valid = false
                diaField.error = "Field can only contain whole number."
            }
        }

        return valid
    }

    private fun getBPTarget() {
        homeSysBPTarget = if (binding.homeSysBPTargetTIET.text.toString().isNullOrEmpty()) {
            0
        } else {
            binding.homeSysBPTargetTIET.text.toString().toInt()
        }

        homeDiaBPTarget = if (binding.homeDiaBPTargetTIET.text.toString().isNullOrEmpty()) {
            0
        } else {
            binding.homeDiaBPTargetTIET.text.toString().toInt()
        }

        clinicSysBPTarget = if (binding.clinicSysBPTargetTIET.text.toString().isNullOrEmpty()) {
            0
        } else {
            binding.clinicSysBPTargetTIET.text.toString().toInt()
        }

        clinicDiaBPTarget = if (binding.clinicDiaBPTargetTIET.text.toString().isNullOrEmpty()) {
            0
        } else {
            binding.clinicDiaBPTargetTIET.text.toString().toInt()
        }
    }

    private fun calcAvgBP() {
        totalSysBP = 0
        totalDiaBP = 0

        for (field in sysBPFields) {
            totalSysBP += field.text.toString().toInt()
        }

        for (field in diaBPFields) {
            totalDiaBP += field.text.toString().toInt()
        }

        avgSysBP = (totalSysBP.toFloat() / sysBPFields.size).roundToInt()
        avgDiaBP = (totalDiaBP.toFloat() / diaBPFields.size).roundToInt()
    }

    // Add new row
    private fun addRow(sysBP: String?, diaBP: String?) {
        val rowBPRecordLayout = layoutInflater.inflate(R.layout.row_bp_record, null, false)

        val sysBPTIET = rowBPRecordLayout.findViewById<View>(R.id.sysBPTIET) as TextInputEditText
        sysBPTIET.setText(sysBP ?: null)
        sysBPFields.add(sysBPTIET)

        val diaBPTIET = rowBPRecordLayout.findViewById<View>(R.id.diaBPTIET) as TextInputEditText
        diaBPTIET.setText(diaBP ?: null)
        diaBPFields.add(diaBPTIET)

        val swapValuesIV = rowBPRecordLayout.findViewById<View>(R.id.swapValuesIV) as ImageView
        swapValuesIV.setOnClickListener {
            val tempValue = sysBPTIET.text.toString()
            sysBPTIET.setText(diaBPTIET.text.toString())
            diaBPTIET.setText(tempValue)
        }

        val removeRowIV = rowBPRecordLayout.findViewById<View>(R.id.removeRowIV) as ImageView
        removeRowIV.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_remove_circle)
                .setTitle("Remove row")
                .setMessage("Do you want to remove the selected row?")
                .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                .setPositiveButton("OK") { dialog, _ ->
                    sysBPFields.remove(sysBPTIET)
                    diaBPFields.remove(diaBPTIET)
                    binding.rowBPRecordLL.removeView(rowBPRecordLayout)
                    dialog.dismiss()
                }
                .show()
        }

        binding.rowBPRecordLL.addView(rowBPRecordLayout)
    }

    // Add divider to separate old and new records
    private fun addDivider() {
        val dividerOldNewRecordLayout =
            layoutInflater.inflate(R.layout.divider_old_new_record, null, false)
        binding.rowBPRecordLL.addView(dividerOldNewRecordLayout)
    }
}

class ModalBottomSheet(private val activity: VerifyScanActivity) : BottomSheetDialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.bottom_sheet_verify_scan, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Continue scanning
        val continueBS = view.findViewById<LinearLayout>(R.id.continueBS)
        continueBS.setOnClickListener {
            activity.continueScan()
            dismiss()
        }

        // Rescan current records
        val rescanBS = view.findViewById<LinearLayout>(R.id.rescanBS)
        rescanBS.setOnClickListener {
            activity.rescanRecords()
            dismiss()
        }

        // Discard progress
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