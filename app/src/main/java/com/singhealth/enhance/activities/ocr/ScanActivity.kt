package com.singhealth.enhance.activities.ocr

import android.Manifest
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
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
import com.google.firebase.auth.auth
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.document.FirebaseVisionCloudDocumentRecognizerOptions
import com.google.firebase.ml.vision.document.FirebaseVisionDocumentText
import com.singhealth.enhance.R
import com.singhealth.enhance.activities.MainActivity
import com.singhealth.enhance.activities.authentication.LoginActivity
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
import com.singhealth.enhance.security.LogOutTimerUtil
import com.singhealth.enhance.security.SecureSharedPreferences
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ScanActivity : AppCompatActivity(), LogOutTimerUtil.LogOutListener {
    private lateinit var binding: ActivityScanBinding
    private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle
    private lateinit var cameraPermissions: Array<String>
    private lateinit var storagePermissions: Array<String>
    private lateinit var progressDialog: ProgressDialog
    private lateinit var outputUri: Uri
    private lateinit var patientID: String
    private var boundingBox: Rect = Rect(0,0,0,0)
    private var sevenDay: Boolean = false
    private var showContinueScan: Boolean = false
    private var clinicSysBP: String? = null
    private var clinicDiaBP: String? = null
    private var direction: String = ""
    private var currentDayReadings = mutableListOf<String>()
    private var allDayReadings = mutableListOf<String>()
    private var ranFirstTime: Boolean = false
    private var allDayReadingsFinal = mutableListOf<String>()

    // Used for Session Timeout do not include onUserInteraction as it might log user out when they are scanning

    override fun doLogout() {
        com.google.firebase.Firebase.auth.signOut()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

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

        binding.generalSourceBtnDev.setOnClickListener {
            sevenDay = false
            onClickRequestPermission()
        }
        binding.generalSourceBtn.setOnClickListener {
            sevenDay = false
            onClickRequestPermissionNoGallery()
        }
        binding.sevenDaySourceBtnDev.setOnClickListener {
            sevenDay = true
            onClickRequestPermission()
        }
        binding.sevenDaySourceBtn.setOnClickListener {
            sevenDay = true
            onClickRequestPermissionNoGallery()
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
//            binding.patientNameValueTextView.text = patientSharedPreferences.getString("legalName", null).toString()
        }
    }

    private fun onClickRequestPermission() {
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) -> {
                LogOutTimerUtil.stopLogoutTimer()
                startCameraWithoutUri(includeCamera = true, includeGallery = true)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun onClickRequestPermissionNoGallery() {
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) -> {
                LogOutTimerUtil.stopLogoutTimer()
                startCameraWithoutUri(includeCamera = true, includeGallery = false)
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
        LogOutTimerUtil.startLogoutTimer(this, this)
        if (it !is CropImage.CancelledResult) {
            handleCropImageResultForAutocrop(it.uriContent.toString())
        }
    }

    private fun handleCropImageResultForAutocrop(uri: String) {
        outputUri = Uri.parse(uri.replace("file:", ""))

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
            .setLanguageHints(listOf("ko")) // Set language hints if needed
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
            ranFirstTime = false
            allDayReadings.clear()
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
        var notConsecutiveBlanks = true
        var savedCurrentIndex = 0
        var lastSavedIndex = 0
        var useSavedCurrentIndex = false
        var numberOfTimesConsecutiveBlanksSpotted = 0
        var numberOfBlanksAccumulated = 0
        var savedNumberOfBlanks = 0
        var blanksBeenSaved = false
        var indexChanged = false
        var listIsNotEmptyStrings = false

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

            // Compare numbers list with populated list that includes empty values
            splitAllDayReadings()
            println("All Day Readings Final (before comparison): $allDayReadingsFinal")

            for (i in allDayReadingsFinal.indices) {
                if (i < allDayReadingsFinal.size) {
                    if (allDayReadingsFinal[i] == "") {
                        if (!blanksBeenSaved) {
                            savedNumberOfBlanks = numberOfBlanksAccumulated
                            blanksBeenSaved = true
                        }
                        if (notConsecutiveBlanks) {
                            savedCurrentIndex = i
                            numberOfTimesConsecutiveBlanksSpotted += 1
                            println("A new index has been saved $savedCurrentIndex")
                        }
                        indexChanged = false
                        numberOfBlanksAccumulated += 1
                        notConsecutiveBlanks = false
                        useSavedCurrentIndex = true
                    }
                    if (allDayReadingsFinal[i] != "") {
                        notConsecutiveBlanks = true
                        blanksBeenSaved = false
                        listIsNotEmptyStrings = true
                        if (useSavedCurrentIndex) {
                            if (numberOfTimesConsecutiveBlanksSpotted > 1 && !indexChanged) {
                                savedCurrentIndex -= savedNumberOfBlanks
                                indexChanged = true
                                println("New saved index $savedCurrentIndex")
                            }
                            if (savedCurrentIndex < numbers.size) {
                                if (allDayReadingsFinal[i] != numbers[savedCurrentIndex]) {
                                    println("A change has happened after blanks ${numbers[savedCurrentIndex]}")
                                    allDayReadingsFinal[i] = numbers[savedCurrentIndex].toString()
                                }
                                lastSavedIndex = savedCurrentIndex
                                savedCurrentIndex += 1
                            } else {
                                // Remove every value after since all of numbers been replaced
                                allDayReadingsFinal = allDayReadingsFinal.subList(0, i)
                            }
                        } else {
                            if (i < numbers.size) {
                                if (allDayReadingsFinal[i] != numbers[i]) {
                                    println("A change has happened before blanks ${numbers[i]}")
                                    allDayReadingsFinal[i] = numbers[i].toString()
                                }
                                lastSavedIndex = i
                            } else {
                                allDayReadingsFinal = allDayReadingsFinal.subList(0, i)
                            }
                        }
                    }
                }
            }

//            println("Numbers List: $numbers")
            println("All Day Readings Final (after comparison): $allDayReadingsFinal")

            lastSavedIndex += 1
            println("Fill up index $lastSavedIndex")
            println("Numbers size ${numbers.size}")

            //  Add remaining numbers not added into allDayReadingsFinal
            if (lastSavedIndex < numbers.size && lastSavedIndex != 1) {
                while (lastSavedIndex != numbers.size) {
                    println("Numbers added")
                    allDayReadingsFinal.add(numbers[lastSavedIndex])
                    lastSavedIndex += 1
                }
            }

            println("All Day Readings Final after filling up $allDayReadingsFinal")

            // Use allDayReadings if there are empty blanks
            if (allDayReadingsFinal.size > numbers.size && listIsNotEmptyStrings) {
                processNumbers(allDayReadingsFinal, sysBPList, diaBPList)
                println("Used list allDayReadings")
            } else {
                // Defaults to numberlist if both have equal length
                processNumbers(numbers, sysBPList, diaBPList)
                println("Used list numbers")
            }
            println("sysBPList after fixing common errors: $sysBPList")
            println("diaBPList after fixing common errors: $diaBPList")

            navigateToVerifyScanActivity(sysBPList, diaBPList, sevenDay)
        }
    }

    private fun suckItUp(value: String) {
        try {
            val number = value.toInt()
//            if (number in 20..220 && number != 202) { // 202 because if date is left unfilled, 202_ will be read as 202
//                currentDayReadings.add(number.toString())
//            }
            currentDayReadings.add(number.toString())
        } catch (_: NumberFormatException) {}
    }

    private fun suckItUpCheck() {
        println("Suck it up called $currentDayReadings")
        if (currentDayReadings.size == 2) {
            allDayReadings.add(currentDayReadings.toString())
        }
        else if (currentDayReadings.size == 1) {
            while (currentDayReadings.size < 2) {
                currentDayReadings.add("-1")
                println("OCR could not detect ${currentDayReadings}")
            }
            allDayReadings.add(currentDayReadings.toString())
        }
        else if (currentDayReadings.size == 0) {
            while (currentDayReadings.size < 2) {
                currentDayReadings.add("")
            }
            allDayReadings.add(currentDayReadings.toString())
        }
        // Experimental Even Values > 2
        else if (currentDayReadings.size % 2 == 0){
            println("Test Even $currentDayReadings")
            while (currentDayReadings.size != 2) {
                val templist = currentDayReadings.slice(0..1)
                currentDayReadings.removeAt(1)
                currentDayReadings.removeAt(0)
                allDayReadings.add(templist.toString())
            }
            allDayReadings.add(currentDayReadings.toString())
        }
        // Experimental Odd Values > 2
        // Only odd if OCR didn't detect header and not detecting some values and continue adding values, so size could be 5, 11 in this instance since it would be +3 everytime instead of 2, not 8 because its even if 2 headers gets detected, can't do anything which is why we do a list comparison
        else if (currentDayReadings.size % 2 == 1){
            println("Test Odd due to OCR not detecting $currentDayReadings")
            while (currentDayReadings.size != 2 && currentDayReadings.size != 0) {
                if (currentDayReadings.size < 2){
                    currentDayReadings.add("-1")
                }
                println("Updated $currentDayReadings")
                val templist = currentDayReadings.slice(0..1)
                currentDayReadings.removeAt(1)
                currentDayReadings.removeAt(0)
                allDayReadings.add(templist.toString())
            }
        }
        currentDayReadings.clear()
    }

    private fun splitAllDayReadings() {
//        println("All Day Readings $allDayReadings")
        for (pair in allDayReadings) {
            val readings = pair.removeSurrounding("[", "]").split(", ") // Clean the string and split the readings
            if (readings.size == 2) {
                allDayReadingsFinal.add(readings[0])
                allDayReadingsFinal.add(readings[1])
            }
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
        var afterDay1First = false
        var readingOfDay: String? = null
        var savedCount = 0
        var lastSaved = false
        var detectedBPHeader = false
        var clinicBPFound = false
        var countBeenSaved = false
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
                        detectedBPHeader = true
                    } else if (word.text == "Diastolic") {
                        println("Bounding Box ${block.boundingBox} with Diastolic")
                        secondBoundingBox = block.boundingBox!!
                        detectedBPHeader = true
                    }

                    // Search clinicBP in this and next bounding Box, flow must be in this order of sequence or would not detect
                    if (searchNextBoundingBox) {
                        val targetClinicBP = word.text.split("/").toTypedArray()
                        if (targetClinicBP.size == 2) {
                            clinicSysBP = targetClinicBP[0]
                            clinicDiaBP = targetClinicBP[1]
                            println("Clinic BP detected ${word.text}")
                        }
                        searchNextBoundingBox = false
                    }
                    if (accumulatedWords.contains("Clinic/OfficeBP") && !clinicBPFound) {
                        searchNextBoundingBox = true
                    }
                    if (accumulatedWords.contains("Clinic/OfficeBP:") && !clinicBPFound) {
                        val targetClinicBP = word.text.split("/").toTypedArray()
                        if (targetClinicBP.size == 2) {
                            clinicSysBP = targetClinicBP[0]
                            clinicDiaBP = targetClinicBP[1]
                            searchNextBoundingBox = false
                        }
                        clinicBPFound = true
                        println("Clinic BP detected ${word.text}")
                    }
                    accumulatedWords += word.text

                    // Comparison to detect the direction
                    if (firstBoundingBox != Rect(0, 0, 0, 0) && secondBoundingBox != Rect(0, 0, 0, 0)) {
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
                        } else {
                            direction = "Top"
                        }
                    }

                    // Get word list for each day to detect if there are missing values wrt. direction
                    if(!ranFirstTime) {
                        if (direction == "Top") {
                            when (accumulatedWords) {
                                "1", "1st", "st", "1s" -> {
                                    if (detectedBPHeader && block.boundingBox!!.top > firstBoundingBox.top) {
                                        readingOfDay = "1st"
                                        if (afterDay1First) {
                                            suckItUpCheck()
                                        }
                                    }
                                }

                                "2", "2nd", "nd", "2n" -> {
                                    if (detectedBPHeader && block.boundingBox!!.top > firstBoundingBox.top) {
                                        readingOfDay = "2nd"
                                        afterDay1First = true
                                        suckItUpCheck()
                                    }
                                }
                                // Multiple in case OCR can't detect a certain day, used to make sure pulse/enhance saves only after day 7 records are detected
                                "DAY3", "DAY4", "DAY5", "DAY6", "DAY7" -> {
                                    savedCount = totalCount
                                    countBeenSaved = true
                                }
                                // Saves for Day 7, use pulse as well in case image does not contain ENHaNCe
                                "ENHANCE" -> {
                                    if ((totalCount > savedCount) && !lastSaved && countBeenSaved) {
                                        suckItUpCheck()
                                        lastSaved = true
                                    }
                                }
                                // Make sure bounding box that detects Pulse is not within the one with Systolic and Diastolic, prevents the check
                                "Pulse" -> {
                                    if ((totalCount > savedCount) && !lastSaved && countBeenSaved) {
                                        suckItUpCheck()
                                        lastSaved = true
                                    }
                                }
                            }

                            if (firstBoundingBox != Rect(0, 0, 0, 0) && secondBoundingBox != Rect(
                                    0,
                                    0,
                                    0,
                                    0
                                )
                            ) {
                                if ((readingOfDay == "1st" || readingOfDay == "2nd") && block.boundingBox!!.left < secondBoundingBox.right && block.boundingBox!!.right > firstBoundingBox.left) {
                                    suckItUp(word.text)
                                }
                            } else if (firstBoundingBox != Rect(0, 0, 0, 0)) {
                                if ((readingOfDay == "1st" || readingOfDay == "2nd") && block.boundingBox!!.right > firstBoundingBox.left) {
                                    suckItUp(word.text)
                                }
                            } else if (secondBoundingBox != Rect(0, 0, 0, 0)) {
                                if ((readingOfDay == "1st" || readingOfDay == "2nd") && block.boundingBox!!.left < secondBoundingBox.right) {
                                    suckItUp(word.text)
                                }
                            } else {
                                if (readingOfDay == "1st" || readingOfDay == "2nd") {
                                    suckItUp(word.text)
                                }
                            }
                        } else if (direction == "Left") {
                            when (accumulatedWords) {
                                "1", "1st", "st", "1s" -> {
                                    if (detectedBPHeader && block.boundingBox!!.left > firstBoundingBox.left) {
                                        readingOfDay = "1st"
                                        if (afterDay1First) {
                                            suckItUpCheck()
                                        }
                                    }
                                }

                                "2", "2nd", "nd", "2n" -> {
                                    if (detectedBPHeader && block.boundingBox!!.left > firstBoundingBox.left) {
                                        readingOfDay = "2nd"
                                        afterDay1First = true
                                        suckItUpCheck()
                                    }
                                }
                                // Multiple in case OCR can't detect a certain day, used to make sure pulse/enhance saves only after day 7 records are detected
                                "DAY3", "DAY4", "DAY5", "DAY6", "DAY7" -> {
                                    savedCount = totalCount
                                    countBeenSaved = true
                                }
                                // Saves for Day 7, use pulse as well in case image does not contain ENHaNCe
                                "ENHANCE" -> {
                                    if ((totalCount > savedCount) && !lastSaved && countBeenSaved) {
                                        suckItUpCheck()
                                        lastSaved = true
                                    }
                                }
                                // Make sure bounding box that detects Pulse is not within the one with Systolic and Diastolic, prevents the check
                                "Pulse" -> {
                                    if ((totalCount > savedCount) && !lastSaved && countBeenSaved) {
                                        suckItUpCheck()
                                        lastSaved = true
                                    }
                                }
                            }

                            if (firstBoundingBox != Rect(0, 0, 0, 0) && secondBoundingBox != Rect(
                                    0,
                                    0,
                                    0,
                                    0
                                )
                            ) {
                                if ((readingOfDay == "1st" || readingOfDay == "2nd") && block.boundingBox!!.bottom > secondBoundingBox.top && block.boundingBox!!.top < firstBoundingBox.bottom) {
                                    suckItUp(word.text)
                                }
                            } else if (firstBoundingBox != Rect(0, 0, 0, 0)) {
                                if ((readingOfDay == "1st" || readingOfDay == "2nd") && block.boundingBox!!.top < firstBoundingBox.bottom) {
                                    suckItUp(word.text)
                                }
                            } else if (secondBoundingBox != Rect(0, 0, 0, 0)) {
                                if ((readingOfDay == "1st" || readingOfDay == "2nd") && block.boundingBox!!.bottom > secondBoundingBox.top) {
                                    suckItUp(word.text)
                                }
                            } else {
                                if (readingOfDay == "1st" || readingOfDay == "2nd") {
                                    suckItUp(word.text)
                                }
                            }
                        } else if (direction == "Right") {
                            when (accumulatedWords) {
                                "1", "1st", "st", "1s" -> {
                                    if (detectedBPHeader && block.boundingBox!!.right < firstBoundingBox.right) {
                                        readingOfDay = "1st"
                                        if (afterDay1First) {
                                            suckItUpCheck()
                                        }
                                    }
                                }

                                "2", "2nd", "nd", "2n" -> {
                                    if (detectedBPHeader && block.boundingBox!!.right < firstBoundingBox.right) {
                                        readingOfDay = "2nd"
                                        afterDay1First = true
                                        suckItUpCheck()
                                    }
                                }
                                // Multiple in case OCR can't detect a certain day, used to make sure pulse/enhance saves only after day 7 records are detected
                                "DAY3", "DAY4", "DAY5", "DAY6", "DAY7" -> {
                                    savedCount = totalCount
                                    countBeenSaved = true
                                }
                                // Saves for Day 7, use pulse as well in case image does not contain ENHaNCe
                                "ENHANCE" -> {
                                    if ((totalCount > savedCount) && !lastSaved && countBeenSaved) {
                                        suckItUpCheck()
                                        lastSaved = true
                                    }
                                }
                                // Make sure bounding box that detects Pulse is not within the one with Systolic and Diastolic, prevents the check
                                "Pulse" -> {
                                    if ((totalCount > savedCount) && !lastSaved && countBeenSaved) {
                                        suckItUpCheck()
                                        lastSaved = true
                                    }
                                }
                            }

                            if (firstBoundingBox != Rect(0, 0, 0, 0) && secondBoundingBox != Rect(
                                    0,
                                    0,
                                    0,
                                    0
                                )
                            ) {
                                if ((readingOfDay == "1st" || readingOfDay == "2nd") && block.boundingBox!!.top < secondBoundingBox.bottom && block.boundingBox!!.bottom > firstBoundingBox.top) {
                                    suckItUp(word.text)
                                }
                            } else if (firstBoundingBox != Rect(0, 0, 0, 0)) {
                                if ((readingOfDay == "1st" || readingOfDay == "2nd") && block.boundingBox!!.bottom > firstBoundingBox.top) {
                                    suckItUp(word.text)
                                }
                            } else if (secondBoundingBox != Rect(0, 0, 0, 0)) {
                                if ((readingOfDay == "1st" || readingOfDay == "2nd") && block.boundingBox!!.top < secondBoundingBox.bottom) {
                                    suckItUp(word.text)
                                }
                            } else {
                                if (readingOfDay == "1st" || readingOfDay == "2nd") {
                                    suckItUp(word.text)
                                }
                            }
                        } else if (direction == "Down") {
                            when (accumulatedWords) {
                                "1", "1st", "st", "1s" -> {
                                    if (detectedBPHeader && block.boundingBox!!.bottom < firstBoundingBox.bottom) {
                                        readingOfDay = "1st"
                                        if (afterDay1First) {
                                            suckItUpCheck()
                                        }
                                    }
                                }

                                "2", "2nd", "nd", "2n" -> {
                                    if (detectedBPHeader && block.boundingBox!!.bottom < firstBoundingBox.bottom) {
                                        readingOfDay = "2nd"
                                        afterDay1First = true
                                        suckItUpCheck()
                                    }
                                }
                                // Multiple in case OCR can't detect a certain day, used to make sure pulse/enhance saves only after day 7 records are detected
                                "DAY3", "DAY4", "DAY5", "DAY6", "DAY7" -> {
                                    savedCount = totalCount
                                    countBeenSaved = true
                                }
                                // Saves for Day 7, use pulse as well in case image does not contain ENHaNCe
                                "ENHANCE" -> {
                                    if ((totalCount > savedCount) && !lastSaved && countBeenSaved) {
                                        suckItUpCheck()
                                        lastSaved = true
                                    }
                                }
                                // Make sure bounding box that detects Pulse is not within the one with Systolic and Diastolic, prevents the check
                                "Pulse" -> {
                                    if ((totalCount > savedCount) && !lastSaved && countBeenSaved) {
                                        suckItUpCheck()
                                        lastSaved = true
                                    }
                                }
                            }

                            if (firstBoundingBox != Rect(0, 0, 0, 0) && secondBoundingBox != Rect(
                                    0,
                                    0,
                                    0,
                                    0
                                )
                            ) {
                                if ((readingOfDay == "1st" || readingOfDay == "2nd") && block.boundingBox!!.right > secondBoundingBox.left && block.boundingBox!!.left < firstBoundingBox.right) {
                                    suckItUp(word.text)
                                }
                            } else if (firstBoundingBox != Rect(0, 0, 0, 0)) {
                                if ((readingOfDay == "1st" || readingOfDay == "2nd") && block.boundingBox!!.left < firstBoundingBox.right) {
                                    suckItUp(word.text)
                                }
                            } else if (secondBoundingBox != Rect(0, 0, 0, 0)) {
                                if ((readingOfDay == "1st" || readingOfDay == "2nd") && block.boundingBox!!.right > secondBoundingBox.left) {
                                    suckItUp(word.text)
                                }
                            } else {
                                if (readingOfDay == "1st" || readingOfDay == "2nd") {
                                    suckItUp(word.text)
                                }
                            }
                        }
                    }
                }
            }

            // Get the last coordinates for all the different orientations
            if (leftGap == null){
                leftGap = block.boundingBox!!.left - 30
                println("First Left:$leftGap")
            }
            else if (block.boundingBox!!.left - 30 < leftGap){
                leftGap = block.boundingBox!!.left - 30
                println("New Left:$leftGap")

            }
            if (topGap == null){
                topGap = block.boundingBox!!.top - 30
                println("First Top:$topGap")
            }
            else if (block.boundingBox!!.top - 30 < topGap){
                topGap = block.boundingBox!!.top - 30
                println("New Top:$topGap")

            }
            if (rightGap == null){
                rightGap = block.boundingBox!!.right + 30
                println("First Right:$rightGap")
            }
            else if (block.boundingBox!!.right + 30 > rightGap){
                rightGap = block.boundingBox!!.right + 30
                println("New Right:$rightGap")

            }
            if (bottomGap == null){
                bottomGap = block.boundingBox!!.bottom + 30
                println("First Bottom:$bottomGap")
            }
            else if (block.boundingBox!!.bottom + 30 > bottomGap){
                bottomGap = block.boundingBox!!.bottom + 30
                println("New Bottom:$bottomGap")

            }
        }

        ranFirstTime = true
        println("Finalized list of day readings: $allDayReadings")
        println("Total number of readings: ${allDayReadings.count()}")

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
                    val top = min(firstBoundingBox.top, secondBoundingBox.top)
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
                    val left = min(firstBoundingBox.left, secondBoundingBox.left)
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
            onClickRequestPermissionNoGallery()
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