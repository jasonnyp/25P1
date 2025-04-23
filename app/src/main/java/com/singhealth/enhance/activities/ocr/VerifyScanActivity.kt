package com.singhealth.enhance.activities.ocr

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.os.Looper
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.RelativeSizeSpan
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.text.isDigitsOnly
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.singhealth.enhance.R
import com.singhealth.enhance.security.LogOutTimerUtil
import com.singhealth.enhance.activities.MainActivity
import com.singhealth.enhance.activities.authentication.LoginActivity
import com.singhealth.enhance.activities.validation.errorDialogBuilder
import com.singhealth.enhance.activities.validation.firebaseErrorDialog
import com.singhealth.enhance.activities.validation.patientNotFoundInSessionErrorDialog
import com.singhealth.enhance.activities.result.RecommendationActivity
import com.singhealth.enhance.databinding.ActivityVerifyScanBinding
import com.singhealth.enhance.security.AESEncryption
import com.singhealth.enhance.security.SecureSharedPreferences
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

object ResourcesHelper {
    // Retrieves the string from the res/values/strings.xml file, the same as getString() on activity files
    fun getString(context: Context, @StringRes resId: Int): String {
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

class VerifyScanActivity : AppCompatActivity(), LogOutTimerUtil.LogOutListener {
    private lateinit var binding: ActivityVerifyScanBinding

    private lateinit var progressDialog: ProgressDialog

    private lateinit var patientID: String

    private var homeSysBPTarget = 0
    private var homeDiaBPTarget = 0
    private var clinicSysBPTarget = 0
    private var clinicDiaBPTarget = 0
    private var clinicSysBP = 0
    private var clinicDiaBP = 0
    private var errorchecked = false
    private lateinit var targetHomeSysBP: String
    private lateinit var targetHomeDiaBP: String
    private lateinit var targetClinicSysBP: String
    private lateinit var targetClinicDiaBP: String
    private var sevenDay: Boolean = false
    private var isSwappingValues = false

    private var sysBPList: MutableList<String> = mutableListOf()
    private var diaBPList: MutableList<String> = mutableListOf()
    private var sysBPListHistory: MutableList<String> = mutableListOf()
    private var diaBPListHistory: MutableList<String> = mutableListOf()

    private var sysBPFields = mutableListOf<TextInputEditText>()
    private var diaBPFields = mutableListOf<TextInputEditText>()

    private var totalSysBP = 0
    private var totalDiaBP = 0
    private var avgSysBP = 0
    private var avgDiaBP = 0

    private val validDayIndices = mutableListOf<Int>() // To track the days used for calculating average

    private val undoStackOld = mutableListOf<Pair<MutableList<String>, MutableList<String>>>()
    private val undoStackCurrent = mutableListOf<Pair<MutableList<String>, MutableList<String>>>()
    private val maxUndoStackSize = 10

    private val db = Firebase.firestore

    private val typingDelayHandler = Handler(Looper.getMainLooper())
    private var typingRunnable: Runnable? = null

    private var userChoseToEdit = false
    private var allowBypassErrors = false
    private var shouldCheckErrors = false
    private var errorChecked = false // Global variable to track red errors

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
        binding = ActivityVerifyScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
//        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
//            override fun handleOnBackPressed() {
//                startActivity(Intent(this@VerifyScanActivity, ScanActivity::class.java))
//                finish()
//            }
//        })

        // Handle the physical back button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitConfirmationDialog()
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

        targetHomeSysBP = patientSharedPreferences.getString("targetHomeSysBP", null).toString()
        targetHomeDiaBP = patientSharedPreferences.getString("targetHomeDiaBP", null).toString()

        targetClinicSysBP = patientSharedPreferences.getString("targetClinicSysBP", null).toString()
        targetClinicDiaBP = patientSharedPreferences.getString("targetClinicDiaBP", null).toString()

        sevenDay = scanBundle?.getBoolean("sevenDay", false)!!

        sysBPList = scanBundle?.getStringArrayList("sysBPList")?.toMutableList()!!
        diaBPList = scanBundle?.getStringArrayList("diaBPList")?.toMutableList()!!

        val docRef = db.collection("patients").document(patientID)

        docRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    if (AESEncryption().decrypt(
                            document.getString("targetHomeSys").toString()
                        ) == "" || AESEncryption().decrypt(
                            document.getString("targetHomeDia").toString()
                        ) == ""
                    ) {
                        homeSysBPTarget = 0
                        homeDiaBPTarget = 0
                    } else {
                        homeSysBPTarget =
                            AESEncryption().decrypt(document.getString("targetHomeSys").toString())
                                .toInt()
                        homeDiaBPTarget =
                            AESEncryption().decrypt(document.getString("targetHomeDia").toString())
                                .toInt()
                    }

                    if (AESEncryption().decrypt(
                            document.getString("targetClinicSys").toString()
                        ) == "" || AESEncryption().decrypt(
                            document.getString("targetClinicDia").toString()
                        ) == ""
                    ) {
                        clinicSysBPTarget = 0
                        clinicDiaBPTarget = 0
                    } else {
                        clinicSysBPTarget =
                            AESEncryption().decrypt(
                                document.getString("targetClinicSys").toString()
                            )
                                .toInt()
                        clinicDiaBPTarget =
                            AESEncryption().decrypt(
                                document.getString("targetClinicDia").toString()
                            )
                                .toInt()
                    }

                    binding.verifyHomeTargetSys.text = homeSysBPTarget.toString()
                    binding.verifyHomeTargetDia.text = homeDiaBPTarget.toString()
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
                binding.verifyHomeTargetSys.text = scanBundle.getString("homeSysBPTarget")
            }
            if (scanBundle.containsKey("homeDiaBPTarget")) {
                binding.verifyHomeTargetDia.text = scanBundle.getString("homeDiaBPTarget")
            }
            if (scanBundle.containsKey("clinicSysBPTarget")) {
                binding.verifyClinicTargetSys.text = scanBundle.getString("clinicSysBPTarget")
            }
            if (scanBundle.containsKey("clinicDiaBPTarget")) {
                binding.verifyClinicTargetDia.text = scanBundle.getString("clinicDiaBPTarget")
            }
            if (scanBundle.containsKey("clinicSysBP")) {
                binding.verifyClinicSys.setText(scanBundle.getString("clinicSysBP"))
            }
            if (scanBundle.containsKey("clinicDiaBP")) {
                binding.verifyClinicDia.setText(scanBundle.getString("clinicDiaBP"))
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

            if (scanBundle.containsKey("sysBPListHistory") && scanBundle.containsKey("diaBPListHistory") && !sevenDay) {
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


        // The number used to hide extra rows that were included in seven Day Check function
        val validSysBPList = sysBPList.filter { it != "-2" }
        val validDiaBPList = diaBPList.filter { it != "-2"}

        var totalRows = maxOf(validSysBPList.size, validDiaBPList.size)
        if (sysBPListHistory.isNotEmpty() && diaBPListHistory.isNotEmpty()) {
            val validSysBPListHistory = sysBPListHistory.filter { it != "-2" }
            val validDiaBPListHistory = diaBPListHistory.filter { it != "-2"}
            totalRows += maxOf(validSysBPListHistory.size, validDiaBPListHistory.size)
        }

        val currentRows = maxOf(validSysBPList.size, validDiaBPList.size)

        println("Total rows: $totalRows")
        println("Current rows: $currentRows")

        println("sysBPFields: ${sysBPFields.size}")
        println("diaBPFields: ${diaBPFields.size}")

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

        // More options button
        binding.moreOptionsBtn.setOnClickListener {
            val modalBottomSheet = ModalBottomSheet(this, sevenDay)
            modalBottomSheet.show(supportFragmentManager, ModalBottomSheet.TAG)
        }

        // Edit button for target home BP to show fields and hide edit button
        binding.editHomeBPLayout.setOnClickListener {
            binding.editHomeBPLayout.visibility = View.GONE
            binding.verifyEditHomeBPTarget.visibility = View.VISIBLE
            binding.verifyEditHomeBPTextFields.visibility = View.VISIBLE
            binding.homeBPTargetTV.visibility = View.GONE
            binding.homeTargetSys.setText(homeSysBPTarget.toString())
            binding.homeTargetDia.setText(homeDiaBPTarget.toString())
        }

        binding.verifyEditHomeSave.setOnClickListener {
            showUpdateHomeBPTargetConfirmationDialog()
        }

        binding.verifyEditHomeExit.setOnClickListener {
            showExitHomeBPTargetConfirmationDialog()
        }

        // Edit button for target clinic BP to show fields and hide edit button
        binding.editClinicBPLayout.setOnClickListener {
            binding.editClinicBPLayout.visibility = View.GONE
            binding.verifyEditClinicBPTarget.visibility = View.VISIBLE
            binding.verifyEditClinicBPTextFields.visibility = View.VISIBLE
            binding.clinicBPTargetTV.visibility = View.GONE
            binding.clinicTargetSys.setText(clinicSysBPTarget.toString())
            binding.clinicTargetDia.setText(clinicDiaBPTarget.toString())
        }

        binding.verifyEditClinicSave.setOnClickListener {
            showUpdateClinicBPTargetConfirmationDialog()
        }

        binding.verifyEditClinicExit.setOnClickListener {
            showExitClinicBPTargetConfirmationDialog()
        }


        // Calculate and save average BP, home BP and clinic BP targets, then display outcome and recommendation (separate activity)
        binding.calculateAvgBPBtn.setOnClickListener {
            shouldCheckErrors = false
            errorChecked = false

            val hasRedSysDiaErrors = (sysBPFields + diaBPFields).any {
                it.error?.toString()?.let { msg ->
                    msg.contains("Field is empty", true) ||
                            msg.contains("Impossible Value", true) ||
                            msg.contains("Invalid input", true)
                } == true
            }

            val boldLargeEditPrompt = SpannableString(
                ResourcesHelper.getString(this, R.string.verify_scan_error_body)
            ).apply {
                setSpan(StyleSpan(Typeface.BOLD), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(RelativeSizeSpan(1.15f), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            if (hasRedSysDiaErrors && !allowBypassErrors) {
                MaterialAlertDialogBuilder(this)
                    .setIcon(R.drawable.ic_error)
                    .setMessage(boldLargeEditPrompt)
                    .setNegativeButton(ResourcesHelper.getString(this, R.string.yes_dialog)) { dialog, _ ->
                        userChoseToEdit = true
                        allowBypassErrors = false
                        dialog.dismiss()
                    }
                    .setPositiveButton(ResourcesHelper.getString(this, R.string.no_dialog)) { _, _ ->
                        userChoseToEdit = false
                        allowBypassErrors = true
                        shouldCheckErrors = true
                        postScanValidation()        // ✅ Now runs here
                        proceedToBPValidation()
                    }
                    .show()
                return@setOnClickListener
            }

            shouldCheckErrors = true
            postScanValidation()
            proceedToBPValidation()
        }

        binding.homeTargetSys.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s?.toString()?.toIntOrNull()
                if (input != null && input in 90..179) {
                    // Valid range for verifyClinicSys (91 to 209) <- Outdated values from last batch, copied verifyclinic as reference, change/remove if needed
                    setError(binding.homeTargetSysBox, null)
                } else {
                    // Invalid range, set an error message and icon
                    setError(
                        binding.homeTargetSysBox,
                        ResourcesHelper.getString(
                            this@VerifyScanActivity,
                            R.string.verify_scan_valid_value,
                            90,
                            179
                        )
                    )
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.homeTargetDia.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s?.toString()?.toIntOrNull()
                if (input != null && input in 50..99) {
                    // Valid range for verifyClinicDia (61 to 119) <- Outdated values from last batch, copied verifyclinic as reference, change/remove if needed
                    setError(binding.homeTargetDiaBox, null)
                } else {
                    // Invalid range, set an error message and icon
                    setError(
                        binding.homeTargetDiaBox,
                        ResourcesHelper.getString(
                            this@VerifyScanActivity,
                            R.string.verify_scan_valid_value,
                            50,
                            99
                        )
                    )
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.clinicTargetSys.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s?.toString()?.toIntOrNull()
                if (input != null && input in 90..179) {
                    // Valid range for verifyClinicSys (91 to 209) <- Outdated values from last batch, copied verifyclinic as reference, change/remove if needed
                    setError(binding.clinicTargetSysBox, null)
                } else {
                    // Invalid range, set an error message and icon
                    setError(
                        binding.clinicTargetSysBox,
                        ResourcesHelper.getString(
                            this@VerifyScanActivity,
                            R.string.verify_scan_valid_value,
                            90,
                            179
                        )
                    )
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.clinicTargetDia.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s?.toString()?.toIntOrNull()
                if (input != null && input in 50..99) {
                    // Valid range for verifyClinicDia (61 to 119) <- Outdated values from last batch, copied verifyclinic as reference, change/remove if needed
                    setError(binding.clinicTargetDiaBox, null)
                } else {
                    // Invalid range, set an error message and icon
                    setError(
                        binding.clinicTargetDiaBox,
                        ResourcesHelper.getString(
                            this@VerifyScanActivity,
                            R.string.verify_scan_valid_value,
                            50,
                            99
                        )
                    )
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.verifyClinicSys.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s?.toString()?.toIntOrNull()
                if (input != null && input in 90..179) {
                    // Valid range for verifyClinicSys (91 to 209) <- Outdated values from last batch, change/remove if needed
                    setError(binding.verifyClinicSysBox, null)
                } else {
                    // Invalid range, set an error message and icon
                    setError(
                        binding.verifyClinicSysBox,
                        ResourcesHelper.getString(
                            this@VerifyScanActivity,
                            R.string.verify_scan_valid_value,
                            90,
                            179
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
                if (input != null && input in 50..99) {
                    // Valid range for verifyClinicDia (61 to 119) <- Outdated values from last batch, change/remove if needed
                    setError(binding.verifyClinicDiaBox, null)
                } else {
                    // Invalid range, set an error message and icon
                    setError(
                        binding.verifyClinicDiaBox,
                        ResourcesHelper.getString(
                            this@VerifyScanActivity,
                            R.string.verify_scan_valid_value,
                            50,
                            99
                        )
                    )
                }
            }

            override fun afterTextChanged(s: Editable?) {}


        })
        setupValidation()

        progressDialog.dismiss()
    }

//    override fun onOptionsItemSelected(item: MenuItem): Boolean {
//        return when (item.itemId) {
//            android.R.id.home -> {
//                startActivity(Intent(this, ScanActivity::class.java))
//                finish()
//                true
//            }
//
//            else -> super.onOptionsItemSelected(item)
//        }
//    }

    private fun proceedToBPValidation() {
        // 🚫 Prevent validation if user chose to edit
        if (userChoseToEdit) {
            userChoseToEdit = false  // reset for next click
            return
        }

        // Reset flag to prevent loops
        shouldCheckErrors = false

        val clinicSys = binding.verifyClinicSys.text?.toString()
        val clinicDia = binding.verifyClinicDia.text?.toString()

        val clinicSysEmpty = clinicSys.isNullOrBlank()
        val clinicDiaEmpty = clinicDia.isNullOrBlank()


        if (clinicSysEmpty || clinicDiaEmpty) {
            if (clinicSysEmpty) {
                binding.verifyClinicSys.error = ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
                setErrorIcon(binding.verifyClinicSys, R.color.red)
            }
            if (clinicDiaEmpty) {
                binding.verifyClinicDia.error = ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
                setErrorIcon(binding.verifyClinicDia, R.color.red)
            }

//            errorDialogBuilder(
//                this,
//                ResourcesHelper.getString(this, R.string.verify_scan_error_header),
//                ResourcesHelper.getString(this, R.string.verify_scan_error_body_clinic_bp)
//            )
            val largeClinicBPMsg = SpannableString(
                ResourcesHelper.getString(this, R.string.verify_scan_error_body_clinic_bp)
            ).apply {
                setSpan(StyleSpan(Typeface.BOLD), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(RelativeSizeSpan(1.15f), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_error)
                .setMessage(largeClinicBPMsg)
                .setPositiveButton(ResourcesHelper.getString(this, R.string.ok_dialog)) { dialog, _ -> dialog.dismiss() }
                .show()

            scrollToClinicBP()
            return
        }

        // 🟢 Success: Safe to calculate now
        allowBypassErrors = false
        shouldCheckErrors = false
        errorChecked = false

        Log.d("DEBUG_CALC_BP", "Proceeding with BP Calculation.")

        getBPTarget()
        val filteredSysBPList = sysBPList.filter { it.isNotBlank() && it != "-2" }
        val filteredDiaBPList = diaBPList.filter { it.isNotBlank() && it != "-2" }
        val maxFilteredRows = maxOf(filteredSysBPList.size, filteredDiaBPList.size)
        val maxHistoryRows = maxOf(sysBPListHistory.size, diaBPListHistory.size)
        val finalRows = maxHistoryRows + maxFilteredRows
        val visit: HashMap<String, *>

        sysBPList.clear()
        diaBPList.clear()

        for (field in sysBPFields) {
            sysBPList.add(field.text?.toString() ?: "")
        }
        for (field in diaBPFields) {
            diaBPList.add(field.text?.toString() ?: "")
        }

        if (sevenDay) {
            calcSevenDayAvgBP()
        } else {
            calcAvgBP()
        }

        if (!validateClinicBPFields()) {
            allowBypassErrors = false
            shouldCheckErrors = false
            return
        }

        clinicSysBP = binding.verifyClinicSys.text.toString().toInt()
        clinicDiaBP = binding.verifyClinicDia.text.toString().toInt()

        // Save record into database
        visit = hashMapOf(
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
            "scanRecordCount" to finalRows,
            "validDayIndices" to validDayIndices.distinct().reversed(),
            "sevenDay" to sevenDay
        )

        db.collection("patients").document(patientID).collection("visits").add(visit)
            .addOnSuccessListener {
                Toast.makeText(
                    this,
                    ResourcesHelper.getString(this, R.string.verify_scan_calculation_successful),
                    Toast.LENGTH_SHORT
                ).show()

                val bundle = Bundle().apply {
                    putInt("avgSysBP", avgSysBP)
                    putInt("avgDiaBP", avgDiaBP)
                    putInt("clinicSysBP", clinicSysBP)
                    putInt("clinicDiaBP", clinicDiaBP)
                    putInt("scanRecordCount", finalRows)
                    putString("Source", "Scan")
                    putBoolean("sevenDay", sevenDay)
                }

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
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                showExitConfirmationDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // if clinic bp values empty
    private fun showExitConfirmationDialog() {
        val title = SpannableString(getString(R.string.confirm_navigation)).apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(Color.BLACK), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(getString(R.string.pop_up_message))
            .setPositiveButton(getString(R.string.yes_dialog)) { _, _ ->
                navigateToHome()
            }
            .setNegativeButton(getString(R.string.no_dialog)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun navigateToHome() {
        val intent = Intent(this, ScanActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
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

        binding.verifyHomeTargetSys.let {
            scanIntent.putExtra("homeSysBPTarget", binding.verifyHomeTargetSys.text.toString())
        }
        binding.verifyHomeTargetDia.let {
            scanIntent.putExtra("homeDiaBPTarget", binding.verifyHomeTargetDia.text.toString())
        }
        binding.verifyClinicTargetSys?.let {
            scanIntent.putExtra("clinicSysBPTarget", binding.verifyClinicTargetSys.text.toString())
        }
        binding.verifyClinicTargetDia?.let {
            scanIntent.putExtra("clinicDiaBPTarget", binding.verifyClinicTargetDia.text.toString())
        }
        binding.verifyClinicSys?.let {
            scanIntent.putExtra("clinicSysBP", binding.verifyClinicSys.text.toString())
        }
        binding.verifyClinicDia?.let {
            scanIntent.putExtra("clinicDiaBP", binding.verifyClinicDia.text.toString())
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

                binding.verifyHomeTargetSys?.let {
                    scanIntent.putExtra(
                        "homeSysBPTarget",
                        binding.verifyHomeTargetSys.text.toString()
                    )
                }
                binding.verifyHomeTargetDia?.let {
                    scanIntent.putExtra(
                        "homeDiaBPTarget",
                        binding.verifyHomeTargetDia.text.toString()
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
                    if (binding.verifyClinicSys.text.toString() != "") {
                        scanIntent.putExtra(
                            "clinicSysBP",
                            binding.verifyClinicSys.text.toString()
                        )
                    }
                }
                binding.verifyClinicDia?.let {
                    if (binding.verifyClinicDia.text.toString() != "") {
                        scanIntent.putExtra(
                            "clinicDiaBP",
                            binding.verifyClinicDia.text.toString()
                        )
                    }
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
        println("Saving state for undo...")
        println("Current sysBPList: $sysBPList")
        println("Current diaBPList: $diaBPList")

        // Check if the current state is different from the last saved state
        if (undoStackCurrent.isEmpty() || !listsAreEqual(undoStackCurrent.last().first, sysBPList) || !listsAreEqual(undoStackCurrent.last().second, diaBPList) || undoStackOld.isEmpty() || !listsAreEqual(undoStackOld.last().first, sysBPListHistory) || !listsAreEqual(undoStackOld.last().second, diaBPListHistory)) {
            if (undoStackCurrent.size >= maxUndoStackSize) {
                println("Undo stack is full. Removing oldest state.")
                undoStackCurrent.removeAt(0)
                undoStackOld.removeAt(0)
            }
            println("Adding current state to undo stack.")
            undoStackCurrent.add(Pair(sysBPList.toMutableList(), diaBPList.toMutableList()))
            undoStackOld.add(Pair(sysBPListHistory.toMutableList(), diaBPListHistory.toMutableList()))
        }  else {
            println("Current state is the same as the last state in the undo stack. Not adding.")
        }
        println("Undo stack size after save: ${undoStackOld.size}")
    }

    private fun listsAreEqual(list1: MutableList<String>, list2: MutableList<String>): Boolean {
        return list1.size == list2.size && list1.zip(list2).all { it.first == it.second }
    }

    fun undo() {
        println("Performing undo...")
        if (undoStackCurrent.isNotEmpty() || undoStackOld.isNotEmpty()) {
            // Remove the last state from the undo stack after restoring
            val lastStateCurrent = undoStackCurrent.removeAt(undoStackCurrent.size - 1)
            val lastStateOld = undoStackOld.removeAt(undoStackOld.size - 1)
            println("Restoring state from undo stack...")
            println("Last saved sysBPList: ${lastStateCurrent.first}")
            println("Last saved diaBPList: ${lastStateCurrent.second}")
            println("Last saved sysBPListHistory: ${lastStateOld.first}")
            println("Last saved diaBPListHistory: ${lastStateOld.second}")

            sysBPList.clear()
            sysBPList.addAll(lastStateCurrent.first)
            diaBPList.clear()
            diaBPList.addAll(lastStateCurrent.second)

            sysBPListHistory.clear()
            sysBPListHistory.addAll(lastStateOld.first)
            diaBPListHistory.clear()
            diaBPListHistory.addAll(lastStateOld.second)

            println("Restored sysBPList: $sysBPList")
            println("Restored diaBPList: $diaBPList")
            println("Restored sysBPListHistory: $sysBPListHistory")
            println("Restored diaBPListHistory: $diaBPListHistory")
            Toast.makeText(this, "Undo successful", Toast.LENGTH_SHORT).show()
            refreshViews()
        } else {
            println("Undo stack is empty")
            Toast.makeText(this, "No more undos available", Toast.LENGTH_SHORT).show()
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
            if (sysBPListHistory.isNotEmpty() && diaBPListHistory.isNotEmpty()){
                for (i in 0 until minOf(sysBPListHistory.size, diaBPListHistory.size)) {
                    addRow(sysBPListHistory[i], diaBPListHistory[i])
                }
                addDivider()
            }
            for (i in 0 until maxOf(sysBPList.size, diaBPList.size)) {
                addRow(sysBPList[i], diaBPList[i])
            }
        }

        setupValidation()
        postScanValidation()
        println("sysBPList after adding updating scans: $sysBPList")
        println("diaBPList after adding updating scans: $diaBPList")
    }

    // edited (17 march)
//    private fun postScanValidation() {
//        var errorCount = 0
//        if (sevenDay) {
//            println("sysBPlist size is ${sysBPList.size}")
//            println("sysBPFields size is ${sysBPFields.size}")
//
//            for (i in 0 until sysBPFields.size) {
//
//                if (sysBPFields[i].text.isNullOrEmpty()) {
//                    continue
//                } else if (!sysBPFields[i].text!!.isDigitsOnly()) {
//                    errorCount += 1
//                    sysBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
//                } else if (sysBPFields[i].text!!.toString().toInt() == -1) {
//                    errorCount += 1
//                    sysBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
//                } else if (sysBPFields[i].text.toString().length !in 2..3) {
//                    errorCount += 1
//                    sysBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_invalid_value)
//                } else if (sysBPFields[i].text.toString().toInt() !in 90..179) { // change range to 90 - 179 (17 march)
//                    errorCount += 1
//                    sysBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
//                }
//            }
//
//            for (i in 0 until diaBPFields.size) {
//
//                if (diaBPFields[i].text.isNullOrEmpty()) {
//                    continue
//                } else if (!diaBPFields[i].text!!.isDigitsOnly()) {
//                    errorCount += 1
//                    diaBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
//                } else if (diaBPFields[i].text!!.toString().toInt() == -1) {
//                    errorCount += 1
//                    diaBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_replace_value)
//                } else if (diaBPFields[i].text.toString().length !in 2..3) {
//                    errorCount += 1
//                    diaBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_invalid_value)
//                } else if (diaBPFields[i].text.toString().toInt() !in 50..99) { // change range to 50 - 99 (17 march)
//                    errorCount += 1
//                    diaBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
//                }
//            }
//
//        } else {
//            if (sysBPListHistory.isEmpty() && diaBPListHistory.isEmpty()) {
//                // Outdated values, could refer to guidelines if there is a need to change systolic and diastolic values
//                // With reference from MOH clinical practice guidelines 1/2017 @ https://www.moh.gov.sg/docs/librariesprovider4/guidelines/cpg_hypertension-booklet---nov-2017.pdf
//                for (i in 0 until sysBPList.size) {
//                    val currentValueLength = sysBPFields[i].text.toString().length
//
//                    if (sysBPFields[i].text.isNullOrEmpty()) {
//                        errorCount += 1
//                        sysBPFields[i].error =
//                            ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
//                    } else if (!sysBPFields[i].text!!.isDigitsOnly()) {
//                        errorCount += 1
//                        sysBPFields[i].error =
//                            ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
//                    } else if (sysBPFields[i].text!!.toString().toInt() == -1) {
//                        errorCount += 1
//                        sysBPFields[i].error =
//                            ResourcesHelper.getString(this, R.string.verify_scan_replace_value)
//                    } else if (currentValueLength !in 2..3) {
//                        errorCount += 1
//                        sysBPFields[i].error =
//                            ResourcesHelper.getString(this, R.string.verify_scan_invalid_value)
//                    } else if (sysBPFields[i].text.toString().toInt() !in 100..200) {
//                        errorCount += 1
//                        sysBPFields[i].error =
//                            ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
//                    }
//                }
//
//                for (i in 0 until diaBPList.size) {
//                    val currentValueLength = diaBPFields[i].text.toString().length
//
//                    if (diaBPFields[i].text.isNullOrEmpty()) {
//                        errorCount += 1
//                        diaBPFields[i].error =
//                            ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
//                    } else if (!diaBPFields[i].text!!.isDigitsOnly()) {
//                        errorCount += 1
//                        diaBPFields[i].error =
//                            ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
//                    } else if (diaBPFields[i].text!!.toString().toInt() == -1) {
//                        errorCount += 1
//                        diaBPFields[i].error =
//                            ResourcesHelper.getString(this, R.string.verify_scan_replace_value)
//                    } else if (currentValueLength !in 2..3) {
//                        errorCount += 1
//                        diaBPFields[i].error =
//                            ResourcesHelper.getString(this, R.string.verify_scan_invalid_value)
//                    } else if (diaBPFields[i].text.toString().toInt() !in 50..99) {
//                        errorCount += 1
//                        diaBPFields[i].error =
//                            ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
//                    }
//                }
//            }
//            else{
//                // Outdated values, could refer to guidelines if there is a need to change systolic and diastolic values
//                // With reference from MOH clinical practice guidelines 1/2017 @ https://www.moh.gov.sg/docs/librariesprovider4/guidelines/cpg_hypertension-booklet---nov-2017.pdf
//                for (i in 0 until sysBPList.size) {
//                    val currentValueLength = sysBPFields[i + sysBPListHistory.size].text.toString().length
//
//                    if (sysBPFields[i + sysBPListHistory.size].text.isNullOrEmpty()) {
//                        errorCount += 1
//                        sysBPFields[i + sysBPListHistory.size].error =
//                            ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
//                    } else if (!sysBPFields[i + sysBPListHistory.size].text!!.isDigitsOnly()) {
//                        errorCount += 1
//                        sysBPFields[i + sysBPListHistory.size].error =
//                            ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
//                    } else if (sysBPFields[i + sysBPListHistory.size].text!!.toString().toInt() == -1) {
//                        errorCount += 1
//                        sysBPFields[i + sysBPListHistory.size].error =
//                            ResourcesHelper.getString(this, R.string.verify_scan_replace_value)
//                    } else if (currentValueLength !in 2..3) {
//                        errorCount += 1
//                        sysBPFields[i + sysBPListHistory.size].error =
//                            ResourcesHelper.getString(this, R.string.verify_scan_invalid_value)
//                    } else if (sysBPFields[i + sysBPListHistory.size].text.toString().toInt() !in 100..200) {
//                        errorCount += 1
//                        sysBPFields[i + sysBPListHistory.size].error =
//                            ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
//                    }
//                }
//
//                for (i in 0 until diaBPList.size) {
//                    val currentValueLength = diaBPFields[i + diaBPListHistory.size].text.toString().length
//
//                    if (diaBPFields[i + diaBPListHistory.size].text.isNullOrEmpty()) {
//                        errorCount += 1
//                        diaBPFields[i + diaBPListHistory.size].error =
//                            ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
//                    } else if (!diaBPFields[i + diaBPListHistory.size].text!!.isDigitsOnly()) {
//                        errorCount += 1
//                        diaBPFields[i + diaBPListHistory.size].error =
//                            ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
//                    } else if (diaBPFields[i + diaBPListHistory.size].text!!.toString().toInt() == -1) {
//                        errorCount += 1
//                        diaBPFields[i + diaBPListHistory.size].error =
//                            ResourcesHelper.getString(this, R.string.verify_scan_replace_value)
//                    } else if (currentValueLength !in 2..3) {
//                        errorCount += 1
//                        diaBPFields[i + diaBPListHistory.size].error =
//                            ResourcesHelper.getString(this, R.string.verify_scan_invalid_value)
//                    } else if (diaBPFields[i + diaBPListHistory.size].text.toString().toInt() !in 50..99) {
//                        errorCount += 1
//                        diaBPFields[i + diaBPListHistory.size].error =
//                            ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
//                    }
//                }
//                // Show error text but doesn't add to error count of current records
//                for (i in 0 until sysBPListHistory.size) {
//                    val currentValueLength = sysBPFields[i].text.toString().length
//
//                    if (sysBPFields[i].text.isNullOrEmpty()) {
//                        sysBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
//                    } else if (!sysBPFields[i].text!!.isDigitsOnly()) {
//                        sysBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
//                    } else if (sysBPFields[i].text!!.toString().toInt() == -1) {
//                        sysBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_replace_value)
//                    } else if (currentValueLength !in 2..3) {
//                        sysBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_invalid_value)
//                    } else if (sysBPFields[i].text.toString().toInt() !in 100..200) {
//                        sysBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
//                    }
//                }
//
//                for (i in 0 until diaBPListHistory.size) {
//                    val currentValueLength = diaBPFields[i].text.toString().length
//
//                    if (diaBPFields[i].text.isNullOrEmpty()) {
//                        diaBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
//                    } else if (!diaBPFields[i].text!!.isDigitsOnly()) {
//                        diaBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
//                    } else if (diaBPFields[i].text!!.toString().toInt() == -1) {
//                        diaBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_replace_value)
//                    } else if (currentValueLength !in 2..3) {
//                        diaBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_invalid_value)
//                    } else if (diaBPFields[i].text.toString().toInt() !in 50..99) {
//                        diaBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
//                    }
//                }
//            }
//        }
//        if (errorCount == 6 && !errorchecked) {
//            errorchecked = true
//            MaterialAlertDialogBuilder(this)
//                .setIcon(R.drawable.ic_error)
//                .setTitle(ResourcesHelper.getString(this, R.string.verify_scan_erroneous_header))
//                .setMessage(ResourcesHelper.getString(this, R.string.verify_scan_erroneous_body))
//                .setNegativeButton(
//                    ResourcesHelper.getString(
//                        this,
//                        R.string.no_dialog
//                    )
//                ) { dialog, _ -> dialog.dismiss() }
//                .setPositiveButton(ResourcesHelper.getString(this, R.string.yes_dialog)) { _, _ ->
//                    val scanIntent = Intent(this, ScanActivity::class.java)
//
//                    binding.verifyHomeTargetSys?.let {
//                        scanIntent.putExtra(
//                            "homeSysBPTarget",
//                            binding.verifyHomeTargetSys.text.toString()
//                        )
//                    }
//                    binding.verifyHomeTargetDia?.let {
//                        scanIntent.putExtra(
//                            "homeDiaBPTarget",
//                            binding.verifyHomeTargetDia.text.toString()
//                        )
//                    }
//                    binding.verifyClinicTargetSys?.let {
//                        scanIntent.putExtra(
//                            "clinicSysBPTarget",
//                            binding.verifyClinicTargetSys.text.toString()
//                        )
//                    }
//                    binding.verifyClinicTargetDia?.let {
//                        scanIntent.putExtra(
//                            "clinicDiaBPTarget",
//                            binding.verifyClinicTargetDia.text.toString()
//                        )
//                    }
//                    binding.verifyClinicSys?.let {
//                        scanIntent.putExtra(
//                            "clinicSysBP",
//                            binding.verifyClinicSys.text.toString()
//                        )
//                    }
//                    binding.verifyClinicDia?.let {
//                        scanIntent.putExtra(
//                            "clinicDiaBP",
//                            binding.verifyClinicDia.text.toString()
//                        )
//                    }
//
//                    if (!sysBPListHistory.isNullOrEmpty()) {
//                        scanIntent.putStringArrayListExtra(
//                            "sysBPListHistory",
//                            ArrayList(sysBPListHistory)
//                        )
//                    }
//                    if (!diaBPListHistory.isNullOrEmpty()) {
//                        scanIntent.putStringArrayListExtra(
//                            "diaBPListHistory",
//                            ArrayList(diaBPListHistory)
//                        )
//                    }
//
//                    startActivity(scanIntent)
//                    finish()
//                }
//                .show()
//        }
//    }

    private fun setErrorIcon(field: EditText?, colorResId: Int) {
        if (field == null) {
            Log.e("DEBUG_ERROR_CHECK", "Attempted to set error icon on null field.")
            return
        }

        val drawable = ContextCompat.getDrawable(this, R.drawable.ic_error)?.mutate()
        drawable?.setTint(ContextCompat.getColor(this, colorResId))

        // Remove previous drawable first to avoid caching issues
        field.setCompoundDrawables(null, null, null, null)

        // Explicitly assign the mutated drawable again
        field.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null)

        // Invalidate view to force UI refresh
        field.invalidate()

        val fieldHint = try {
            field.hint ?: "no_hint"
        } catch (e: Exception) {
            "unknown_field"
        }

        Log.d("DEBUG_ERROR_CHECK", "Field: ${field.hint}, Color: ${resources.getResourceEntryName(colorResId)}")

        if (colorResId == R.color.red) {
            errorChecked = true
            Log.d("DEBUG_ERROR_CHECK", "Set errorChecked to TRUE due to RED error.")
        } else {
            // Always reset `errorChecked` if no more red errors exist
            val anyRedErrors = sysBPFields.any {
                it.error != null && (
                        it.error.toString().contains("empty field", true) ||
                                it.error.toString().contains("impossible value", true) ||
                                it.error.toString().contains("invalid input", true)
                        )
            } || diaBPFields.any {
                it.error != null && (
                        it.error.toString().contains("empty field", true) ||
                                it.error.toString().contains("impossible value", true) ||
                                it.error.toString().contains("invalid input", true)
                        )
            }

            errorChecked = anyRedErrors
            Log.d("DEBUG_ERROR_CHECK", "Rechecked all fields. Final errorChecked: $errorChecked")
        }
    }


    private fun clearError(field: EditText) {
        field.error = null
        field.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
    }

    private fun postScanValidation() {
        errorChecked = false

        var errorCount = 0
        var orangeErrorCount = 0

        if (sevenDay) {
            println("sysBPlist size is ${sysBPList.size}")
            println("sysBPFields size is ${sysBPFields.size}")

            for (i in 0 until sysBPFields.size) {
                val value = sysBPFields[i].text.toString()

                if (value.isEmpty() || value.length >= 4) {
                    // Mark as empty with an error, color-coded red
                    errorCount += 1
                    sysBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
                    setErrorIcon(sysBPFields[i], R.color.red)
                } else if (value.length == 1) {
                    errorCount += 1
                    sysBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_impossible_value)
                    setErrorIcon(sysBPFields[i], R.color.red)
                } else if (!value.isDigitsOnly()) {
                    // Invalid characters like abc, !@#, set as blank
                    sysBPFields[i].setText("")
                } else if (value.toInt() < 0) {
                    // Negative value, show error and color red
                    errorCount += 1
                    sysBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
                    setErrorIcon(sysBPFields[i], R.color.red)
                } else if (sysBPFields[i].text!!.toString().toInt() == -1) {
                    errorCount += 1
                    sysBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
                } else if (value.toInt() in 90..179) {
                    // Normal range, no error
                    sysBPFields[i].error = null
                } else if (value.toInt() in 50..300) {
                    // Out-of-range but acceptable, color-coded orange
                    orangeErrorCount++
                    sysBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_out_of_range_header)
                    setErrorIcon(sysBPFields[i], R.color.orange)
                } else {
                    // Impossible value, color-coded red
                    errorCount += 1
                    sysBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_impossible_value)
                    setErrorIcon(sysBPFields[i], R.color.red)
                }
            }

            for (i in 0 until diaBPFields.size) {
                val value = diaBPFields[i].text.toString()

                if (value.isEmpty() || value.length >= 4) {
                    // Mark as empty with an error, color-coded red
                    errorCount += 1
                    diaBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
                    setErrorIcon(diaBPFields[i], R.color.red)
                } else if (value.length == 1) {
                    errorCount += 1
                    diaBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_impossible_value)
                    setErrorIcon(diaBPFields[i], R.color.red)
                } else if (!value.isDigitsOnly()) {
                    // Invalid characters like abc, !@#, set as blank
                    diaBPFields[i].setText("")
                } else if (value.toInt() < 0) {
                    // Negative value, show error and color red
                    errorCount += 1
                    diaBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
                    setErrorIcon(diaBPFields[i], R.color.red)
                } else if (diaBPFields[i].text!!.toString().toInt() == -1) {
                    errorCount += 1
                    diaBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
                } else if (value.toInt() in 50..99) {
                    // Normal range, no error
                    diaBPFields[i].error = null
                } else if (value.toInt() in 30..180) {
                    // Out-of-range but acceptable, color-coded orange
                    orangeErrorCount++
                    diaBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_out_of_range_header)
                    setErrorIcon(diaBPFields[i], R.color.orange)
                } else {
                    // Impossible value, color-coded red
                    errorCount += 1
                    diaBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_impossible_value)
                    setErrorIcon(diaBPFields[i], R.color.red)
                }
            }

        } else { // general scan
            if (sysBPListHistory.isEmpty() && diaBPListHistory.isEmpty()) {
                // Outdated values, could refer to guidelines if there is a need to change systolic and diastolic values
                // With reference from MOH clinical practice guidelines 1/2017 @ https://www.moh.gov.sg/docs/librariesprovider4/guidelines/cpg_hypertension-booklet---nov-2017.pdf
                for (i in 0 until sysBPList.size) {
                    val value = sysBPFields[i].text.toString()

                    if (value.isEmpty() || value.length >= 4) {
                        // Mark as empty with an error, color-coded red
                        errorCount += 1
                        sysBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
                        setErrorIcon(sysBPFields[i], R.color.red)
                    } else if (value.length == 1) {
                        errorCount += 1
                        sysBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_impossible_value)
                        setErrorIcon(sysBPFields[i], R.color.red)
                    } else if (!value.isDigitsOnly()) {
                        // Invalid characters like abc, !@#, set as blank
                        sysBPFields[i].setText("")
                    } else if (value.toInt() < 0) {
                        // Negative value, show error and color red
                        errorCount += 1
                        sysBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
                        setErrorIcon(sysBPFields[i], R.color.red)
                    } else if (sysBPFields[i].text!!.toString().toInt() == -1) {
                        errorCount += 1
                        sysBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
                    } else if (value.toInt() in 90..179) {
                        // Normal range, no error
                        sysBPFields[i].error = null
                    } else if (value.toInt() in 50..300) {
                        // Out-of-range but acceptable, color-coded orange
                        orangeErrorCount++
                        sysBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_out_of_range_header)
                        setErrorIcon(sysBPFields[i], R.color.orange)
                    } else {
                        // Impossible value, color-coded red
                        errorCount += 1
                        sysBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_impossible_value)
                        setErrorIcon(sysBPFields[i], R.color.red)
                    }
                }

                for (i in 0 until diaBPList.size) {
                    val value = diaBPFields[i].text.toString()

                    if (value.isEmpty() || value.length >= 4) {
                        // Mark as empty with an error, color-coded red
                        errorCount += 1
                        diaBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
                        setErrorIcon(diaBPFields[i], R.color.red)
                    } else if (value.length == 1) {
                        errorCount += 1
                        diaBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_impossible_value)
                        setErrorIcon(diaBPFields[i], R.color.red)
                    } else if (!value.isDigitsOnly()) {
                        // Invalid characters like abc, !@#, set as blank
                        diaBPFields[i].setText("")
                    } else if (value.toInt() < 0) {
                        // Negative value, show error and color red
                        errorCount += 1
                        diaBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
                        setErrorIcon(diaBPFields[i], R.color.red)
                    } else if (diaBPFields[i].text!!.toString().toInt() == -1) {
                        errorCount += 1
                        diaBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
                    } else if (value.toInt() in 50..99) {
                        // Normal range, no error
                        diaBPFields[i].error = null
                    } else if (value.toInt() in 30..180) {
                        // Out-of-range but acceptable, color-coded orange
                        orangeErrorCount++
                        diaBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_out_of_range_header)
                        setErrorIcon(diaBPFields[i], R.color.orange)
                    } else {
                        // Impossible value, color-coded red
                        errorCount += 1
                        diaBPFields[i].error = ResourcesHelper.getString(this, R.string.verify_scan_impossible_value)
                        setErrorIcon(diaBPFields[i], R.color.red)
                    }
                }
            }
            else {
                // Validate current sysBPFields
                for (i in 0 until sysBPList.size) {
                    val index = i + sysBPListHistory.size
                    if (index >= sysBPFields.size) continue
                    val field = sysBPFields[index]
                    Log.d("DEBUG_VALIDATION", "Checking sysBPFields index: $index of size ${sysBPFields.size}")
                    val value = field.text.toString()

                    if (value.isEmpty() || value.length >= 4) {
                        // Mark as empty with an error, color-coded red
                        errorCount += 1
                        field.error = ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
                        setErrorIcon(field, R.color.red)
                    } else if (value.length == 1) {
                        errorCount += 1
                        field.error = ResourcesHelper.getString(this, R.string.verify_scan_impossible_value)
                        setErrorIcon(field, R.color.red)
                    } else if (!value.isDigitsOnly()) {
                        field.setText("")
                    } else if (value.toInt() < 0) {
                        errorCount += 1
                        field.error = ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
                        setErrorIcon(field, R.color.red)
                    } else if (value.toInt() in 90..179) {
                        field.error = null
                        setErrorIcon(field, android.R.color.transparent)
                    } else if (value.toInt() in 50..300) {
                        orangeErrorCount++
                        field.error = ResourcesHelper.getString(this, R.string.verify_scan_out_of_range_header)
                        setErrorIcon(field, R.color.orange)
                    } else {
                        errorCount += 1
                        field.error = ResourcesHelper.getString(this, R.string.verify_scan_impossible_value)
                        setErrorIcon(field, R.color.red)
                    }
                }

                // Validate current diaBPFields
                for (i in 0 until diaBPList.size) {
                    val index = i + diaBPListHistory.size
                    if (index >= diaBPFields.size) continue
                    val field = diaBPFields[index]
                    val value = field.text.toString()

                    if (value.isEmpty() || value.length >= 4) {
                        // Mark as empty with an error, color-coded red
                        errorCount += 1
                        field.error = ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
                        setErrorIcon(field, R.color.red)
                    } else if (value.length == 1) {
                        errorCount += 1
                        field.error = ResourcesHelper.getString(this, R.string.verify_scan_impossible_value)
                        setErrorIcon(field, R.color.red)
                    } else if (!value.isDigitsOnly()) {
                        field.setText("")
                    } else if (value.toInt() < 0) {
                        errorCount += 1
                        field.error = ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
                        setErrorIcon(field, R.color.red)
                    } else if (value.toInt() in 50..99) {
                        field.error = null
                        setErrorIcon(field, android.R.color.transparent)
                    } else if (value.toInt() in 30..180) {
                        orangeErrorCount++
                        field.error = ResourcesHelper.getString(this, R.string.verify_scan_out_of_range_header)
                        setErrorIcon(field, R.color.orange)
                    } else {
                        errorCount += 1
                        field.error = ResourcesHelper.getString(this, R.string.verify_scan_impossible_value)
                        setErrorIcon(field, R.color.red)
                    }
                }

                // Historical Data Validation (without incrementing errorCount)
                sysBPFields.take(sysBPListHistory.size).forEach { field ->
                    val value = field.text.toString()

                    if (value.isEmpty() || value.length >= 4) {
                        // Mark as empty with an error, color-coded red
                        errorCount += 1
                        field.error = ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
                        setErrorIcon(field, R.color.red)
                    } else if (value.length == 1) {
                        errorCount += 1
                        field.error = ResourcesHelper.getString(this, R.string.verify_scan_impossible_value)
                        setErrorIcon(field, R.color.red)
                    } else if (!value.isDigitsOnly()) {
                        field.setText("")
                    } else if (value.toInt() < 0) {
                        field.error = ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
                        setErrorIcon(field, R.color.red)
                    } else if (value.toInt() in 50..300) {
                        field.error = ResourcesHelper.getString(this, R.string.verify_scan_out_of_range_header)
                        setErrorIcon(field, R.color.orange)
                    } else {
                        field.error = ResourcesHelper.getString(this, R.string.verify_scan_impossible_value)
                        setErrorIcon(field, R.color.red)
                    }
                }

                diaBPFields.take(diaBPListHistory.size).forEach { field ->
                    val value = field.text.toString()

                    if (value.isEmpty() || value.length >= 4) {
                        // Mark as empty with an error, color-coded red
                        errorCount += 1
                        field.error = ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
                        setErrorIcon(field, R.color.red)
                    } else if (value.length == 1) {
                        errorCount += 1
                        field.error = ResourcesHelper.getString(this, R.string.verify_scan_impossible_value)
                        setErrorIcon(field, R.color.red)
                    } else if (!value.isDigitsOnly()) {
                        field.setText("")
                    } else if (value.toInt() in 30..180) {
                        orangeErrorCount++
                        field.error = ResourcesHelper.getString(this, R.string.verify_scan_out_of_range_header)
                        setErrorIcon(field, R.color.orange)
                    } else {
                        field.error = ResourcesHelper.getString(this, R.string.verify_scan_impossible_value)
                        setErrorIcon(field, R.color.red)
                    }
                }
            }

        }

        // Recheck for red errors after validation
        errorChecked = errorCount > 0
        Log.d("DEBUG_POST_VALIDATION", "Final errorChecked State: $errorChecked (Red Count: $errorCount, Orange Count: $orangeErrorCount)")

        // If NO red errors remain, make sure to reset errorChecked
        if (errorCount == 0) {
            errorChecked = false
            Log.d("DEBUG_POST_VALIDATION", "All errors cleared. Setting errorChecked = false.")
        }

    }

    private fun scrollToFirstRedSysDiaField() {
        val firstRedField = (sysBPFields + diaBPFields).firstOrNull { field ->
            field.error?.toString()?.let {
                it.contains("Field is empty", true) ||
                        it.contains("Impossible Value", true) ||
                        it.contains("Invalid input", true)
            } == true
        }

        firstRedField?.let { field ->
            field.requestFocus()

            // Post after layout draw to ensure accurate scrolling
            binding.mainScrollView.postDelayed({
                val fieldLocation = IntArray(2)
                field.getLocationInWindow(fieldLocation)

                val scrollViewLocation = IntArray(2)
                binding.mainScrollView.getLocationInWindow(scrollViewLocation)

                val relativeTop = fieldLocation[1] - scrollViewLocation[1]
                val scrollTarget = relativeTop - binding.mainScrollView.height / 3

                binding.mainScrollView.smoothScrollBy(0, scrollTarget)
                Log.d("SCROLL_FIX", "Scrolled to: ${field.hint}, Y=$scrollTarget")
            }, 200) // Delay to wait for dialog/layout to render
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


    private fun validateTargetHomeBP(): Boolean {
        var valid = true

        // Validate target home sys BP on edit
        if (binding.homeTargetSys.text.isNullOrEmpty()) {
            valid = false
            binding.homeTargetSys.error = ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
        } else if (!binding.homeTargetSys.text!!.isDigitsOnly()) {
            valid = false
            binding.homeTargetSys.error = ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
        } else if (binding.homeTargetSys.text.toString().toInt() !in 100..200) {
            valid = false
            binding.homeTargetSys.error = ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
        }

        // Validate target home dia BP on edit
        if (binding.homeTargetDia.text.isNullOrEmpty()) {
            valid = false
            binding.homeTargetDia.error = ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
        } else if (!binding.homeTargetDia.text!!.isDigitsOnly()) {
            valid = false
            binding.homeTargetDia.error = ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
        } else if (binding.homeTargetDia.text.toString().toInt() !in 50..99) {
            valid = false
            binding.homeTargetDia.error = ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
        }

        return valid
    }

    private fun validateTargetClinicBP(): Boolean {
        var valid = true

        // Validate target clinic sys BP on edit
        if (binding.clinicTargetSys.text.isNullOrEmpty()) {
            valid = false
            binding.clinicTargetSys.error = ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
        } else if (!binding.clinicTargetSys.text!!.isDigitsOnly()) {
            valid = false
            binding.clinicTargetSys.error = ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
        } else if (binding.clinicTargetSys.text.toString().toInt() !in 100..200) {
            valid = false
            binding.clinicTargetSys.error = ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
        }

        // Validate target clinic dia BP on edit
        if (binding.clinicTargetDia.text.isNullOrEmpty()) {
            valid = false
            binding.clinicTargetDia.error = ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
        } else if (!binding.clinicTargetDia.text!!.isDigitsOnly()) {
            valid = false
            binding.clinicTargetDia.error = ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
        } else if (binding.clinicTargetDia.text.toString().toInt() !in 50..99) {
            valid = false
            binding.clinicTargetDia.error = ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
        }

        return valid
    }

    private fun createTextWatcher(field: EditText, isSystolic: Boolean): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val value = s.toString()
                if (isSystolic) {
                    validateSysBPField(field, value)
                } else {
                    validateDiaBPField(field, value)
                }
            }
        }
    }


    private fun setupValidation() {

        sysBPFields.forEach { field ->
            field.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val rawValue = s?.toString()?.trim() ?: "" // Get the user input
                    val validatedValue = validateBPValue(rawValue) // Run validateBPValue() first

                    if (validatedValue.isNotEmpty()) {
                        Log.d("DEBUG_SYS_BP", "Typed: $rawValue, Validated: $validatedValue")
                        validateSysBPField(field, validatedValue)
                    } else {
                        field.error = ResourcesHelper.getString(this@VerifyScanActivity, R.string.verify_scan_empty_field)
                        setErrorIcon(field, R.color.red)
                    }

                    // After validating each field:
                    val hasRedErrors = sysBPFields.any {
                        it.error != null && (
                                it.error.toString().contains("empty field", true) ||
                                        it.error.toString().contains("impossible value", true) ||
                                        it.error.toString().contains("invalid input", true)
                                )
                    } || diaBPFields.any {
                        it.error != null && (
                                it.error.toString().contains("empty field", true) ||
                                        it.error.toString().contains("impossible value", true) ||
                                        it.error.toString().contains("invalid input", true)
                                )
                    }

                    errorChecked = hasRedErrors
                    Log.d("DEBUG_LIVE_VALIDATION", "Live update: errorChecked = $errorChecked")

                }

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, nstart: Int, before: Int, count: Int) {}
            })
            setupDynamicValidation(field, isSystolic = true)
        }

        diaBPFields.forEach { field ->
            field.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val rawValue = s?.toString()?.trim() ?: "" // Get the user input
                    val validatedValue = validateBPValue(rawValue) // Run validateBPValue() first

                    if (validatedValue.isNotEmpty()) {
                        validateDiaBPField(field, validatedValue) // Now validate diastolic value
                    } else {
                        field.error = ResourcesHelper.getString(this@VerifyScanActivity, R.string.verify_scan_empty_field)
                        setErrorIcon(field, R.color.red)
                    }

                    // After validating each field:
                    val hasRedErrors = sysBPFields.any {
                        it.error != null && (
                                it.error.toString().contains("empty field", true) ||
                                        it.error.toString().contains("impossible value", true) ||
                                        it.error.toString().contains("invalid input", true)
                                )
                    } || diaBPFields.any {
                        it.error != null && (
                                it.error.toString().contains("empty field", true) ||
                                        it.error.toString().contains("impossible value", true) ||
                                        it.error.toString().contains("invalid input", true)
                                )
                    }

                    errorChecked = hasRedErrors
                    Log.d("DEBUG_LIVE_VALIDATION", "Live update: errorChecked = $errorChecked")

                }

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
            setupDynamicValidation(field, isSystolic = false)
        }
    }


    private fun setupDynamicValidation(field: EditText, isSystolic: Boolean) {
        field.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val value = field.text.toString() // Extract the input value
                if (isSystolic) validateSysBPField(field, value)
                else validateDiaBPField(field, value)
            }
        }

