package com.singhealth.enhance.activities.ocr

import android.Manifest
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Rect
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
import com.google.firebase.ml.vision.document.FirebaseVisionCloudDocumentRecognizerOptions
import com.google.firebase.ml.vision.document.FirebaseVisionDocumentText
import com.singhealth.enhance.R
import com.singhealth.enhance.activities.MainActivity
import com.singhealth.enhance.activities.dashboard.SimpleDashboardActivity
import com.singhealth.enhance.activities.history.HistoryActivity
import com.singhealth.enhance.activities.patient.ProfileActivity
import com.singhealth.enhance.activities.patient.RegistrationActivity
import com.singhealth.enhance.activities.settings.SettingsActivity
import com.singhealth.enhance.activities.validation.internetConnectionCheck
import com.singhealth.enhance.activities.validation.ocrTextErrorDialog
import com.singhealth.enhance.activities.validation.patientNotFoundInSessionErrorDialog
import com.singhealth.enhance.databinding.ActivityScanBinding
import com.singhealth.enhance.security.AESEncryption
import com.singhealth.enhance.security.SecureSharedPreferences
import kotlin.math.abs
import kotlin.math.max

class ScanActivity : AppCompatActivity() {
    private lateinit var binding: ActivityScanBinding
    private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle
    private lateinit var cameraPermissions: Array<String>
    private lateinit var storagePermissions: Array<String>
    private lateinit var progressDialog: ProgressDialog
    private lateinit var outputUri: Uri
    private lateinit var patientID: String
    private lateinit var orientation: String
    private var boundingBox: Rect = Rect(0,0,0,0)
    private var sevenDay: Boolean = false
    private var showContinueScan: Boolean = false
    private var clinicSysBP: String? = null
    private var clinicDiaBP: String? = null
    private var direction: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        internetConnectionCheck(this)

        setupNavigationDrawer()
        setupBottomNavigationView()
        checkPatientInfo()

        cameraPermissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        storagePermissions = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        progressDialog = ProgressDialog(this)
        progressDialog.setCanceledOnTouchOutside(false)

        if (intent.extras != null) {
            showContinueScan = intent.extras?.getBoolean("showContinueScan") ?: false
            if (showContinueScan) {
                binding.scanStatusContainer.visibility = View.VISIBLE
                binding.sevenDaySourceBtn.visibility = View.GONE
                binding.spaceBetweenButtons.visibility = View.GONE
            }
        }

