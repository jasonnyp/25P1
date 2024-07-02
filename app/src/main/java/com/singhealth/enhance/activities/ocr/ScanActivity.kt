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
import androidx.core.text.isDigitsOnly
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.document.FirebaseVisionDocumentText
import com.singhealth.enhance.R
import com.singhealth.enhance.activities.DashboardActivity
import com.singhealth.enhance.activities.MainActivity
import com.singhealth.enhance.activities.history.HistoryActivity
import com.singhealth.enhance.activities.patient.ProfileActivity
import com.singhealth.enhance.activities.patient.RegistrationActivity
import com.singhealth.enhance.activities.settings.SettingsActivity
import com.singhealth.enhance.databinding.ActivityScanBinding
import com.singhealth.enhance.security.AESEncryption
import com.singhealth.enhance.security.SecureSharedPreferences
import kotlin.math.abs

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
        binding.bottomNavigationView.selectedItemId = R.id.item_scan

        binding.bottomNavigationView.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.item_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    finish()
                    false
                }

                R.id.item_scan -> {
                    true
                }

                R.id.item_history -> {
                    startActivity(Intent(this, HistoryActivity::class.java))
                    finish()
                    false
                }
                R.id.item_dashboard -> {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                    false
                }

                else -> false
            }
        }

        // Check if patient information is available in the current session
        val patientSharedPreferences = SecureSharedPreferences.getSharedPreferences(applicationContext)
        if (patientSharedPreferences.getString("patientID", null).isNullOrEmpty()) {
            val mainIntent = Intent(this, MainActivity::class.java)
            Toast.makeText(this, "Patient information could not be found in current session. Please try again.", Toast.LENGTH_LONG).show()
            startActivity(mainIntent)
            finish()
        }else {
            patientID = patientSharedPreferences.getString("patientID", null).toString()
            binding.patientIdValueTextView.text = AESEncryption().decrypt(patientID)
            println("Legal name: ${patientSharedPreferences.getString("legalName", null)}")
            binding.patientNameValueTextView.text =patientSharedPreferences.getString("legalName", null).toString()
        }
        binding.ocrInstructionsTextViewValue.text = "Please ensure that only the blood pressure values are seen and exclude all headers when cropping."

        cameraPermissions =
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        storagePermissions = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)

        progressDialog = ProgressDialog(this)
        progressDialog.setCanceledOnTouchOutside(false)

        binding.selectSourceBtn.setOnClickListener {
            onClickRequestPermission()
        }
    }


    // Below codes are for the camera and photo crop functionality
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