        field.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val value = field.text.toString().trim() // Trim spaces

                if (value.isEmpty()) {
                    field.error = ResourcesHelper.getString(this@VerifyScanActivity, R.string.verify_scan_empty_field)
                    setErrorIcon(field, R.color.red)
                    return
                }

                if (!value.all { it.isDigit() }) { // Ensure only digits
                    field.setText("")
                    return
                }

                val intValue = value.toIntOrNull() ?: return

                if (isSystolic) {
                    validateSysBPField(field, intValue.toString())
                } else {
                    validateDiaBPField(field, intValue.toString())
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun validateSysBPField(field: EditText, value: String): Boolean {
        Log.d("DEBUG_VALIDATE_SYS", "Validating Systolic: $value")

        if (value.isEmpty() || value.length >= 4) {
            field.error = ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
            setErrorIcon(field, R.color.red)
            return false
        } else if (value.length == 1) {
            field.error = ResourcesHelper.getString(this, R.string.verify_scan_impossible_value)
            setErrorIcon(field, R.color.red)
            return false
        }

        if (!value.all { it.isDigit() }) { // Alternative to isDigitsOnly()
            field.setText("")
            return false
        }

        val intValue = value.toIntOrNull() ?: return false

        return when {
            intValue < 0 -> {
                field.error = ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
                setErrorIcon(field, R.color.red)
                false
            }
            intValue in 90..179 -> {
                field.error = null
                setErrorIcon(field, android.R.color.transparent)
                true
            }
            intValue in 50..300 -> {
                field.error = ResourcesHelper.getString(this, R.string.verify_scan_out_of_range_header)
                setErrorIcon(field, R.color.orange)
                true
            }
            else -> {
                field.error = ResourcesHelper.getString(this, R.string.verify_scan_impossible_value)
                setErrorIcon(field, R.color.red)
                false
            }
        }
    }

    private fun validateDiaBPField(field: EditText, value: String): Boolean {
        if (value.isEmpty() || value.length >= 4) {
            field.error = ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
            setErrorIcon(field, R.color.red)
            return false
        } else if (value.length == 1) {
            field.error = ResourcesHelper.getString(this, R.string.verify_scan_impossible_value)
            setErrorIcon(field, R.color.red)
            return false
        }

        if (!value.all { it.isDigit() }) {
            field.setText("")
            return false
        }

        val intValue = value.toIntOrNull() ?: return false

        return when {
            intValue < 0 -> {
                field.error = ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
                setErrorIcon(field, R.color.red)
                false
            }
            intValue in 50..99 -> {
                field.error = null
                setErrorIcon(field, android.R.color.transparent)
                true
            }
            intValue in 30..180 -> {
                field.error = ResourcesHelper.getString(this, R.string.verify_scan_out_of_range_header)
                setErrorIcon(field, R.color.orange)
                true
            }
            else -> {
                field.error = ResourcesHelper.getString(this, R.string.verify_scan_impossible_value)
                setErrorIcon(field, R.color.red)
                false
            }
        }
    }

    private fun validateClinicBPFields(): Boolean {
        var valid = true

        // Validate Systolic BP
        val sysText = binding.verifyClinicSys.text?.toString()?.trim()
        if (sysText.isNullOrEmpty()) {
            valid = false
            binding.verifyClinicSys.error = ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
            setErrorIcon(binding.verifyClinicSys, R.color.red)
        } else if (!sysText.isDigitsOnly()) {
            valid = false
            binding.verifyClinicSys.error = ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
            setErrorIcon(binding.verifyClinicSys, R.color.red)
        } else {
            val sysValue = sysText.toInt()
            if (sysValue !in 90..179) {
                valid = false
                binding.verifyClinicSys.error = ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
                setErrorIcon(binding.verifyClinicSys, R.color.orange)
            } else {
                binding.verifyClinicSys.error = null
                setErrorIcon(binding.verifyClinicSys, android.R.color.transparent)
            }
        }

        // Validate Diastolic BP
        val diaText = binding.verifyClinicDia.text?.toString()?.trim()
        if (diaText.isNullOrEmpty()) {
            valid = false
            binding.verifyClinicDia.error = ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
            setErrorIcon(binding.verifyClinicDia, R.color.red)
        } else if (!diaText.isDigitsOnly()) {
            valid = false
            binding.verifyClinicDia.error = ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
            setErrorIcon(binding.verifyClinicDia, R.color.red)
        } else {
            val diaValue = diaText.toInt()
            if (diaValue !in 50..99) {
                valid = false
                binding.verifyClinicDia.error = ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
                setErrorIcon(binding.verifyClinicDia, R.color.orange)
            } else {
                binding.verifyClinicDia.error = null
                setErrorIcon(binding.verifyClinicDia, android.R.color.transparent)
            }
        }

        // Show error dialog if invalid
        if (!valid) {
            errorDialogBuilder(
                this,
                ResourcesHelper.getString(this, R.string.verify_scan_error_header),
                ResourcesHelper.getString(this, R.string.verify_scan_error_body_clinic_bp)
            )
            scrollToClinicBP()
        }

        return valid
    }



    // auto scroll to clinic bp section
    private fun scrollToClinicBP() {
        binding.mainScrollView.post {
            binding.mainScrollView.smoothScrollTo(0, binding.verifyClinicSys.top)
        }
    }

//
//    private fun validateFields(): Boolean {
//        if (!shouldCheckErrors) return true
//        var valid = true
//
//        // Validate sysBPFields for verifyClinicSys
//        if (binding.verifyClinicSys.text.isNullOrEmpty()) {
//            valid = false
//            binding.verifyClinicSys.error = ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
//        } else if (!binding.verifyClinicSys.text!!.isDigitsOnly()) {
//            valid = false
//            binding.verifyClinicSys.error = ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
//        } else if (binding.verifyClinicSys.text.toString().toInt() !in 90..179) {
//            valid = false
//            binding.verifyClinicSys.error = ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
//        }
//
//        // Validate diaBPFields for verifyClinicDia
//        if (binding.verifyClinicDia.text.isNullOrEmpty()) {
//            valid = false
//            binding.verifyClinicDia.error = ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
//        } else if (!binding.verifyClinicDia.text!!.isDigitsOnly()) {
//            valid = false
//            binding.verifyClinicDia.error = ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
//        } else if (binding.verifyClinicDia.text.toString().toInt() !in 50..99) {
//            valid = false
//            binding.verifyClinicDia.error = ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
//        }
//
//        if (!sevenDay) {
//            // Validate Systolic Fields
//            sysBPFields.forEach { field ->
//                if (field.error != null) valid = false
//            }
//
//            // Validate Diastolic Fields
//            diaBPFields.forEach { field ->
//                if (field.error != null) valid = false
//            }
//        }
////            for (sysField in sysBPFields) {
////                if (sysField.text.isNullOrEmpty()) {
////                    valid = false
////                    sysField.error = ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
////                } else if (!sysField.text!!.isDigitsOnly()) {
////                    valid = false
////                    sysField.error = ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
////                } else if (sysField.text!!.toString().toInt() == -1) {
////                    valid = false
////                    sysField.error = ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
////                } else if (sysField.text!!.length !in 2..3) {
////                    valid = false
////                    sysField.error = ResourcesHelper.getString(this, R.string.verify_scan_invalid_value)
////                } else if (sysField.text.toString().toInt() !in 50..220) {
////                    valid = false
////                    sysField.error = ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
////                }
////            }
////
////            for (diaField in diaBPFields) {
////                if (diaField.text.isNullOrEmpty()) {
////                    valid = false
////                    diaField.error = ResourcesHelper.getString(this, R.string.verify_scan_empty_field)
////                } else if (!diaField.text!!.isDigitsOnly()) {
////                    valid = false
////                    diaField.error = ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
////                } else if (diaField.text!!.toString().toInt() == -1) {
////                    valid = false
////                    diaField.error = ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
////                } else if (diaField.text!!.length !in 2..3) {
////                    valid = false
////                    diaField.error = ResourcesHelper.getString(this, R.string.verify_scan_invalid_value)
////                } else if (diaField.text.toString().toInt() !in 20..160) {
////                    valid = false
////                    diaField.error = ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
////                }
////            }
////        } else {
////            // Validate sysBPFields for sevenDay = true
////            for (sysField in sysBPFields) {
////                val sysText = sysField.text!!.toString()
////                if (sysText.isEmpty()) {
////                    continue
////                } else if (!sysText.isDigitsOnly()) {
////                    valid = false
////                    sysField.error = ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
////                } else if (sysText.toInt() == -1) {
////                    valid = false
////                    sysField.error = ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
////                } else if (sysText.length !in 2..3) {
////                    valid = false
////                    sysField.error = ResourcesHelper.getString(this, R.string.verify_scan_invalid_value)
////                } else if (sysText.toInt() !in 50..220) {
////                    valid = false
////                    sysField.error = ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
////                }
////            }
////
////            for (diaField in diaBPFields) {
////                val diaText = diaField.text!!.toString()
////                if (diaText.isEmpty()) {
////                    continue
////                } else if (!diaText.isDigitsOnly()) {
////                    valid = false
////                    diaField.error = ResourcesHelper.getString(this, R.string.verify_scan_whole_number)
////                } else if (diaText.toInt() == -1) {
////                    valid = false
////                    diaField.error = ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
////                } else if (diaText.length !in 2..3) {
////                    valid = false
////                    diaField.error = ResourcesHelper.getString(this, R.string.verify_scan_invalid_value)
////                } else if (diaText.toInt() !in 20..160) {
////                    valid = false
////                    diaField.error = ResourcesHelper.getString(this, R.string.verify_scan_abnormal_value)
////                }
////            }
////        }
//
//        return valid
//    }

    private fun getBPTarget() {
        homeSysBPTarget = if (binding.verifyHomeTargetSys.text.toString().isEmpty()) {
            0
        } else {
            binding.verifyHomeTargetSys.text.toString().toInt()
        }

        homeDiaBPTarget = if (binding.verifyHomeTargetDia.text.toString().isEmpty()) {
            0
        } else {
            binding.verifyHomeTargetDia.text.toString().toInt()
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

        // Convert the values from sysBPFields and diaBPFields into validated lists
        val sysBPValues = sysBPFields.map { validateBPValue(it.text.toString()) }
        val diaBPValues = diaBPFields.map { validateBPValue(it.text.toString()) }

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
        val incompleteDayReadings = mutableListOf<List<Pair<String, String>>>()
        val dayReadingsStatus = mutableListOf<Int>() // 2 for full day, 1 for incomplete day, 0 for empty day

        var currentDayReadings = mutableListOf<Pair<String, String>>()

        sysBPList = sysBPList.subList(0, 28).toMutableList()
        diaBPList = diaBPList.subList(0, 28).toMutableList()


        // Validate and update sysBPList and diaBPList values
        sysBPList = sysBPList.map { validateBPValue(it).toString() }.toMutableList()
        diaBPList = diaBPList.map { validateBPValue(it).toString() }.toMutableList()

        for (i in sysBPList.indices) {
            if (sysBPList[i].isNotBlank() && sysBPList[i] != "-2" && diaBPList[i].isNotBlank() && diaBPList[i] != "-2") {
                currentDayReadings.add(Pair(sysBPList[i], diaBPList[i]))
            }

            // Each day should have 4 readings (2 morning + 2 evening)
            if (currentDayReadings.size == 4) {
                dayReadings.add(currentDayReadings.toList())
                dayReadingsStatus.add(2)
                currentDayReadings.clear()
            } else if ((i + 1) % 4 == 0) {
                // If it's the end of the day (4 readings) but incomplete
                if (currentDayReadings.isNotEmpty()) {
                    incompleteDayReadings.add(currentDayReadings.toList())
                    dayReadingsStatus.add(1)
                } else if (currentDayReadings.isEmpty()) {
                    dayReadingsStatus.add(0)
                }
                // Needed to locate for validConsecutiveDays
                dayReadings.add(emptyList())
                currentDayReadings.clear()
            }
        }

        // Now check if there are 3 consecutive full days remaining, starting from the last day
        val validConsecutiveDays = mutableListOf<List<Pair<String, String>>>()

        if (dayReadings.isNotEmpty()) {
            dayReadings.removeAt(0)
            dayReadingsStatus.removeAt(0)
        }

        // Clear the validDayIndices before populating
        validDayIndices.clear()

        for (i in dayReadingsStatus.size - 1 downTo 2) {
            // Check for 3 consecutive full days in reverse
            if (dayReadingsStatus[i] == 2 && dayReadingsStatus[i - 1] == 2 && dayReadingsStatus[i - 2] == 2) {
                validConsecutiveDays.add(dayReadings[i])
                validConsecutiveDays.add(dayReadings[i - 1])
                validConsecutiveDays.add(dayReadings[i - 2])
                // Collect indices of all valid days, why +2 for each under? +1 cus day 1 was removed earlier so list size is 6 instead of 7, and index starts with 0 instead of 1 so + another 1
                validDayIndices.add(i + 2)
                validDayIndices.add(i + 1)
                validDayIndices.add(i)

                // Continue checking for more sets of consecutive full days in reverse
                var j = i - 3
                while (j >= 0 && dayReadingsStatus[j] == 2) {
                    validConsecutiveDays.add(dayReadings[j])
                    validDayIndices.add(j + 2)
                    j--
                }
                break
            }
        }

        println("Number of Complete Day Readings: ${dayReadingsStatus.count { it == 2 }}")
        println("Number of Incomplete Day Readings: ${dayReadingsStatus.count { it == 1 }}")
        println("Number of Empty Day Readings: ${dayReadingsStatus.count { it == 0 }}")
        println("Day Readings Status: $dayReadingsStatus")
        println("Number of Valid Consecutive Days: ${validConsecutiveDays.count()}")

        if (validConsecutiveDays.size >= 3) {

            val filteredSysBPList = mutableListOf<String>()
            val filteredDiaBPList = mutableListOf<String>()

            for (day in validConsecutiveDays) {
                if (day.isNotEmpty()) {
                    val (morningSysBP1, morningDiaBP1) = day[0]
                    val (morningSysBP2, morningDiaBP2) = day[1]
                    val (eveningSysBP1, eveningDiaBP1) = day[2]
                    val (eveningSysBP2, eveningDiaBP2) = day[3]

                    println("Processing Day Readings:")
                    println("Morning Readings: $morningSysBP1, $morningDiaBP1; $morningSysBP2, $morningDiaBP2")
                    println("Evening Readings: $eveningSysBP1, $eveningDiaBP1; $eveningSysBP2, $eveningDiaBP2")

                    val chosenMorningSysBP =
                        if (morningSysBP1.toIntOrNull() ?: 0 >= targetHomeSysBP.toInt() || morningDiaBP1.toIntOrNull() ?: 0 >= targetHomeDiaBP.toInt()) {
                            morningSysBP2
                        } else {
                            morningSysBP1
                        }
                    val chosenMorningDiaBP =
                        if (morningSysBP1.toIntOrNull() ?: 0 >= targetHomeSysBP.toInt() || morningDiaBP1.toIntOrNull() ?: 0 >= targetHomeDiaBP.toInt()) {
                            morningDiaBP2
                        } else {
                            morningDiaBP1
                        }

                    val chosenEveningSysBP =
                        if (eveningSysBP1.toIntOrNull() ?: 0 >= targetHomeSysBP.toInt() || eveningDiaBP1.toIntOrNull() ?: 0 >= targetHomeDiaBP.toInt()) {
                            eveningSysBP2
                        } else {
                            eveningSysBP1
                        }
                    val chosenEveningDiaBP =
                        if (eveningSysBP1.toIntOrNull() ?: 0 >= targetHomeSysBP.toInt() || eveningDiaBP1.toIntOrNull() ?: 0 >= targetHomeDiaBP.toInt()) {
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
            }

            val finalSysBPList = filteredSysBPList.toMutableList()
            val finalDiaBPList = filteredDiaBPList.toMutableList()

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

            println("Days used for average calculation: ${validDayIndices.distinct().joinToString(", ")}")
        }
        else if (validConsecutiveDays.size < 3) {

            val SysBPList = mutableListOf<String>()
            val DiaBPList = mutableListOf<String>()

            for (day in dayReadings) {
                if (day.isNotEmpty()) {
                    val (morningSysBP1, morningDiaBP1) = day[0]
                    val (morningSysBP2, morningDiaBP2) = day[1]
                    val (eveningSysBP1, eveningDiaBP1) = day[2]
                    val (eveningSysBP2, eveningDiaBP2) = day[3]

                    SysBPList.add(morningSysBP1)
                    SysBPList.add(morningSysBP2)
                    SysBPList.add(eveningSysBP1)
                    SysBPList.add(eveningSysBP2)

                    DiaBPList.add(morningDiaBP1)
                    DiaBPList.add(morningDiaBP2)
                    DiaBPList.add(eveningDiaBP1)
                    DiaBPList.add(eveningDiaBP2)
                }
            }

            for (day in incompleteDayReadings) {
                for ((sysBP, diaBP) in day) {
                    SysBPList.add(sysBP)
                    DiaBPList.add(diaBP)
                }
            }

            totalSysBP = 0
            totalDiaBP = 0

            for (field in SysBPList) {
                totalSysBP += field.toInt()
            }

            for (field in DiaBPList) {
                totalDiaBP += field.toInt()
            }

            avgSysBP = (totalSysBP.toFloat() / SysBPList.size).roundToInt()
            avgDiaBP = (totalDiaBP.toFloat() / DiaBPList.size).roundToInt()

            println("Days used for average calculation: ${validDayIndices.distinct().joinToString(", ")}")
        }
    }

    private fun sevenDayCheck() {
        // Ensure sysBPList and diaBPList have at least 28 elements
        ensureListSize(sysBPList, 28)
        ensureListSize(diaBPList, 28)

        var recordIndex = 0

        for (day in 1..7) {
            // Morning
            addRow(
                sysBPList.getOrElse(recordIndex) { "-2" },
                diaBPList.getOrElse(recordIndex) { "-2" },
                true,
                day,
                0,
                showHeader = true
            ) // Morning BP1 with header

            addRow(
                sysBPList.getOrElse(recordIndex + 1) { "-2" },
                diaBPList.getOrElse(recordIndex + 1) { "-2" },
                true,
                day,
                0
            ) // Morning BP2

            // Evening
            addRow(
                sysBPList.getOrElse(recordIndex + 2) { "-2" },
                diaBPList.getOrElse(recordIndex + 2) { "-2" },
                true,
                day,
                1,
                showHeader = true
            ) // Evening BP1 with header

            addRow(
                sysBPList.getOrElse(recordIndex + 3) { "-2" },
                diaBPList.getOrElse(recordIndex + 3) { "-2" },
                true,
                day,
                1
            ) // Evening BP2

            recordIndex += 4
        }

        while (recordIndex < sysBPList.size && recordIndex < diaBPList.size) {
            addRow(
                sysBPList.getOrElse(recordIndex) { "-2" },
                diaBPList.getOrElse(recordIndex) { "-2" },
                true,
                0,
                0,
                false
            )
            recordIndex++
        }
    }

    private fun ensureListSize(list: MutableList<String>, targetSize: Int) {
        // Since -1 was used to indicate an error since it was not able to detect a value, -2 will be used to ensure list size
        // So empty blanks to ensure 7 day records will not show up when it is -2, but errors like -1 will still show up
        while (list.size < targetSize) {
            list.add("-2")
        }
    }

    private fun removeExtraRow(){
        if (sevenDay) {
            // Remove rows more than 30 rows that are blank
            // Seven day scan since seven day should only have 28 rows but have 2 more rows in case of data manipulation
            for (index in 29 until sysBPList.size) {
                if (index < sysBPList.size) {
                    if (sysBPList[index] == "" && diaBPList[index] == "") {
                        sysBPList.removeAt(index)
                        diaBPList.removeAt(index)
                    }
                }
            }

            // Remove rows with -2 and empty spaces
            // Could use either sysBPList or diaBPList size since they are theorectically the same
            for (index in 0 until sysBPList.size) {
                if (index < sysBPList.size) {
                    if ((sysBPList[index] == "-2" && diaBPList[index] == "") || (sysBPList[index] == "" && diaBPList[index] == "-2")) {
                        sysBPList.removeAt(index)
                        diaBPList.removeAt(index)
                    }
                }
            }
        }
        else{
            for (index in 0 until sysBPList.size) {
                if (index < sysBPList.size) {
                    if (sysBPList[index] == "" && diaBPList[index] == "") {
                        sysBPList.removeAt(index)
                        diaBPList.removeAt(index)
                    }
                }
            }
            for (index in 0 until sysBPListHistory.size) {
                if (index < sysBPListHistory.size) {
                    if (sysBPListHistory[index] == "" && diaBPListHistory[index] == "") {
                        sysBPListHistory.removeAt(index)
                        diaBPListHistory.removeAt(index)
                    }
                }
            }
        }
    }

    // Combine and split lists for "Stepping"
    private fun shiftStepping(list1: MutableList<String>, list2: MutableList<String>, type: String, index: Int){
        val combinedNumbers = mutableListOf<String>()
        val newSysBPList = mutableListOf<String>()
        val newDiaBPList = mutableListOf<String>()

        // Theoretically size should be the same so use either but just to play safe just make a check
        while (list1.size != list2.size) {
            if (list1.size > list2.size) {
                list2.add("")
            } else {
                list1.add("")
            }
        }

        for (listIndex in 0 until list1.size){
            combinedNumbers.add(list1[listIndex])
            combinedNumbers.add(list2[listIndex])
        }

        // Add " " in combined list then split
        if (type == "sys" || type == "sysHistory"){
            combinedNumbers.add(index * 2, "")
        } else if (type == "dia" || type == "diaHistory"){
            combinedNumbers.add(index * 2 + 1, "")
        }

        combinedNumbers.forEachIndexed { index, value ->
            if (index % 2 == 0) {
                newSysBPList.add(value)
            } else {
                newDiaBPList.add(value)
            }
        }

        if (type == "sysHistory" || type == "diaHistory" ) {
            sysBPListHistory = newSysBPList
            diaBPListHistory = newDiaBPList
        }
        else if (type == "sys" || type == "dia") {
            sysBPList = newSysBPList
            diaBPList = newDiaBPList
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
        var sysUndoState = true
        var diaUndoState = true

        if (isSevenDayCheck) {
            if ((sysBP == null || sysBP == "-2") && (diaBP == null || diaBP == "-2")) {
                bpRowContainer.visibility = View.GONE
            } else {
                bpRowContainer.visibility = View.VISIBLE
                addOneRowBtn.visibility = View.GONE
                sysBPTIET.setText(validateBPValue(sysBP))
                diaBPTIET.setText(validateBPValue(diaBP))
            }
            if (day != -1 && time != -1 && showHeader) {
                headerRowContainer.visibility = View.VISIBLE
                dayTV.text = if (time == 0) "Day $day - Morning" else "Day $day - Evening"
            } else {
                dayTV.visibility = View.GONE
            }
        } else {
            bpRowContainer.visibility = View.VISIBLE
            sysBPTIET.setText(validateBPValue(sysBP))
            diaBPTIET.setText(validateBPValue(diaBP))
            if (sysBP == null && diaBP == null) {
                sysBPList.add("")
                diaBPList.add("")
            }
        }

        // Add TextWatchers to update the lists
        sysBPTIET.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (sysUndoState && !isSwappingValues) {
                    saveStateForUndo()
                    sysUndoState = false
                }
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                println("SysBP Text Changed: $s")
                val index = sysBPFields.indexOf(sysBPTIET)
                if (index != -1) {
                    if(!sevenDay){
                        if (sysBPListHistory.isNotEmpty()){
                            if (index >= sysBPListHistory.size){
                                val newIndex = index - sysBPListHistory.size
                                sysBPList[newIndex] = s.toString()
                            }
                            else{
                                sysBPListHistory[index] = s.toString()
                            }
                        }
                        else{
                            sysBPList[index] = s.toString()
                        }
                    }
                    else{
                        sysBPList[index] = s.toString()
                    }
                }
            }

            override fun afterTextChanged(s: Editable?) {
                // Runnable is to help with undo as it would give before user time to finish typing the number before saving state for undo
                // If user tries to go to 1 but change too slow like 180 and then remove 0 and stays at 18 for 1000ms before going to 1, will save 18 and not 180
                // Goal is to save value before they tried to change so above would be worse if delay is faster
                typingRunnable?.let { typingDelayHandler.removeCallbacks(it) }
                typingRunnable = Runnable {
                    sysUndoState = true
                }
                typingDelayHandler.postDelayed(typingRunnable!!, 1000)
                // 500ms delay - recommended by older batches could be shorter but might affect it saving before fully typing, I recommend 1000
                // Longer delay or shorter which one better? - Note: won't affect if user just change this box and then go to another, below only affects if retype
                // If too fast, affects saving value before finish typing, if too slow, might not even saved the multiple retype value if they choose to come back and type before the delay
            }
        })

        diaBPTIET.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (diaUndoState && !isSwappingValues) {
                    saveStateForUndo()
                    diaUndoState = false
                }
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                println("DiaBP Text Changed: $s")
                val index = diaBPFields.indexOf(diaBPTIET)
                if (index != -1) {
                    if(!sevenDay){
                        if (diaBPListHistory.isNotEmpty()){
                            if (index >= diaBPListHistory.size){
                                val newIndex = index - diaBPListHistory.size
                                diaBPList[newIndex] = s.toString()
                            }
                            else{
                                diaBPListHistory[index] = s.toString()
                            }
                        }
                        else{
                            diaBPList[index] = s.toString()
                        }
                    }
                    else{
                        diaBPList[index] = s.toString()
                    }
                }
            }

            override fun afterTextChanged(s: Editable?) {
                typingRunnable?.let { typingDelayHandler.removeCallbacks(it) }
                typingRunnable = Runnable {
                    diaUndoState = true
                }
                typingDelayHandler.postDelayed(typingRunnable!!, 1000)
            }
        })

