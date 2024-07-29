package com.singhealth.enhance.activities.ocr

import android.Manifest
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.document.FirebaseVisionDocumentText
import com.singhealth.enhance.R
import com.singhealth.enhance.activities.MainActivity
import com.singhealth.enhance.activities.dashboard.SimpleDashboardActivity
import com.singhealth.enhance.activities.error.internetConnectionCheck
import com.singhealth.enhance.activities.error.ocrTextErrorDialog
import com.singhealth.enhance.activities.error.patientNotFoundInSessionErrorDialog
import com.singhealth.enhance.activities.history.HistoryActivity
import com.singhealth.enhance.activities.patient.ProfileActivity
import com.singhealth.enhance.activities.patient.RegistrationActivity
import com.singhealth.enhance.activities.settings.SettingsActivity
import com.singhealth.enhance.databinding.ActivityScanBinding
import com.singhealth.enhance.security.AESEncryption
import com.singhealth.enhance.security.SecureSharedPreferences

class ScanActivity : AppCompatActivity() {
    private lateinit var binding: ActivityScanBinding
    private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle
    private lateinit var cameraPermissions: Array<String>
    private lateinit var storagePermissions: Array<String>
    private lateinit var progressDialog: ProgressDialog
    private lateinit var outputUri: Uri
    private lateinit var patientID: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        internetConnectionCheck(this)

        setupNavigationDrawer()
        setupBottomNavigationView()
        checkPatientInfo()

        binding.ocrInstructionsTextViewValue.text = getString(R.string.ocr_instructions)
        cameraPermissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        storagePermissions = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        progressDialog = ProgressDialog(this)
        progressDialog.setCanceledOnTouchOutside(false)