        binding.generalSourceBtn.setOnClickListener {
            sevenDay = false
            onClickRequestPermission()
        }
        binding.sevenDaySourceBtn.setOnClickListener {
            sevenDay = true
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
                    skipEditing = true,
                ),
            ),
        )
    }

    private val customCropImage = registerForActivityResult(CropImageContract()) {
        if (it !is CropImage.CancelledResult) {
            handleCropImageResultForAutocrop(it.uriContent.toString())
        }
    }

    private fun handleCropImageResultForAutocrop(uri: String) {
        outputUri = Uri.parse(uri.replace("file:", ""))
        contentResolver.openInputStream(outputUri)?.use { inputStream ->
            val bitmap = BitmapFactory.decodeStream(inputStream)
            orientation = if (bitmap.width > bitmap.height){
                "Horizontal"
            } else{
                "Vertical"
            }
        }

        if (outputUri.toString().isNotEmpty()) {
            processDocumentImageForAutoCrop()
        } else {
            Toast.makeText(this, "ERROR: No image to process.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processDocumentImageForAutoCrop() {
        progressDialog.setTitle("Processing image")
        progressDialog.setMessage("Please wait a moment...")
        progressDialog.show()

        val options = FirebaseVisionCloudDocumentRecognizerOptions.Builder()
            .setLanguageHints(listOf("kr")) // Set language hints if needed
            .build()

        val ocrEngine = FirebaseVision.getInstance().getCloudDocumentTextRecognizer(options)
        val imageToProcess = FirebaseVisionImage.fromFilePath(this, outputUri)

        ocrEngine.processImage(imageToProcess)
            .addOnSuccessListener { firebaseVisionDocumentText ->
                // Used here to detect words for the auto crop box
                extractWordsFromBlocks(firebaseVisionDocumentText.blocks)
                startCameraWithUri()
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                ocrTextErrorDialog(this)
            }
    }

    private fun startCameraWithUri() {
        println("Bounding Box Used $boundingBox")
        if (boundingBox != Rect(0,0,0,0)) {
            customCroppedImage.launch(
                CropImageContractOptions(
                    uri = outputUri,
                    cropImageOptions = CropImageOptions(
                        initialCropWindowRectangle = boundingBox,
                    ),
                ),
            )
        } else{
            customCroppedImage.launch(
                CropImageContractOptions(
                    uri = outputUri,
                    cropImageOptions = CropImageOptions(
                    ),
                ),
            )
        }
    }

    private val customCroppedImage = registerForActivityResult(CropImageContract()) {
        if (it !is CropImage.CancelledResult) {
            handleCropImageResult(it.uriContent.toString())
        }
        else{
            binding.cropIV.visibility = View.GONE
            binding.ocrInstructionsTextViewValue.visibility = View.VISIBLE
            binding.scanStatusTextView.visibility = View.VISIBLE
            progressDialog.dismiss()
        }
    }

    private fun handleCropImageResult(uri: String) {
        outputUri = Uri.parse(uri.replace("file:", "")).also { parsedUri ->
            binding.cropIV.visibility = View.VISIBLE
            binding.cropIV.setImageUriAsync(parsedUri)
            binding.ocrInstructionsTextViewValue.visibility = View.GONE
            binding.scanStatusTextView.visibility = View.GONE
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

        val options = FirebaseVisionCloudDocumentRecognizerOptions.Builder()
            .setLanguageHints(listOf("kr")) // Set language hints if needed
            .build()

        val ocrEngine = FirebaseVision.getInstance().getCloudDocumentTextRecognizer(options)
        val imageToProcess = FirebaseVisionImage.fromFilePath(this, outputUri)

        ocrEngine.processImage(imageToProcess)
            .addOnSuccessListener { firebaseVisionDocumentText ->
                processDocumentTextBlock(firebaseVisionDocumentText)
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                ocrTextErrorDialog(this)
            }
    }

    // New Algorithm
    private fun processDocumentTextBlock(result: FirebaseVisionDocumentText) {
        val sysBPList = mutableListOf<String>()
        val diaBPList = mutableListOf<String>()
        val blocks = result.blocks

        if (blocks.isEmpty()) {
            progressDialog.dismiss()
            ocrTextErrorDialog(this)
        } else {
            // Return list of words from the function
            val words = extractWordsFromBlocks(blocks)
            println("Scanned words:")
            words.forEachIndexed { index, word ->
                println("Word ${index + 1}: ${word.text}")
            }

            val numbers = words.map { it.text }
                .filter{ it.lowercase() !in "systolic" && it.lowercase() !in "diastolic" && it.lowercase() !in "pulse" && it.lowercase() !in "morning" && it.lowercase() !in "evening" && it.lowercase() !in "1st" && it.lowercase() !in "2nd" && it.length in 2..3}
                .toMutableList()
            println("Filtered numbers: $numbers")
            processNumbers(numbers, sysBPList, diaBPList)
            println("sysBPList after fixing common errors: $sysBPList")
            println("diaBPList after fixing common errors: $diaBPList")

            navigateToVerifyScanActivity(sysBPList, diaBPList, sevenDay)
        }
    }

    private fun extractWordsFromBlocks(blocks: List<FirebaseVisionDocumentText.Block>): MutableList<FirebaseVisionDocumentText.Word> {
        val words = mutableListOf<FirebaseVisionDocumentText.Word>()
        var totalCount = 0
        var searchNextBoundingBox = false
        var firstBoundingBox = Rect(0, 0, 0, 0)
        var secondBoundingBox = Rect(0, 0, 0, 0)
        var leftGap: Int? = null
        var topGap: Int? = null
        var rightGap: Int? = null
        var bottomGap: Int? = null
        for (block in blocks) {
            var accumulatedWords = ""
            totalCount += 1
            println("Bounding block number $totalCount")
            for (paragraph in block.paragraphs) {
                // Used to detect words from cropping and for processDocumentTextBlocks
                words.addAll(paragraph.words)

                // Used for auto cropping by detection of words
                for (word in paragraph.words) {
                    println(word.text)
                    if (word.text == "Systolic") {
                        println("Bounding Box ${block.boundingBox} with Systolic")
                        firstBoundingBox = block.boundingBox!!
                    } else if (word.text == "Diastolic") {
                        println("Bounding Box ${block.boundingBox} with Diastolic")
                        secondBoundingBox = block.boundingBox!!
                    }

                    // Search clinicBP in this and next bounding Box, flow must be in this order of sequence or would not detect
                    if (searchNextBoundingBox){
                        val targetClinicBP = word.text.split("/").toTypedArray()
                        if (targetClinicBP.size == 2) {
                            clinicSysBP = targetClinicBP[0]
                            clinicDiaBP = targetClinicBP[1]
                            println("Clinic BP detected ${word.text}")
                        }
                        searchNextBoundingBox = false
                    }
                    if (accumulatedWords == "Clinic/OfficeBP"){
                        searchNextBoundingBox = true
                    }
                    if (accumulatedWords == "Clinic/OfficeBP:"){
                        val targetClinicBP = word.text.split("/").toTypedArray()
                        if (targetClinicBP.size == 2) {
                            clinicSysBP = targetClinicBP[0]
                            clinicDiaBP = targetClinicBP[1]
                            searchNextBoundingBox = false
                        }
                        println("Clinic BP detected ${word.text}")
                    }
                    accumulatedWords += word.text

                    // Get word list for each day to detect if there are missing values
                }
            }

            // Get the last coordinates for all the different orientations
            if (leftGap == null){
                leftGap = block.boundingBox!!.left - 30
                println("First Left:$leftGap")
            }
            else if (block.boundingBox!!.left < leftGap){
                leftGap = block.boundingBox!!.left - 30
                println("New Left:$leftGap")

            }
            if (topGap == null){
                topGap = block.boundingBox!!.top - 30
                println("First Top:$topGap")
            }
            else if (block.boundingBox!!.top < topGap){
                topGap = block.boundingBox!!.top - 30
                println("New Top:$topGap")

            }
            if (rightGap == null){
                rightGap = block.boundingBox!!.right + 30
                println("First Right:$rightGap")
            }
            else if (block.boundingBox!!.right > rightGap){
                rightGap = block.boundingBox!!.right + 30
                println("New Right:$rightGap")

            }
            if (bottomGap == null){
                bottomGap = block.boundingBox!!.bottom + 30
                println("First Bottom:$bottomGap")
            }
            else if (block.boundingBox!!.bottom > bottomGap){
                bottomGap = block.boundingBox!!.bottom + 30
                println("New Bottom:$bottomGap")

            }
        }

        // Comparison to detect the direction
        if (firstBoundingBox != Rect(0, 0, 0, 0) && secondBoundingBox != Rect(0, 0, 0, 0)) {
            println("Orientation:$orientation")
            // Comparison in regards to orientation of image flaws: could be vertical picture taken in landscape
//            if (orientation == "Vertical"){
//                if (firstBoundingBox.left < secondBoundingBox.left) {
//                    direction = "Top"
//                } else if (firstBoundingBox.left > secondBoundingBox.left) {
//                    direction = "Down"
//                }
//            } else if (orientation == "Horizontal") {
//                if (firstBoundingBox.top > secondBoundingBox.top) {
//                    direction = "Left"
//                } else if (firstBoundingBox.top < secondBoundingBox.left) {
//                    direction = "Right"
//                }
//            }

            // Comparison in regards to difference in distances flaws: uses hardcoded values which means the difference changes depending on how near/far its taken
            // Further the image, the difference will be lesser
            if (firstBoundingBox.top > secondBoundingBox.top && abs(firstBoundingBox.top - secondBoundingBox.top) > 180){
                direction = "Left"
            } else if (firstBoundingBox.left < secondBoundingBox.left && abs(firstBoundingBox.left - secondBoundingBox.left) > 180){
                direction = "Top"
            } else if (firstBoundingBox.top < secondBoundingBox.top && abs(firstBoundingBox.top - secondBoundingBox.top) > 180){
                direction = "Right"
            } else if (firstBoundingBox.left > secondBoundingBox.left && abs(firstBoundingBox.left - secondBoundingBox.left) > 180){
                direction = "Down"
            }
        }

        // Setting the bounding box to be used for the autocrop library
        if (firstBoundingBox != Rect(0, 0, 0, 0) && secondBoundingBox != Rect(0, 0, 0, 0)){
            println("Direction:$direction")
            // Autocrop if not rotated
            if (direction == "Top") {
                if (firstBoundingBox == secondBoundingBox) {
                    val left = firstBoundingBox.left - 100
                    val top = firstBoundingBox.top
                    val right = firstBoundingBox.right + 100
                    if (bottomGap != null) {
                        boundingBox.set(left, top, right, bottomGap)
                    } else {
                        boundingBox.set(left, top, right, 0)
                    }
                } else {
                    val left = firstBoundingBox.left - 100
                    val top = max(firstBoundingBox.top, secondBoundingBox.top)
                    val right = secondBoundingBox.right + 100
                    if (bottomGap != null) {
                        boundingBox.set(left, top, right, bottomGap)
                    } else {
                        boundingBox.set(left, top, right, 0)
                    }
                }
            }
            // Autocrop box if horizontal-right
            else if (direction == "Right") {
                if (firstBoundingBox == secondBoundingBox) {
                    val top = firstBoundingBox.top - 100
                    val right = firstBoundingBox.right
                    val bottom = firstBoundingBox.bottom + 100
                    if (leftGap != null) {
                        boundingBox.set(leftGap, top, right, bottom)
                    } else {
                        boundingBox.set(0, top, right, 0)
                    }
                } else {
                    val top = firstBoundingBox.top - 100
                    val right = max(firstBoundingBox.right, secondBoundingBox.right)
                    val bottom = secondBoundingBox.bottom + 100
                    if (leftGap != null) {
                        boundingBox.set(leftGap, top, right, bottom)
                    } else {
                        boundingBox.set(0, top, right, bottom)
                    }
                }
            }
            // Autocrop box if upside down
            else if (direction == "Down") {
                if (firstBoundingBox == secondBoundingBox) {
                    val left = firstBoundingBox.left - 100
                    val right = firstBoundingBox.right + 100
                    val bottom = firstBoundingBox.bottom
                    if (topGap != null) {
                        boundingBox.set(left, topGap, right, bottom)
                    } else {
                        boundingBox.set(left, 0, right, 0)
                    }
                } else {
                    val left = secondBoundingBox.left - 100
                    val right = firstBoundingBox.right + 100
                    val bottom = max(firstBoundingBox.bottom, secondBoundingBox.bottom)
                    if (topGap != null) {
                        boundingBox.set(left, topGap, right, bottom)
                    } else {
                        boundingBox.set(left, 0, right, 0)
                    }
                }
            }
            // Autocrop box if horizontal-left
            else if (direction == "Left") {
                if (firstBoundingBox == secondBoundingBox) {
                    val left = firstBoundingBox.left
                    val top = firstBoundingBox.top - 100
                    val bottom = firstBoundingBox.bottom + 100
                    if (rightGap != null) {
                        boundingBox.set(left, top, rightGap, bottom)
                    } else {
                        boundingBox.set(left, top, 0, bottom)
                    }
                } else {
                    val left = max(firstBoundingBox.left, secondBoundingBox.left)
                    val top = secondBoundingBox.top - 100
                    val bottom = firstBoundingBox.bottom + 100
                    if (rightGap != null) {
                        boundingBox.set(left, top, rightGap, bottom)
                    } else {
                        boundingBox.set(left, top, 0, bottom)
                    }
                }
            }
        } else{
            boundingBox.set(0,0,0,0)
        }
        return words
    }

    private fun processNumbers(
        numbers: List<String>,
        sysBPList: MutableList<String>,
        diaBPList: MutableList<String>
    ) {
        var correctedNumbers = mutableListOf<String>()

        // Smart Algorithm
        var i = 0
        while (i < numbers.size) {
            val systolic = numbers.getOrNull(i)
            val diastolic = numbers.getOrNull(i + 1)

            if (systolic != null && diastolic != null) {
                if (systolic.toIntOrNull() != null && diastolic.toIntOrNull() != null){
                    if (systolic.toInt() in 50..220 && diastolic.toInt() in 20..160) {
                        if (diastolic.toInt() > systolic.toInt()) {
                            correctedNumbers.add(diastolic)
                            correctedNumbers.add(systolic)
                        } else {
                            correctedNumbers.add(systolic)
                            correctedNumbers.add(diastolic)
                        }
                    } else if (systolic.toInt() in 20..160 && diastolic.toInt() in 50..220) {
                        correctedNumbers.add(diastolic)
                        correctedNumbers.add(systolic)
                    } else {
                        correctedNumbers.add(systolic)
                        correctedNumbers.add(diastolic)
                    }
                }
                else{
                    correctedNumbers.add(systolic)
                    correctedNumbers.add(diastolic)
                }
            } else if (systolic != null) {
                correctedNumbers.add(systolic)
                correctedNumbers.add("-1")
            } else if (diastolic != null) {
                correctedNumbers.add("-1")
                correctedNumbers.add(diastolic)
            }

            i += 2
        }

        // "Dumb Algorithm" Remove algorithm on top
//        correctedNumbers = numbers.toMutableList()

        if (correctedNumbers.size % 2 != 0) {
            correctedNumbers.add("-1")
        }

        correctedNumbers.forEachIndexed { index, value ->
            if (index % 2 == 0) {
                sysBPList.add(value)
            } else {
                diaBPList.add(value)
            }
        }
    }

    private fun navigateToVerifyScanActivity(sysBPList: MutableList<String>, diaBPList: MutableList<String>, sevenDay: Boolean) {
        val bundle = Bundle().apply {
            putStringArrayList("sysBPList", ArrayList(sysBPList))
            putStringArrayList("diaBPList", ArrayList(diaBPList))
            if (clinicSysBP != null && clinicDiaBP != null) {
                putString("clinicSysBP", clinicSysBP)
                putString("clinicDiaBP", clinicDiaBP)
            }
            putBoolean("sevenDay", sevenDay)
            putBoolean("showProgressDialog", true)
            intent.extras?.let {
                it.getString("homeSysBPTarget")?.let { target -> putString("homeSysBPTarget", target) }
                it.getString("homeDiaBPTarget")?.let { target -> putString("homeDiaBPTarget", target) }
                it.getString("clinicSysBPTarget")?.let { target -> putString("clinicSysBPTarget", target) }
                it.getString("clinicDiaBPTarget")?.let { target -> putString("clinicDiaBPTarget", target) }
                it.getString("clinicSysBP")?.let { target -> putString("clinicSysBP", target) }
                it.getString("clinicDiaBP")?.let { target -> putString("clinicDiaBP", target) }
                it.getStringArrayList("sysBPListHistory")?.let{ target -> putStringArrayList("sysBPListHistory", target) }
                it.getStringArrayList("diaBPListHistory")?.let{ target -> putStringArrayList("diaBPListHistory", target) }
            }
        }

        val verifyScanIntent = Intent(this, VerifyScanActivity::class.java).apply { putExtras(bundle) }
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
        else {
            onClickRequestPermission()
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

// Old Scan Algorithm
//    private fun startCameraWithoutUri(includeCamera: Boolean, includeGallery: Boolean) {
//        customCropImage.launch(
//            CropImageContractOptions(
//                uri = null,
//                cropImageOptions = CropImageOptions(
//                    imageSourceIncludeCamera = includeCamera,
//                    imageSourceIncludeGallery = includeGallery,
//                ),
//            ),
//        )
//    }
//
//    private val customCropImage = registerForActivityResult(CropImageContract()) {
//        if (it !is CropImage.CancelledResult) {
//            handleCropImageResult(it.uriContent.toString())
//        }
//    }
//
//    private fun handleCropImageResult(uri: String) {
//        outputUri = Uri.parse(uri.replace("file:", "")).also { parsedUri ->
//            binding.cropIV.setImageUriAsync(parsedUri)
//            binding.ocrInstructionsTextViewValue.visibility = View.GONE
//            binding.scanStatusTextView.visibility = View.GONE
//        }
//
//        if (outputUri.toString().isNotEmpty()) {
//            processDocumentImage()
//        } else {
//            Toast.makeText(this, "ERROR: No image to process.", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//    private fun processDocumentImage() {
//        progressDialog.setTitle("Processing image")
//        progressDialog.setMessage("Please wait a moment...")
//        progressDialog.show()
//
//        val options = FirebaseVisionCloudDocumentRecognizerOptions.Builder()
//            .setLanguageHints(listOf("kr")) // Set language hints if needed
//            .build()
//
//        val ocrEngine = FirebaseVision.getInstance().getCloudDocumentTextRecognizer(options)
//        val imageToProcess = FirebaseVisionImage.fromFilePath(this, outputUri)
//
//        ocrEngine.processImage(imageToProcess)
//            .addOnSuccessListener { firebaseVisionDocumentText ->
//                processDocumentTextBlock(firebaseVisionDocumentText)
//            }
//            .addOnFailureListener { e ->
//                progressDialog.dismiss()
//                ocrTextErrorDialog(this, )
//            }
//    }


// Old Algorithm
//    private fun processDocumentTextBlock(result: FirebaseVisionDocumentText) {
//        val sysBPList = mutableListOf<String>()
//        val diaBPList = mutableListOf<String>()
//        val blocks = result.blocks
//
//        if (blocks.isEmpty()) {
//            progressDialog.dismiss()
//            ocrTextErrorDialog(this)
//        } else {
//            val words = extractWordsFromBlocks(blocks)
//            println("Scanned words:")
//            words.forEachIndexed { index, word ->
//                println("Word ${index + 1}: ${word.text}")
//            }
//
//            val numbers = words.map { it.text }
//                .asSequence()
//                .filter { it != "*" && it != "7" && it != "07" && it != "8" }
//                .map {
//                    when (it) {
//                        "Sis", "Eis", "Su", "Eu", "fir" -> "84"
//                        "14" -> "121"
//                        "10" -> "70"
//                        "16" -> "116"
//                        "1/6" -> "116"
//                        "T17" -> "117"
//                        "+5" -> "75"
//                        "+9" -> "79"
//                        "TIT", "川", "!!!", "|||" -> "111"
//                        "734" -> "134"
//                        "13T" -> "131"
//                        "9/2" -> "92"
//                        else -> it.replace(Regex("[^\\d]"), "")
//                    }
//                }
//                .map { it.replace(Regex("(\\d*)T(\\d*)"), "1$1$2") } // No idea what this does essentially 203T3045 will beccome 12033045
//                .filter { it.matches(Regex("\\d+")) && it.toInt() in 20..220 }
//                .map { it.toInt() }
//                .toMutableList()
//            println("Filtered numbers: $numbers")
//            processNumbers(numbers, sysBPList, diaBPList)
//            fixCommonErrors(sysBPList, diaBPList)
//            println("sysBPList after fixing common errors: $sysBPList")
//            println("diaBPList after fixing common errors: $diaBPList")
//
//            navigateToVerifyScanActivity(sysBPList, diaBPList, sevenDay)
//        }
//    }
//
//    private fun extractWordsFromBlocks(blocks: List<FirebaseVisionDocumentText.Block>): MutableList<FirebaseVisionDocumentText.Word> {
//        val words = mutableListOf<FirebaseVisionDocumentText.Word>()
//        for (block in blocks) {
//            for (paragraph in block.paragraphs) {
//                words.addAll(paragraph.words)
//            }
//        }
//
//        println("Words before sorting by bounding box:")
//        words.forEachIndexed { index, word ->
//            println("Word ${index + 1}: ${word.text}")
//        }
//
//        return words
//    }

//    private fun processNumbers(
//        numbers: List<Int>,
//        sysBPList: MutableList<String>,
//        diaBPList: MutableList<String>
//    ) {
//        val correctedNumbers = mutableListOf<Int>()
//
//        var i = 0
//        while (i < numbers.size) {
//            val systolic = numbers.getOrNull(i)
//            val diastolic = numbers.getOrNull(i + 1)
//
//            if (systolic != null && diastolic != null) {
//                if (systolic in 50..220 && diastolic in 20..160) {
//                    if (diastolic > systolic) {
//                        correctedNumbers.add(diastolic)
//                        correctedNumbers.add(systolic)
//                    } else {
//                        correctedNumbers.add(systolic)
//                        correctedNumbers.add(diastolic)
//                    }
//                } else if (systolic in 20..160 && diastolic in 50..220) {
//                    correctedNumbers.add(diastolic)
//                    correctedNumbers.add(systolic)
//                } else if (systolic !in 50..220) {
//                    correctedNumbers.add(-1)
//                    correctedNumbers.add(diastolic)
//                } else if (diastolic !in 20..160) {
//                    correctedNumbers.add(systolic)
//                    correctedNumbers.add(-1)
//                } else {
//                    correctedNumbers.add(-1)
//                    correctedNumbers.add(-1)
//                }
//            } else if (systolic != null) {
//                correctedNumbers.add(systolic)
//                correctedNumbers.add(-1)
//            } else if (diastolic != null) {
//                correctedNumbers.add(-1)
//                correctedNumbers.add(diastolic)
//            }
//
//            i += 2
//        }
//
//        if (correctedNumbers.size % 2 != 0) {
//            correctedNumbers.add(-1)
//        }
//
//        correctedNumbers.forEachIndexed { index, value ->
//            if (index % 2 == 0) {
//                sysBPList.add(value.toString())
//            } else {
//                diaBPList.add(value.toString())
//            }
//        }
//    }
//
//    private fun fixCommonErrors(sysBPList: MutableList<String>, diaBPList: MutableList<String>) {
//        for (i in 0 until maxOf(sysBPList.size, diaBPList.size)) {
//            sysBPList[i] = sysBPList[i].replace("기", "71").replace("ㄱㄱ", "77").replace("ㄱㅇ", "70").replace("סר", "70").replace("סך", "70").replace(" ", "")
//            diaBPList[i] = diaBPList[i].replace("기", "71").replace("ㄱㄱ", "77").replace("ㄱㅇ", "70").replace("סר", "70").replace("סך", "70").replace(" ", "")
//        }
//    }