        // Add to fields lists
        sysBPFields.add(sysBPTIET)
        diaBPFields.add(diaBPTIET)

        val swapValuesIV = rowBPRecordLayout.findViewById<View>(R.id.swapValuesIV) as ImageView
        swapValuesIV.setOnClickListener {
            saveStateForUndo()
            println("Swapping values...")

            // Since swap already calls undo, disable textwatcher so it does not call undo again when text changed
            isSwappingValues = true

            val tempValue = sysBPTIET.text.toString()
            sysBPTIET.setText(diaBPTIET.text.toString())
            diaBPTIET.setText(tempValue)

            isSwappingValues = false

            binding.rowBPRecordLL.removeAllViews()
            sysBPFields.clear()
            diaBPFields.clear()
            refreshViews()

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
                    println("Removing row...")

                    // Print lists before removal
                    println("sysBPList before removal: $sysBPList")
                    println("diaBPList before removal: $diaBPList")

                    val diaIndex = diaBPFields.indexOf(diaBPTIET)
                    val sysIndex = sysBPFields.indexOf(sysBPTIET)
                    if (!sevenDay) {
                        // Since both would be the same length theorectically, either could be used for checks
                        if (sysBPListHistory.isNotEmpty() && diaBPListHistory.isNotEmpty()){
                            // The index would probaly be the same but just to standardise with the previous batches codes, I used 2 indexes
                            if (sysIndex >= sysBPListHistory.size){
                                val newSysIndex = sysIndex - sysBPListHistory.size
                                val newDiaIndex = diaIndex - diaBPListHistory.size
                                diaBPList.removeAt(newDiaIndex)
                                sysBPList.removeAt(newSysIndex)
                            }
                            else{
                                diaBPListHistory.removeAt(diaIndex)
                                sysBPListHistory.removeAt(sysIndex)
                            }
                        }
                        else{
                            diaBPList.removeAt(diaIndex)
                            sysBPList.removeAt(sysIndex)
                        }
                    } else {
                        diaBPList.removeAt(diaIndex)
                        sysBPList.removeAt(sysIndex)
                    }
                    sysBPFields.remove(sysBPTIET)
                    diaBPFields.remove(diaBPTIET)

                    binding.rowBPRecordLL.removeAllViews()
                    sysBPFields.clear()
                    diaBPFields.clear()
                    refreshViews()

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
            println("Adding row... (BUTTON)")

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
            println("Adding row... ")

            if (!sevenDay) {
                val currentRowIndex = binding.rowBPRecordLL.indexOfChild(rowBPRecordLayout)
                if (sysBPListHistory.isNotEmpty() && diaBPListHistory.isNotEmpty()){
                    // Could use either sysBPListHistory or diaBPListHistory since their size would be the same theoratically
                    // Why -1? cus divider to seperate old records and new records is considered as a child of rowBPRecordLayout
                    if (currentRowIndex >= sysBPListHistory.size){
                        val newCurrentRowIndex = currentRowIndex - sysBPListHistory.size - 1
                        sysBPList.add(newCurrentRowIndex + 1, "")
                        diaBPList.add(newCurrentRowIndex + 1, "")
                    } else{
                        sysBPListHistory.add(currentRowIndex + 1, "")
                        diaBPListHistory.add(currentRowIndex + 1, "")
                    }
                }else {
                    sysBPList.add(currentRowIndex + 1, "")
                    diaBPList.add(currentRowIndex + 1, "")
                }

                println("addRowIV clicked. Current Row Index: $currentRowIndex")
                println("sysBPList before modification: $sysBPList\n")
                println("diaBPList before modification: $diaBPList\n")
            } else {
                val currentRowIndex = binding.rowBPRecordLL.indexOfChild(rowBPRecordLayout)
                val nextRowIndex = currentRowIndex + 1

                println("addRowIV clicked. Current Row Index: $currentRowIndex")
                println("sysBPList before modification: $sysBPList\n")
                println("diaBPList before modification: $diaBPList\n")

                // Check if the next row index is within bounds
                if (nextRowIndex < binding.rowBPRecordLL.childCount) {
                    // Get the next row from the LinearLayout
                    val nextRowLayout = binding.rowBPRecordLL.getChildAt(nextRowIndex)

                    // Find the TextInputEditText elements in the next row
                    val nextSysBPTIET =
                        nextRowLayout.findViewById<TextInputEditText>(R.id.sysBPTIET)
                    val nextDiaBPTIET =
                        nextRowLayout.findViewById<TextInputEditText>(R.id.diaBPTIET)

                    if (nextSysBPTIET.text.toString() != "-2" && nextDiaBPTIET.text.toString() != "-2") {
                        sysBPList.add(currentRowIndex + 1, "")
                        diaBPList.add(currentRowIndex + 1, "")
                    } else {
                        sysBPList[currentRowIndex + 1] = ""
                        diaBPList[currentRowIndex + 1] = ""
                    }
                } else {
                    sysBPList.add(currentRowIndex + 1, "")
                    diaBPList.add(currentRowIndex + 1, "")
                }
                println("sysBPList after modification: $sysBPList\n")
                println("diaBPList after modification: $diaBPList\n")

            }

            // Refresh the view
            binding.rowBPRecordLL.removeAllViews()
            sysBPFields.clear()
            diaBPFields.clear()
            refreshViews()

            val toast = Toast.makeText(this, "Row added", Toast.LENGTH_SHORT)
            toast.show()

        }