        binding.generalSourceBtn.setOnClickListener {
            onClickRequestPermission()
        }
    }

    private fun setupNavigationDrawer() {
        actionBarDrawerToggle = ActionBarDrawerToggle(this, binding.drawerLayout, 0, 0)
        binding.drawerLayout.addDrawerListener(actionBarDrawerToggle)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        actionBarDrawerToggle.syncState()

        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.item_home -> {
                    navigateTo(MainActivity::class.java)
                    true
                }
                R.id.item_patient_registration -> {
                    navigateTo(RegistrationActivity::class.java)
                    true
                }
                R.id.item_settings -> {
                    navigateTo(SettingsActivity::class.java)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupBottomNavigationView() {
        binding.bottomNavigationView.selectedItemId = R.id.item_scan
        binding.bottomNavigationView.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.item_profile -> {
                    navigateTo(ProfileActivity::class.java)
                    false
                }
                R.id.item_scan -> true
                R.id.item_history -> {
                    navigateTo(HistoryActivity::class.java)
                    false
                }
                R.id.item_dashboard -> {
                    navigateTo(SimpleDashboardActivity::class.java)
                    false
                }
                else -> false
            }
        }
    }

    private fun checkPatientInfo() {
        val patientSharedPreferences = SecureSharedPreferences.getSharedPreferences(applicationContext)
        if (patientSharedPreferences.getString("patientID", null).isNullOrEmpty()) {
            patientNotFoundInSessionErrorDialog(this)
        } else {
            patientID = patientSharedPreferences.getString("patientID", null).toString()
            binding.patientIdValueTextView.text = AESEncryption().decrypt(patientID)
            binding.patientNameValueTextView.text = patientSharedPreferences.getString("legalName", null).toString()
        }
    }

    private fun onClickRequestPermission() {
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) -> {
                startCameraWithoutUri(includeCamera = true, includeGallery = true)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCameraWithoutUri(includeCamera: Boolean, includeGallery: Boolean) {
        customCropImage.launch(
            CropImageContractOptions(
                uri = null,
                cropImageOptions = CropImageOptions(
                    imageSourceIncludeCamera = includeCamera,
                    imageSourceIncludeGallery = includeGallery,
                ),
            ),
        )
    }

    private val customCropImage = registerForActivityResult(CropImageContract()) {
        if (it !is CropImage.CancelledResult) {
            handleCropImageResult(it.uriContent.toString())
        }
    }

    private fun handleCropImageResult(uri: String) {
        outputUri = Uri.parse(uri.replace("file:", "")).also { parsedUri ->
            binding.cropIV.setImageUriAsync(parsedUri)
            binding.ocrInstructionsTextViewValue.visibility = View.GONE
        }

        if (outputUri.toString().isNotEmpty()) {
            processDocumentImage()
        } else {
            Toast.makeText(this, "ERROR: No image to process.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processDocumentImage() {
        progressDialog.setTitle("Processing image")
        progressDialog.setMessage("Please wait a moment...")
        progressDialog.show()

        val ocrEngine = FirebaseVision.getInstance().cloudDocumentTextRecognizer
        val imageToProcess = FirebaseVisionImage.fromFilePath(this, outputUri)

        ocrEngine.processImage(imageToProcess)
            .addOnSuccessListener { firebaseVisionDocumentText ->
                processDocumentTextBlock(firebaseVisionDocumentText)
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                ocrTextErrorDialog(this, )
            }
    }

    private fun processDocumentTextBlock(result: FirebaseVisionDocumentText) {
        val sysBPList = mutableListOf<String>()
        val diaBPList = mutableListOf<String>()
        val blocks = result.blocks

        if (blocks.isEmpty()) {
            progressDialog.dismiss()
            ocrTextErrorDialog(this)
        } else {
            val words = extractWordsFromBlocks(blocks)
            println("Scanned words:" )
            words.forEachIndexed { index, word ->
                println("Word ${index + 1}: ${word.text}")
            }

            val numbers = words.map { it.text }
                .asSequence()
                .filter { it != "*" && it != "7" && it != "07" && it != "8" }
                .map {
                    when (it) {
                        "Sis", "Eis", "Su" -> "84"
                        "14" -> "121"
                        "10" -> "70"
                        "16" -> "116"
                        "1/6" -> "116"
                        else -> it.replace(Regex("[^\\d]"), "")
                    }
                }
                .filter { it.matches(Regex("\\d+")) && it.toInt() in 40..210 }
                .map { it.toInt() }
                .toMutableList()

            println("Filtered numbers: $numbers")
            processNumbers(numbers, sysBPList, diaBPList)
            fixCommonErrors(sysBPList, diaBPList)
            println("sysBPList after fixing common errors: $sysBPList")
            println("diaBPList after fixing common errors: $diaBPList")

            navigateToVerifyScanActivity(sysBPList, diaBPList)
        }
    }

    private fun extractWordsFromBlocks(blocks: List<FirebaseVisionDocumentText.Block>): MutableList<FirebaseVisionDocumentText.Word> {
        val words = mutableListOf<FirebaseVisionDocumentText.Word>()
        for (block in blocks) {
            for (paragraph in block.paragraphs) {
                words.addAll(paragraph.words)
            }
        }

        println("Words before sorting by bounding box:")
        words.forEachIndexed { index, word ->
            println("Word ${index + 1}: ${word.text}")
        }

/*        words.sortWith(compareBy({ it.boundingBox?.top }, { it.boundingBox?.left }))

        println("Words after sorting by bounding box:")
        words.forEachIndexed { index, word ->
            println("Word ${index + 1}: ${word.text}")
        }*/

        return words
    }

    private fun processNumbers(
        numbers: List<Int>,
        sysBPList: MutableList<String>,
        diaBPList: MutableList<String>
    ) {
        val correctedNumbers = mutableListOf<Int>()

        for (i in numbers.indices step 2) {
            val systolic = numbers.getOrNull(i)
            val diastolic = numbers.getOrNull(i + 1)

            if (systolic != null && diastolic != null) {
                if (systolic in 100..210 && diastolic in 40..100) {
                    correctedNumbers.add(systolic)
                    correctedNumbers.add(diastolic)
                } else if (systolic in 40..100 && diastolic in 100..210) {
                    correctedNumbers.add(diastolic)
                    correctedNumbers.add(systolic)
                } else if (systolic in 40..100) {
                    correctedNumbers.add(999)
                    correctedNumbers.add(systolic)
                } else if (diastolic in 120..200) {
                    correctedNumbers.add(systolic)
                    correctedNumbers.add(999)
                } else {
                    correctedNumbers.add(999)
                    correctedNumbers.add(999)
                }
            } else if (systolic != null) {
                correctedNumbers.add(systolic)
                correctedNumbers.add(999)
            } else if (diastolic != null) {
                correctedNumbers.add(999)
                correctedNumbers.add(diastolic)
            }
        }

        if (correctedNumbers.size % 2 != 0) {
            correctedNumbers.add(999)
        }

        correctedNumbers.forEachIndexed { index, value ->
            if (index % 2 == 0) {
                sysBPList.add(value.toString())
            } else {
                diaBPList.add(value.toString())
            }
        }
    }

    private fun fixCommonErrors(sysBPList: MutableList<String>, diaBPList: MutableList<String>) {
        for (i in 0 until maxOf(sysBPList.size, diaBPList.size)) {
            sysBPList[i] = sysBPList[i].replace("기", "71").replace("ㄱㄱ", "77").replace("ㄱㅇ", "70").replace("סר", "70").replace("סך", "70").replace(" ", "")
            diaBPList[i] = diaBPList[i].replace("기", "71").replace("ㄱㄱ", "77").replace("ㄱㅇ", "70").replace("סר", "70").replace("סך", "70").replace(" ", "")
        }
    }

    private fun navigateToVerifyScanActivity(sysBPList: MutableList<String>, diaBPList: MutableList<String>) {
        val bundle = Bundle().apply {
            putStringArrayList("sysBPList", ArrayList(sysBPList))
            putStringArrayList("diaBPList", ArrayList(diaBPList))
            intent.extras?.let {
                it.getString("homeSysBPTarget")?.let { target -> putString("homeSysBPTarget", target) }
                it.getString("homeDiaBPTarget")?.let { target -> putString("homeDiaBPTarget", target) }
                it.getString("clinicSysBPTarget")?.let { target -> putString("clinicSysBPTarget", target) }
                it.getString("clinicDiaBPTarget")?.let { target -> putString("clinicDiaBPTarget", target) }
                putStringArrayList("sysBPListHistory", it.getStringArrayList("sysBPListHistory"))
                putStringArrayList("diaBPListHistory", it.getStringArrayList("diaBPListHistory"))
            }
        }

        val verifyScanIntent = Intent(this, VerifyScanActivity::class.java).apply { putExtras(bundle) }
        progressDialog.dismiss()
        startActivity(verifyScanIntent)
        finish()
    }


    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (!isGranted) {
            MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_error)
                .setTitle(getString(R.string.ocr_app_permissions_header))
                .setMessage(getString(R.string.ocr_app_permissions_body))
                .setPositiveButton(getString(R.string.ok_dialog)) { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (actionBarDrawerToggle.onOptionsItemSelected(item)) {
            true
        } else super.onOptionsItemSelected(item)
    }

    private fun navigateTo(activityClass: Class<*>) {
        startActivity(Intent(this, activityClass))
        finish()
    }
}
