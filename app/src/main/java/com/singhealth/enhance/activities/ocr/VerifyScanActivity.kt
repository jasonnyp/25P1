package com.singhealth.enhance.activities.ocr

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
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
import com.singhealth.enhance.activities.error.errorDialogBuilder
import com.singhealth.enhance.activities.error.firebaseErrorDialog
import com.singhealth.enhance.activities.error.patientNotFoundInSessionErrorDialog
import com.singhealth.enhance.activities.result.RecommendationActivity
import com.singhealth.enhance.databinding.ActivityVerifyScanBinding
import com.singhealth.enhance.security.AESEncryption
import com.singhealth.enhance.security.SecureSharedPreferences
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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

    private lateinit var patientID: String

    private var homeSysBPTarget = 0
    private var homeDiaBPTarget = 0
    private var clinicSysBPTarget = 0
    private var clinicDiaBPTarget = 0
    private var clinicSysBP = 0
    private var clinicDiaBP = 0

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
            patientNotFoundInSessionErrorDialog(this)
        } else {
            patientID = patientSharedPreferences.getString("patientID", null).toString()
            binding.patientIDTV.text = AESEncryption().decrypt(patientID)
        }

        val scanBundle = intent.extras

        sysBPList = scanBundle?.getStringArrayList("sysBPList")?.toMutableList()!!
        diaBPList = scanBundle?.getStringArrayList("diaBPList")?.toMutableList()!!

        val docRef = db.collection("patients").document(patientID)

        docRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    if (AESEncryption().decrypt(document.getString("targetSys").toString()) == "" || AESEncryption().decrypt(document.getString("targetDia").toString()) == "") {
                        homeSysBPTarget = 0
                        homeDiaBPTarget = 0
                    } else {
                        homeSysBPTarget = AESEncryption().decrypt(document.getString("targetSys").toString()).toInt()
                        homeDiaBPTarget = AESEncryption().decrypt(document.getString("targetDia").toString()).toInt()
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
                    binding.verifyClinicTargetSys.text = clinicSysBPTarget.toString()
                    binding.verifyClinicTargetDia.text = clinicDiaBPTarget.toString()
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
                .setMessage(ResourcesHelper.getString(this, R.string.verify_scan_rows, currentRows, totalRows))
                .setPositiveButton(ResourcesHelper.getString(this, R.string.ok_dialog)) { dialog, _ -> dialog.dismiss() }
                .show()
        } else if (currentRows > 1 && totalRows > 1) {
            MaterialAlertDialogBuilder(this)
                .setMessage(ResourcesHelper.getString(this, R.string.verify_scan_rows, currentRows, totalRows))
                .setPositiveButton(ResourcesHelper.getString(this, R.string.ok_dialog)) { dialog, _ -> dialog.dismiss() }
                .show()
        } else if (currentRows == 1 && totalRows <= 1) {
            MaterialAlertDialogBuilder(this)
                .setMessage(ResourcesHelper.getString(this, R.string.verify_scan_rows, currentRows, totalRows))
                .setPositiveButton(ResourcesHelper.getString(this, R.string.ok_dialog)) { dialog, _ -> dialog.dismiss() }
                .show()
        } else if (currentRows == 1 && totalRows > 1) {
            MaterialAlertDialogBuilder(this)
                .setMessage(ResourcesHelper.getString(this, R.string.verify_scan_rows, currentRows, totalRows))
                .setPositiveButton(ResourcesHelper.getString(this, R.string.ok_dialog)) { dialog, _ -> dialog.dismiss() }
                .show()
        } else {
            MaterialAlertDialogBuilder(this)
                .setMessage(ResourcesHelper.getString(this, R.string.verify_scan_no_records))
                .setPositiveButton(ResourcesHelper.getString(this, R.string.ok_dialog)) { dialog, _ -> dialog.dismiss() }
                .show()
        }

        // Prompt user if total records captured is less than 12
        if (totalRows < 12) {
            errorDialogBuilder(this, getString(R.string.verify_scan_inadequate_reading_header), getString(R.string.verify_scan_inadequate_reading_body), ScanActivity::class.java)
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
                    "clinicDiaBP" to clinicDiaBP
                )

                db.collection("patients").document(patientID).collection("visits").add(visit)
                    .addOnSuccessListener {
                        Toast.makeText(this, ResourcesHelper.getString(this, R.string.verify_scan_calculation_successful), Toast.LENGTH_SHORT)
                            .show()

                        val bundle = Bundle()

                        bundle.putInt("avgSysBP", avgSysBP)
                        bundle.putInt("avgDiaBP", avgDiaBP)
                        bundle.putInt("clinicSysBP", clinicSysBP)
                        bundle.putInt("clinicDiaBP", clinicDiaBP)
                        bundle.putString("Source", "Scan")

                        val recommendationIntent = Intent(this, RecommendationActivity::class.java)

                        recommendationIntent.putExtras(bundle)

                        startActivity(recommendationIntent)
                    }
                    .addOnFailureListener { e ->
                        errorDialogBuilder(this, ResourcesHelper.getString(this, R.string.verify_scan_saving_header), ResourcesHelper.getString(this, R.string.verify_scan_saving_body, e))
                    }
            } else {
                errorDialogBuilder(this, ResourcesHelper.getString(this, R.string.verify_scan_error_header), ResourcesHelper.getString(this, R.string.verify_scan_error_body))
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
                    setError(binding.verifyClinicSysBox, "Please enter a value between 90 and 210")
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
                    setError(binding.verifyClinicDiaBox, "Please enter a value between 60 and 120")
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })



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

        startActivity(scanIntent)
        finish()
    }

    fun rescanRecords() {
        MaterialAlertDialogBuilder(this)
            .setTitle(ResourcesHelper.getString(this, R.string.verify_scan_rescan_header))
            .setMessage(ResourcesHelper.getString(this, R.string.verify_scan_rescan_body))
            .setNegativeButton(ResourcesHelper.getString(this, R.string.no_dialog)) { dialog, _ -> dialog.dismiss() }
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
        errorDialogBuilder(this, ResourcesHelper.getString(this, R.string.verify_scan_discard_header),
            ResourcesHelper.getString(this, R.string.verify_scan_discard_body), ScanActivity::class.java, R.drawable.ic_delete)
    }

    private fun postScanValidation() {
        var errorCount = 0

        // With reference from MOH clinical practice guidelines 1/2017 @ https://www.moh.gov.sg/docs/librariesprovider4/guidelines/cpg_hypertension-booklet---nov-2017.pdf
        for (i in sysBPListHistory.size until sysBPList.size + sysBPListHistory.size) {
            val currentValueLength = sysBPFields[i].text.toString().length

            if (sysBPFields[i].text.isNullOrEmpty()) {
                errorCount += 1
                setError(sysBPFields[i].parent.parent as TextInputLayout, ResourcesHelper.getString(this, R.string.verify_scan_empty_field))
            } else if (!sysBPFields[i].text!!.isDigitsOnly()) {
                errorCount += 1
                setError(sysBPFields[i].parent.parent as TextInputLayout, ResourcesHelper.getString(this, R.string.verify_scan_whole_number))
            } else if (currentValueLength !in 2..3) {
                errorCount += 1
                setError(sysBPFields[i].parent.parent as TextInputLayout, ResourcesHelper.getString(this, R.string.verify_scan_invalid_value))
            } else if (sysBPFields[i].text.toString().toInt() !in 90..150) {
                errorCount += 1
                setError(sysBPFields[i].parent.parent as TextInputLayout, ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value))
            }
        }

        for (i in diaBPListHistory.size until diaBPList.size + diaBPListHistory.size) {
            val currentValueLength = diaBPFields[i].text.toString().length

            if (diaBPFields[i].text.isNullOrEmpty()) {
                errorCount += 1
                setError(diaBPFields[i].parent.parent as TextInputLayout, ResourcesHelper.getString(this, R.string.verify_scan_empty_field))
            } else if (!diaBPFields[i].text!!.isDigitsOnly()) {
                errorCount += 1
                setError(diaBPFields[i].parent.parent as TextInputLayout, ResourcesHelper.getString(this, R.string.verify_scan_whole_number))
            } else if (currentValueLength !in 2..3) {
                errorCount += 1
                setError(diaBPFields[i].parent.parent as TextInputLayout, ResourcesHelper.getString(this, R.string.verify_scan_invalid_value))
            } else if (diaBPFields[i].text.toString().toInt() !in 60..110) {
                errorCount += 1
                setError(diaBPFields[i].parent.parent as TextInputLayout, ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value))
            }
        }

        // Show error text but doesn't add to error count of current records
        for (i in 0 until sysBPListHistory.size) {
            val currentValueLength = sysBPFields[i].text.toString().length

            if (sysBPFields[i].text.isNullOrEmpty()) {
                setError(sysBPFields[i].parent.parent as TextInputLayout, ResourcesHelper.getString(this, R.string.verify_scan_empty_field))
            } else if (!sysBPFields[i].text!!.isDigitsOnly()) {
                setError(sysBPFields[i].parent.parent as TextInputLayout, ResourcesHelper.getString(this, R.string.verify_scan_whole_number))
            } else if (currentValueLength !in 2..3) {
                setError(sysBPFields[i].parent.parent as TextInputLayout, ResourcesHelper.getString(this, R.string.verify_scan_invalid_value))
            } else if (sysBPFields[i].text.toString().toInt() !in 90..150) {
                setError(sysBPFields[i].parent.parent as TextInputLayout, ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value))
            }
        }

        for (i in 0 until diaBPListHistory.size) {
            val currentValueLength = diaBPFields[i].text.toString().length

            if (diaBPFields[i].text.isNullOrEmpty()) {
                setError(diaBPFields[i].parent.parent as TextInputLayout, ResourcesHelper.getString(this, R.string.verify_scan_empty_field))
            } else if (!diaBPFields[i].text!!.isDigitsOnly()) {
                setError(diaBPFields[i].parent.parent as TextInputLayout, ResourcesHelper.getString(this, R.string.verify_scan_whole_number))
            } else if (currentValueLength !in 2..3) {
                setError(diaBPFields[i].parent.parent as TextInputLayout, ResourcesHelper.getString(this, R.string.verify_scan_invalid_value))
            } else if (diaBPFields[i].text.toString().toInt() !in 60..110) {
                setError(diaBPFields[i].parent.parent as TextInputLayout, ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value))
            }
        }

        if (errorCount > 6) {
            MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_error)
                .setTitle(ResourcesHelper.getString(this, R.string.verify_scan_erroneous_header))
                .setMessage(ResourcesHelper.getString(this, R.string.verify_scan_erroneous_body))
                .setNegativeButton(ResourcesHelper.getString(this, R.string.no_dialog)) { dialog, _ -> dialog.dismiss() }
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
            setError(binding.verifyClinicSys.parent.parent as TextInputLayout, ResourcesHelper.getString(this, R.string.verify_scan_empty_field))
        } else if (!binding.verifyClinicSys.text!!.isDigitsOnly()) {
            valid = false
            setError(binding.verifyClinicSys.parent.parent as TextInputLayout, ResourcesHelper.getString(this, R.string.verify_scan_whole_number))
        }

        if (binding.verifyClinicDia.text.isNullOrEmpty()) {
            valid = false
            setError(binding.verifyClinicDia.parent.parent as TextInputLayout, ResourcesHelper.getString(this, R.string.verify_scan_empty_field))
        } else if (!binding.verifyClinicDia.text!!.isDigitsOnly()) {
            valid = false
            setError(binding.verifyClinicDia.parent.parent as TextInputLayout, ResourcesHelper.getString(this, R.string.verify_scan_whole_number))
        }

        for (sysField in sysBPFields) {
            if (sysField.text.isNullOrEmpty()) {
                valid = false
                setError(sysField.parent.parent as TextInputLayout, ResourcesHelper.getString(this, R.string.verify_scan_empty_field))
            } else if (!sysField.text!!.isDigitsOnly()) {
                valid = false
                setError(sysField.parent.parent as TextInputLayout, ResourcesHelper.getString(this, R.string.verify_scan_whole_number))
            }
        }

        for (diaField in diaBPFields) {
            if (diaField.text.isNullOrEmpty()) {
                valid = false
                setError(diaField.parent.parent as TextInputLayout, ResourcesHelper.getString(this, R.string.verify_scan_empty_field))
            } else if (!diaField.text!!.isDigitsOnly()) {
                valid = false
                setError(diaField.parent.parent as TextInputLayout, ResourcesHelper.getString(this, R.string.verify_scan_whole_number))
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
                .setTitle(ResourcesHelper.getString(this, R.string.verify_scan_remove_row_header))
                .setMessage(ResourcesHelper.getString(this, R.string.verify_scan_remove_row_body))
                .setNegativeButton(ResourcesHelper.getString(this, R.string.cancel_dialog)) { dialog, _ -> dialog.dismiss() }
                .setPositiveButton(ResourcesHelper.getString(this, R.string.ok_dialog)) { dialog, _ ->
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