        val sysLeftIV = rowBPRecordLayout.findViewById<View>(R.id.sysLeftIV) as ImageView
        sysLeftIV.setOnClickListener {
            saveStateForUndo()
            val currentRowIndex = binding.rowBPRecordLL.indexOfChild(rowBPRecordLayout)

            if (!sevenDay) {
                if (sysBPListHistory.isNotEmpty()) {
                    if (currentRowIndex >= sysBPListHistory.size) {
                        val newCurrentRowIndex = currentRowIndex - sysBPListHistory.size - 1
                        // Pushes Down
                        sysBPList.add(newCurrentRowIndex, "")
                        // Step diagonally
//                        shiftStepping(sysBPList, diaBPList, "sys", newCurrentRowIndex)

                        while (diaBPList.size != sysBPList.size) {
                            if (diaBPList.size > sysBPList.size) {
                                sysBPList.add("")
                            } else {
                                diaBPList.add("")
                            }
                        }
                    }
                    else{
                        sysBPListHistory.add(currentRowIndex, "")
//                        shiftStepping(sysBPListHistory, diaBPListHistory, "sysHistory", currentRowIndex)

                        while (diaBPListHistory.size != sysBPListHistory.size) {
                            if (diaBPListHistory.size > sysBPListHistory.size) {
                                sysBPListHistory.add("")
                            } else {
                                diaBPListHistory.add("")
                            }
                        }
                    }
                }
                else {
                    sysBPList.add(currentRowIndex, "")
//                    shiftStepping(sysBPList, diaBPList, "sys", currentRowIndex)

                    while (diaBPList.size != sysBPList.size) {
                        if (diaBPList.size > sysBPList.size) {
                            sysBPList.add("")
                        } else {
                            diaBPList.add("")
                        }
                    }
                }
            }
            else {
                sysBPList.add(currentRowIndex, "")
//                shiftStepping(sysBPList, diaBPList, "sys", currentRowIndex)

                while (diaBPList.size != sysBPList.size) {
                    if (diaBPList.size > sysBPList.size) {
                        sysBPList.add("")
                    } else {
                        diaBPList.add("")
                    }
                }

                // Remove the hidden -2 and replace it with a space when any value shifted
                for (index in 0 until diaBPList.size){
                    if (index < diaBPList.size) {
                        if (sysBPList[index].toIntOrNull() != null) {
                            if (diaBPList[index] == "-2" && sysBPList[index].toInt() > 0) {
                                diaBPList[index] = ""
                            }
                        }
                    }
                }
            }

            // Remove extra rows from shifting left columns
            removeExtraRow()

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

            if (!sevenDay) {
                if (diaBPListHistory.isNotEmpty()) {
                    if (currentRowIndex >= diaBPListHistory.size) {
                        val newCurrentRowIndex = currentRowIndex - diaBPListHistory.size - 1
                        diaBPList.add(newCurrentRowIndex, "")
//                        shiftStepping(sysBPList, diaBPList, "dia", newCurrentRowIndex)

                        while (diaBPList.size != sysBPList.size) {
                            if (diaBPList.size > sysBPList.size) {
                                sysBPList.add("")
                            } else {
                                diaBPList.add("")
                            }
                        }
                    }
                    else{
                        diaBPListHistory.add(currentRowIndex, "")
//                        shiftStepping(sysBPListHistory, diaBPListHistory, "diaHistory", currentRowIndex)

                        while (diaBPListHistory.size != sysBPListHistory.size) {
                            if (diaBPListHistory.size > sysBPListHistory.size) {
                                sysBPListHistory.add("")
                            } else {
                                diaBPListHistory.add("")
                            }
                        }
                    }
                }
                else {
                    diaBPList.add(currentRowIndex, "")
//                    shiftStepping(sysBPList, diaBPList, "dia", currentRowIndex)

                    while (diaBPList.size != sysBPList.size) {
                        if (diaBPList.size > sysBPList.size) {
                            sysBPList.add("")
                        } else {
                            diaBPList.add("")
                        }
                    }
                }
            }
            else {
                diaBPList.add(currentRowIndex, "")
//                shiftStepping(sysBPList, diaBPList, "dia", currentRowIndex)

                while (diaBPList.size != sysBPList.size) {
                    if (diaBPList.size > sysBPList.size) {
                        sysBPList.add("")
                    } else {
                        diaBPList.add("")
                    }
                }

                for (index in 0 until sysBPList.size){
                    if (index < sysBPList.size) {
                        if (diaBPList[index].toIntOrNull() != null) {
                            if (sysBPList[index] == "-2" && diaBPList[index].toInt() > 0) {
                                sysBPList[index] = ""
                            }
                        }
                    }
                }
            }

            removeExtraRow()

            binding.rowBPRecordLL.removeAllViews()
            sysBPFields.clear()
            diaBPFields.clear()
            refreshViews()

            val toast = Toast.makeText(this, "Empty Diastolic Added", Toast.LENGTH_SHORT)
            toast.show()
        }