//    private fun handleCropImageResult(uri: String) {
//        outputUri = Uri.parse(uri.replace("file:", "")).also { parsedUri ->
//            binding.cropIV.setImageUriAsync(parsedUri)
//        }
//
//        if (outputUri != null) {
//            val filePath = outputUri.path
//            println(filePath)
//            val inputStream = contentResolver.openInputStream(outputUri)
//
//            if (filePath != null) {
//                lifecycleScope.launch { // Launch a coroutine on lifecycleScope
//                    if (inputStream != null) {
//                        processDocumentImage()
//                    }
//                }
//            } else {
//                Toast.makeText(this, "ERROR: No file path for the image.", Toast.LENGTH_SHORT).show()
//            }
//        } else {
//            Toast.makeText(this, "ERROR: No image to process.", Toast.LENGTH_SHORT).show()
//        }
//    }
    private fun handleCropImageResult(uri: String) {
            outputUri = Uri.parse(uri.replace("file:", "")).also { parsedUri ->
                binding.cropIV.setImageUriAsync(parsedUri)
                binding.ocrInstructionsTextViewValue.visibility = View.GONE
            }

            if (outputUri != null) {
                processDocumentImage()
            } else {
                Toast.makeText(this, "ERROR: No image to process.", Toast.LENGTH_SHORT).show()
            }
        }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (!isGranted) {
                MaterialAlertDialogBuilder(this)
                    .setIcon(R.drawable.ic_error)
                    .setTitle("Enable app permissions")
                    .setMessage("To use the OCR functionality, you need to allow access to your camera, gallery and external storage.")
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                    .show()
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

    //  Trying out AWS TextRACT (use analyze_doc for sync)

//    suspend fun processdoc2(inputStream: InputStream) {
//
//        val sourceBytes = inputStream.readBytes()
//
//        // Get the input Document object as bytes.
//        val myDoc = Document {
//            bytes = sourceBytes
//        }
//
//        val detectDocumentTextRequest = DetectDocumentTextRequest {
//            document = myDoc
//        }
//
//        TextractClient { credentialsProvider = EnvironmentCredentialsProvider(); region = "ap-southeast-1" }.use { textractClient ->
//            val response = textractClient.detectDocumentText(detectDocumentTextRequest)
//            response.blocks?.forEach { block ->
//                println("The block type is ${block.blockType}")
//            }
//
//            val documentMetadata = response.documentMetadata
//            if (documentMetadata != null) {
//                println("The number of pages in the document is ${documentMetadata.pages}")
//            }
//        }
//    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (actionBarDrawerToggle.onOptionsItemSelected(item)) {
            true
        } else super.onOptionsItemSelected(item)
    }


    // Below codes are for the OCR functions
    private fun processDocumentImage() {
        progressDialog.setTitle("Processing image")
        progressDialog.setMessage("Please wait a moment...")
        progressDialog.show()

        val ocrEngine = FirebaseVision.getInstance().cloudDocumentTextRecognizer
        var imageToProcess = FirebaseVisionImage.fromFilePath(this, outputUri)

        ocrEngine.processImage(imageToProcess)
            .addOnSuccessListener { firebaseVisionDocumentText ->
                processDocumentTextBlock(firebaseVisionDocumentText)
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()

                MaterialAlertDialogBuilder(this)
                    .setIcon(R.drawable.ic_error)
                    .setTitle("Error processing image")
                    .setMessage("The image cannot be processed due to an error.\n\nContact IT support with the following error code if issue persists: ${e.message}")
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                    .show()
            }
    }



    private fun processDocumentTextBlock(result: FirebaseVisionDocumentText) {
        var sysBPList = mutableListOf<String>()
        var diaBPList = mutableListOf<String>()

        val blocks = result.blocks

        if (blocks.isEmpty()) {
            progressDialog.dismiss()

            MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_error)
                .setTitle("Nothing to process")
                .setMessage("We couldn't find any handwriting or text to process.\n\nTip: Ensure the image is clear and cropped to only the values, and exclude any headers (such as 'Systolic' and 'Diastolic').")
                .setPositiveButton(R.string.dialog_positive_ok) { dialog, _ -> dialog.dismiss() }
                .show()
        } else {
            val words = mutableListOf<FirebaseVisionDocumentText.Word>()

            for (block in blocks) {
                val paragraphs = block.paragraphs
                for (paragraph in paragraphs) {
                    words.addAll(paragraph.words)
                }
            }

            words.sortWith(compareBy({ it.boundingBox?.top }, { it.boundingBox?.left }))
            var nextWord: FirebaseVisionDocumentText.Word? = null
            var prevWord: FirebaseVisionDocumentText.Word? = null

            for (i in 1..<words.size step 2) {
                var word = words[i]

                // Skip '*'
                if (word.text == "*" && i != words.size - 1) {
                    word = words[i + 1]
                    nextWord = words[i + 2]
                    prevWord = words[i - 1]
                }
                if (i != words.size - 1) {
                    nextWord = words[i + 1]
                    if (nextWord.text == "*") {
                        try {
                            nextWord = words[i + 2]
                        } catch (e: Exception) {
                        }
                    }
                }

                prevWord = words[i - 1]

                // Ignore '*'
                if (prevWord.text == "*") {
                    try {
                        prevWord = words[i - 2]
                    } catch (e: Exception) {
                    }
                }

                println("Word: ${words[i].text}, BoundingBox: ${words[i].boundingBox}")
                println("PrevWord: ${prevWord?.text}, BoundingBox: ${prevWord?.boundingBox}")

                val prevMidpoint =
                    (prevWord?.boundingBox!!.top + prevWord?.boundingBox!!.bottom) / 2
                val currentMidpoint = (word.boundingBox!!.top + word.boundingBox!!.bottom) / 2

                try {
                    if (nextWord != null) {
                        println("NextWord: ${nextWord.text}, BoundingBox: ${nextWord.boundingBox}")
                    }

                    val nextMidpoint =
                        (nextWord?.boundingBox!!.top + nextWord.boundingBox!!.bottom) / 2

                    if (abs(currentMidpoint - prevMidpoint) <= 50 &&
                        word.boundingBox!!.left <= prevWord?.boundingBox!!.right + 1000
                    ) {
                        sysBPList.add(prevWord.text)
                        diaBPList.add(word.text)
                        if (i + 1 == words.size - 1 && nextWord != null) {
                            sysBPList.add(nextWord.text)
                            diaBPList.add("")
                        }
                    } else if (abs(nextMidpoint - currentMidpoint) <= 50 &&
                        nextWord.boundingBox!!.left <= word.boundingBox!!.right + 1000
                    ) {
                        sysBPList.add(word.text)
                        diaBPList.add(nextWord.text)
                    } else {
                        sysBPList.add(word.text)
                        sysBPList.add(prevWord.text)
                        diaBPList.add("")
                        diaBPList.add("")
                    }
                    if (prevWord.text !in sysBPList && prevWord.text !in diaBPList) {
                        sysBPList.add(prevWord.text)
                        diaBPList.add("")
                    }
                } catch (e: Exception) {
                    println("error caught at next word")
                    if (abs(currentMidpoint - prevMidpoint) <= 50 &&
                        word.boundingBox!!.left <= prevWord.boundingBox!!.right + 1000
                    ) {
                        sysBPList.add(prevWord.text)
                        diaBPList.add(word.text)
                        if (i == words.size - 1) {
                            if (nextWord != null) {
                                sysBPList.add(nextWord.text)
                                diaBPList.add("")
                            }
                        }
                    }
                }
                println(sysBPList)
                println(diaBPList)

//                val nextMidpoint = (nextWord?.boundingBox!!.top + nextWord.boundingBox!!.bottom) / 2
                //if try catch caught then exit
//                if (abs(currentMidpoint - previousMidpoint) <= 50 &&
//                    nextWord.boundingBox!!.left <= word.boundingBox!!.right + 1000) {
//                    sysBPList.add(previousWord.text)
//                    diaBPList.add(word.text)
//                    if (i+1==words.size-1 && nextWord != null){
//                        sysBPList.add(nextWord.text)
//                        diaBPList.add("")
//
//                    }
//                } else if (abs(nextMidpoint - currentMidpoint) <= 50 &&
//                    nextWord.boundingBox!!.left <= word.boundingBox!!.right + 1000) {
//                    sysBPList.add(word.text)
//                    diaBPList.add(nextWord.text)
//                }
//                else{
//                    sysBPList.add(word.text)
//                    sysBPList.add(previousWord.text)
//                    diaBPList.add("")
//                    diaBPList.add("")
//                }
//                if (previousWord.text !in sysBPList && previousWord.text !in diaBPList){
//                    sysBPList.add(previousWord.text)
//                    diaBPList.add("")
//                }
            }

            // Attempt to fix common errors
            for (i in 0..<maxOf(sysBPList.size, diaBPList.size)) {
                sysBPList[i] =
                    sysBPList[i].replace("기", "71").replace("ㄱㄱ", "77").replace("ㄱㅇ", "70")
                        .replace("סר", "70").replace("סך", "70").replace(" ", "")
                diaBPList[i] =
                    diaBPList[i].replace("기", "71").replace("ㄱㄱ", "77").replace("ㄱㅇ", "70")
                        .replace("סר", "70").replace("סך", "70").replace(" ", "")
            }




            for (i in sysBPList.indices) {
                val systolicBPValue = sysBPList[i].toIntOrNull()
                val diastolicBPValue = diaBPList[i].toIntOrNull()

                if (systolicBPValue != null && diastolicBPValue != null && systolicBPValue in 50..200 && diastolicBPValue in 50..200) {
                    if (systolicBPValue < diastolicBPValue) {
                        sysBPList[i] = diastolicBPValue.toString()
                        diaBPList[i] = systolicBPValue.toString()
                    } else if (diastolicBPValue > systolicBPValue) {
                        diaBPList[i] = systolicBPValue.toString()
                        sysBPList[i] = diastolicBPValue.toString()
                    }
                }
            }

            // SWAP when there is obvious case that values are flipped
            for (i in 0 until maxOf(sysBPList.size, diaBPList.size)) {
                if (diaBPList[i].length == 2 && diaBPList[i].isDigitsOnly()) {
                    if (diaBPList[i][0].digitToInt() < 5 && diaBPList[i][1].digitToInt() >= 5) {
                        println("value before flipped: ${diaBPList[i]}")
                        diaBPList[i] = diaBPList[i].reversed()
                        println("value after flipped: ${diaBPList[i]}")
                    }
                } else if (diaBPList[i].length == 3 && diaBPList[i].isDigitsOnly()) {
                    if (diaBPList[i][0].digitToInt() != 1 && diaBPList[i][2].digitToInt() == 1) {
                        println("value before flipped: ${diaBPList[i]}")
                        diaBPList[i] = diaBPList[i].reversed()
                        println("value after flipped: ${diaBPList[i]}")
                    }
                }
                if (sysBPList[i].length == 3 && sysBPList[i].isDigitsOnly()) {
                    if (sysBPList[i][0].digitToInt() != 1 && sysBPList[i][2].digitToInt() == 1) {
                        sysBPList[i] = sysBPList[i].reversed()
                    }
                }
            }

            var addSysBP = 0
            var addDiaBP = 0
            var bpAvg = 0
            var count = 0


            // Count the total number of systolic and diastolic numbers
            for (i in 0..<maxOf(sysBPList.size, diaBPList.size)) {
                if (sysBPList[i].isDigitsOnly() && sysBPList[i].isNotEmpty()) {
                    if (sysBPList[i].toInt() in 50..200) {
                        addSysBP += sysBPList[i].toInt()
                        count += 1
                    }
                }
                if (diaBPList[i].isDigitsOnly() && diaBPList[i].isNotEmpty()) {
                    if (diaBPList[i].toInt() in 50..200) {
                        addDiaBP += diaBPList[i].toInt()
                        count += 1
                    }
                }
            }

            // SWAP based on the assumption that 'value <= 106' is diastolic and count less than 4 as average may be skewed
            // when there are a few numbers only
            if (count <= 4) {
                for (i in 0 until maxOf(sysBPList.size, diaBPList.size)) {
                    if (sysBPList[i].isDigitsOnly() && sysBPList[i].isNotEmpty()) {
                        if (sysBPList[i].toInt() in 50..106) {
                            println("Swapping sysBP value as it is <= 106: ${sysBPList[i]}")
                            val sysValue = sysBPList[i]
                            val diaValue = diaBPList[i]
                            diaBPList[i] = sysValue
                            sysBPList[i] = diaValue
                        }
                    }

                    if (diaBPList[i].isDigitsOnly() && diaBPList[i].isNotEmpty()) {
                        if (diaBPList[i].toInt() in 107..200) {
                            println("Swapping diaBP value as it is > 106: ${diaBPList[i]}")
                            val sysValue = sysBPList[i]
                            val diaValue = diaBPList[i]
                            diaBPList[i] = sysValue
                            sysBPList[i] = diaValue
                        }
                    }
                }
            }

            // Swap based on average (NOTE: may be inaccurate if more there are more sysBP values than diaBP values, vice versa)
            // Average is calculated by adding all numbers detected(both systolic and diastolic) and dividing by number of values detected
            // Check if number above average is a systolic value otherwise swap position with diastolic, vice versa
            else if (count > 4) {
                bpAvg = (addSysBP + addDiaBP) / count
                println("[ScanActivity] BP average: $bpAvg")
                for (i in 0 until maxOf(sysBPList.size, diaBPList.size)) {
                    try {
                        if (sysBPList[i].toInt() < bpAvg && sysBPList[i].toInt() in 50..200) {
                            println("Swapping sysBP value as it is < average: ${sysBPList[i]}")
                            val sysValue = sysBPList[i]
                            val diaValue = diaBPList[i]
                            diaBPList[i] = sysValue
                            sysBPList[i] = diaValue
                        }
                    } catch (e: Exception) {
                    }

                    try {
                        if (diaBPList[i].toInt() > bpAvg && diaBPList[i].toInt() in 50..200) {
                            println("Swapping diaBP value as it is > average: ${diaBPList[i]}")
                            val sysValue = sysBPList[i]
                            val diaValue = diaBPList[i]
                            diaBPList[i] = sysValue
                            sysBPList[i] = diaValue
                        }
                    } catch (e: Exception) {
                    }
                }
            }

            // To cover the possibility that the diastolic value was previously in systolic hence not flipped
            for (i in 0 until maxOf(sysBPList.size, diaBPList.size)) {
                if (diaBPList[i].length == 2 && diaBPList[i].isDigitsOnly()) {
                    if (diaBPList[i][0].digitToInt() < 5 && diaBPList[i][1].digitToInt() >= 5) {
                        println("final diaBP value flipped: ${diaBPList[i]}")
                        diaBPList[i] = diaBPList[i].reversed()
                    }
                }
            }

            // Swap to ensure bigger number is in sysBPList as the last layer of checking
            for (i in sysBPList.indices) {
                val systolicBPValue = sysBPList[i].toIntOrNull()
                val diastolicBPValue = diaBPList[i].toIntOrNull()

                if (systolicBPValue != null && diastolicBPValue != null && systolicBPValue in 50..200 && diastolicBPValue in 50..200) {
                    if (systolicBPValue < diastolicBPValue) {
                        sysBPList[i] = diastolicBPValue.toString()
                        diaBPList[i] = systolicBPValue.toString()
                    } else if (diastolicBPValue > systolicBPValue) {
                        diaBPList[i] = systolicBPValue.toString()
                        sysBPList[i] = diastolicBPValue.toString()
                    }
                }
            }


            // Replace values that are not digits with blank
            sysBPList.replaceAll { if (it.isDigitsOnly()) it else "" }
            diaBPList.replaceAll { if (it.isDigitsOnly()) it else "" }

            val bundle = Bundle()

            bundle.putStringArrayList("sysBPList", ArrayList(sysBPList))
            bundle.putStringArrayList("diaBPList", ArrayList(diaBPList))

            // Restore previous records to VerifyScanActivity (if any)
            if (intent.extras != null && intent.extras!!.containsKey("homeSysBPTarget")) {
                bundle.putString("homeSysBPTarget", intent.extras!!.getString("homeSysBPTarget"))
            }
            if (intent.extras != null && intent.extras!!.containsKey("homeDiaBPTarget")) {
                bundle.putString("homeDiaBPTarget", intent.extras!!.getString("homeDiaBPTarget"))
            }
            if (intent.extras != null && intent.extras!!.containsKey("clinicSysBPTarget")) {
                bundle.putString(
                    "clinicSysBPTarget",
                    intent.extras!!.getString("clinicSysBPTarget")
                )
            }
            if (intent.extras != null && intent.extras!!.containsKey("clinicDiaBPTarget")) {
                bundle.putString(
                    "clinicDiaBPTarget",
                    intent.extras!!.getString("clinicDiaBPTarget")
                )
            }

            if (intent.hasExtra("sysBPListHistory")) {
                bundle.putStringArrayList(
                    "sysBPListHistory",
                    intent.getStringArrayListExtra("sysBPListHistory")
                )
            }
            if (intent.hasExtra("diaBPListHistory")) {
                bundle.putStringArrayList(
                    "diaBPListHistory",
                    intent.getStringArrayListExtra("diaBPListHistory")
                )
            }

            val verifyScanIntent = Intent(this, VerifyScanActivity::class.java)
            verifyScanIntent.putExtras(bundle)

            progressDialog.dismiss()

            startActivity(verifyScanIntent)
            finish()
        }
    }
}