        binding.rowBPRecordLL.addView(rowBPRecordLayout)
    }

    private fun validateBPValue(value: String?): String {
        if (value == "|||" || value == "!!!" || value == "III") {
            return "111"
        }
        return value?.toIntOrNull()?.toString() ?: ""
    }

    private fun addDivider() {
        val dividerOldNewRecordLayout =
            layoutInflater.inflate(R.layout.divider_old_new_record, null, false)
        binding.rowBPRecordLL.addView(dividerOldNewRecordLayout)
    }

    // Update the home BP target
    private fun updateHomeBP() {
        if (validateTargetHomeBP()) {
            progressDialog.setTitle(getString(R.string.verify_scan_update_data))
            progressDialog.setMessage(getString(R.string.verify_scan_update_data_caption))
            progressDialog.show()

            val docRef = db.collection("patients").document(patientID)

            docRef.get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        if (binding.homeTargetSys.text.toString().toInt() != homeSysBPTarget || binding.homeTargetDia.text.toString().toInt() != homeDiaBPTarget) {
                            // Update db to store recent target home BP
                            val data = hashMapOf("targetHomeSys" to AESEncryption().encrypt(binding.homeTargetSys.text.toString()),
                                "targetHomeDia" to AESEncryption().encrypt(binding.homeTargetDia.text.toString()),)
                            docRef.set(data, SetOptions.merge())

                            // Updates UI to hide options and show view
                            binding.editHomeBPLayout.visibility = View.VISIBLE
                            binding.verifyEditHomeBPTarget.visibility = View.GONE
                            binding.verifyEditHomeBPTextFields.visibility = View.GONE
                            binding.homeBPTargetTV.visibility = View.VISIBLE

                            // Updates components
                            homeSysBPTarget = binding.homeTargetSys.text.toString().toInt()
                            homeDiaBPTarget = binding.homeTargetDia.text.toString().toInt()

                            // Updates UI to show updated value
                            binding.verifyHomeTargetSys.text = homeSysBPTarget.toString()
                            binding.verifyHomeTargetDia.text = homeDiaBPTarget.toString()

                            // Updates hidden view to save data into firebase when clicked on 7 day calc
                            binding.homeBPTargetTV.text = String.format(
                                "%s / %s",
                                homeSysBPTarget.toString(),
                                homeDiaBPTarget.toString()
                            )

                            // Updates the values used for comparison in 7 day calc
                            targetHomeSysBP = homeSysBPTarget.toString()
                            targetHomeDiaBP = homeDiaBPTarget.toString()

                            // Updates the values stored in secured shared preferences
                            SecureSharedPreferences.getSharedPreferences(applicationContext)
                                .edit()
                                .putString("targetHomeSysBP", targetHomeSysBP)
                                .putString("targetHomeDiaBP", targetHomeDiaBP)
                                .apply()

                            progressDialog.dismiss()
                        }
                        else{
                            binding.editHomeBPLayout.visibility = View.VISIBLE
                            binding.verifyEditHomeBPTarget.visibility = View.GONE
                            binding.verifyEditHomeBPTextFields.visibility = View.GONE
                            binding.homeBPTargetTV.visibility = View.VISIBLE
                            progressDialog.dismiss()
                            noChangesToBPTarget()
                        }
                    }
                    else{
                        progressDialog.dismiss()
                        errorDialogBuilder(this, getString(R.string.update_bp_error_header), getString(R.string.update_bp_error_body), MainActivity::class.java)
                    }
                }
                .addOnFailureListener { e ->
                    progressDialog.dismiss()
                    firebaseErrorDialog(this, e, docRef)
                }
        }
    }

    // Update the clinic BP target
    private fun updateClinicBP(){
        if (validateTargetClinicBP()) {
            progressDialog.setTitle(getString(R.string.verify_scan_update_data))
            progressDialog.setMessage(getString(R.string.verify_scan_update_data_caption))
            progressDialog.show()

            val docRef = db.collection("patients").document(patientID)

            docRef.get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        if (binding.clinicTargetSys.text.toString().toInt() != clinicSysBPTarget || binding.clinicTargetDia.text.toString().toInt() != clinicDiaBPTarget) {
                            // Update db to store recent target home BP
                            val data = hashMapOf("targetClinicSys" to AESEncryption().encrypt(binding.clinicTargetSys.text.toString()),
                                "targetClinicDia" to AESEncryption().encrypt(binding.clinicTargetDia.text.toString()),)
                            docRef.set(data, SetOptions.merge())

                            binding.editClinicBPLayout.visibility = View.VISIBLE
                            binding.verifyEditClinicBPTarget.visibility = View.GONE
                            binding.verifyEditClinicBPTextFields.visibility = View.GONE
                            binding.clinicBPTargetTV.visibility = View.VISIBLE

                            clinicSysBPTarget = binding.clinicTargetSys.text.toString().toInt()
                            clinicDiaBPTarget = binding.clinicTargetDia.text.toString().toInt()

                            binding.verifyClinicTargetSys.text = clinicSysBPTarget.toString()
                            binding.verifyClinicTargetDia.text = clinicDiaBPTarget.toString()

                            binding.clinicBPTargetTV.text = String.format(
                                "%s / %s",
                                clinicSysBPTarget.toString(),
                                clinicDiaBPTarget.toString()
                            )

                            targetClinicSysBP = clinicSysBPTarget.toString()
                            targetClinicDiaBP = clinicDiaBPTarget.toString()

                            SecureSharedPreferences.getSharedPreferences(applicationContext)
                                .edit()
                                .putString("targetClinicSysBP", targetClinicSysBP)
                                .putString("targetClinicDiaBP", targetClinicDiaBP)
                                .apply()

                            progressDialog.dismiss()
                        }
                        else{
                            binding.editClinicBPLayout.visibility = View.VISIBLE
                            binding.verifyEditClinicBPTarget.visibility = View.GONE
                            binding.verifyEditClinicBPTextFields.visibility = View.GONE
                            binding.clinicBPTargetTV.visibility = View.VISIBLE
                            progressDialog.dismiss()
                            noChangesToBPTarget()
                        }
                    }
                    else{
                        progressDialog.dismiss()
                        errorDialogBuilder(this, getString(R.string.update_bp_error_header), getString(R.string.update_bp_error_body), MainActivity::class.java)
                    }
                }
                .addOnFailureListener { e ->
                    progressDialog.dismiss()
                    firebaseErrorDialog(this, e, docRef)
                }
        }
    }

    // Discard home BP changes from editing
    private fun discardChangesHomeBP() {
        binding.editHomeBPLayout.visibility = View.VISIBLE
        binding.verifyEditHomeBPTarget.visibility = View.GONE
        binding.verifyEditHomeBPTextFields.visibility = View.GONE
        binding.homeBPTargetTV.visibility = View.VISIBLE
    }

    // Discard clinic BP changes from editing
    private fun discardChangesClinicBP() {
        binding.editClinicBPLayout.visibility = View.VISIBLE
        binding.verifyEditClinicBPTarget.visibility = View.GONE
        binding.verifyEditClinicBPTextFields.visibility = View.GONE
        binding.clinicBPTargetTV.visibility = View.VISIBLE
    }

    // Method to show the home BP target update confirmation dialog
    private fun showUpdateHomeBPTargetConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.verify_scan_update_target_home_header))
            .setMessage(getString(R.string.verify_scan_update_target_home_body))
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton(getString(R.string.update)) { dialog, _ ->
                dialog.dismiss()
                updateHomeBP()
            }
            .show()
    }

    // Method to show the home BP target exit confirmation dialog
    private fun showExitHomeBPTargetConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.verify_scan_exit_target_home_header))
            .setMessage(getString(R.string.verify_scan_exit_target_home_body))
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton(getString(R.string.exit)) { dialog, _ ->
                dialog.dismiss()
                discardChangesHomeBP()
            }
            .show()
    }

    // Method to show the clinic BP target update confirmation dialog
    private fun showUpdateClinicBPTargetConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.verify_scan_update_target_clinic_header))
            .setMessage(getString(R.string.verify_scan_update_target_clinic_body))
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton(getString(R.string.update)) { dialog, _ ->
                dialog.dismiss()
                updateClinicBP()
            }
            .show()
    }

    // Method to show the clinic BP target exit confirmation dialog
    private fun showExitClinicBPTargetConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.verify_scan_exit_target_clinic_header))
            .setMessage(getString(R.string.verify_scan_exit_target_clinic_body))
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton(getString(R.string.exit)) { dialog, _ ->
                dialog.dismiss()
                discardChangesClinicBP()
            }
            .show()
    }

    // Method to show no updates were made dialog
    private fun noChangesToBPTarget() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.verify_scan_no_update))
            .setMessage(getString(R.string.verify_scan_no_update_caption))
            .setNeutralButton(getString(R.string.ok)){ dialog, _ ->
                dialog.dismiss()
            }
            .show()
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