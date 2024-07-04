package com.singhealth.enhance.activities.patient

import android.app.DatePickerDialog
import android.app.ProgressDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.singhealth.enhance.R
import com.singhealth.enhance.activities.MainActivity
import com.singhealth.enhance.activities.settings.SettingsActivity
import com.singhealth.enhance.databinding.ActivityRegistrationBinding
import com.singhealth.enhance.security.AESEncryption
import com.singhealth.enhance.security.SecureSharedPreferences
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class RegistrationActivity : AppCompatActivity() {
    lateinit var binding: ActivityRegistrationBinding

    private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle
    private lateinit var progressDialog: ProgressDialog

    private lateinit var photoBA: ByteArray

    private val db = Firebase.firestore
    private val storage = Firebase.storage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Navigation drawer
        actionBarDrawerToggle = ActionBarDrawerToggle(this, binding.drawerLayout, 0, 0)
        binding.drawerLayout.addDrawerListener(actionBarDrawerToggle)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        actionBarDrawerToggle.syncState()

        binding.navigationView.setCheckedItem(R.id.item_patient_registration)

        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.item_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }

                R.id.item_patient_registration -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
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

        // Upload photo
        binding.photoIV.setOnClickListener { uploadPhoto() }

        // Date of birth
        val cal = Calendar.getInstance()

        val dobSetListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, month)
            cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)

            val calFormat = "dd/MM/yyyy"
            val sdf = SimpleDateFormat(calFormat, Locale.ENGLISH)
            binding.dateOfBirthTIET.setText(sdf.format(cal.time).toString())
        }

        binding.dateOfBirthTIET.setOnClickListener {
            val datePickerDialog = DatePickerDialog(
                this,
                dobSetListener,
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            )
            datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
            datePickerDialog.show()
        }

        // Gender
        val genderItems = listOf("Female", "Male")
        val genderAdapter = ArrayAdapter(this, R.layout.list_gender_item, genderItems)
        binding.genderACTV.setAdapter(genderAdapter)

        // Dismiss error messages
        binding.legalNameTIET.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.legalNameTIL.error = null
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.idTIET.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.idTIL.error = null
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.dateOfBirthTIET.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.dateOfBirthTIL.error = null
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.genderACTV.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.genderTIL.error = null
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.addressTIET.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.addressTIL.error = null
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.weightTIET.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.weightTIL.error = null
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.heightTIET.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.heightTIL.error = null
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        progressDialog = ProgressDialog(this)
        progressDialog.setCanceledOnTouchOutside(false)

        // Register patient
        binding.registerBtn.setOnClickListener {
            if (validateFields()) {
                registerPatient()
            } else {
                Toast.makeText(
                    this,
                    "Please correct all errors before proceeding.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (actionBarDrawerToggle.onOptionsItemSelected(item)) {
            true
        } else super.onOptionsItemSelected(item)
    }

    private fun uploadPhoto() {
        val browseImageIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
        }
        if (browseImageIntent.resolveActivity(packageManager) != null) {
            activityResultLauncher.launch(browseImageIntent)
        }
    }

    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK) {
                val uri = result.data?.data ?: return@registerForActivityResult
                try {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        val squareBitmap = cropToSquare(bitmap) // Convert the image to a square (No cropping)
                        val stream = ByteArrayOutputStream()



                        squareBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                        photoBA = stream.toByteArray()
                        binding.photoIV.setImageBitmap(squareBitmap)

                        binding.photoTV.text = getString(R.string.profile_picture_info)
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, e.message.toString(), Toast.LENGTH_LONG).show()
                }
            }
        }

    // Cropping the image to be a square
    private fun cropToSquare(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val newWidth = if (width < height) width else height
        val newHeight = if (width < height) width else height

        val cropW = (width - newWidth) / 2
        val cropH = (height - newHeight) / 2

        return Bitmap.createBitmap(bitmap, cropW, cropH, newWidth, newHeight)
    }

    private fun validateFields(): Boolean {
        var valid = true

        if (!::photoBA.isInitialized) {
            valid = false
            MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_error)
                .setTitle("No photo uploaded")
                .setMessage("Please upload patient's photo before continuing.")
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
        }

        if (binding.legalNameTIET.text.isNullOrEmpty()) {
            valid = false
            binding.legalNameTIL.error = "Field cannot be empty"
        }

        if (binding.idTIET.text.isNullOrEmpty()) {
            valid = false
            binding.idTIL.error = "Field cannot be empty"
        }

        if (binding.dateOfBirthTIET.text.isNullOrEmpty()) {
            valid = false
            binding.dateOfBirthTIL.error = "Field cannot be empty"
        }

        if (binding.genderACTV.text.isNullOrEmpty()) {
            valid = false
            binding.genderTIL.error = "Field cannot be empty"
        }

        if (binding.addressTIET.text.isNullOrEmpty()) {
            valid = false
            binding.addressTIL.error = "Field cannot be empty"
        }

        if (binding.weightTIET.text.isNullOrEmpty()) {
            valid = false
            binding.weightTIL.error = "Field cannot be empty"
        } else if (binding.weightTIET.text.toString().toFloatOrNull() == null) {
            valid = false
            binding.weightTIL.error = "Invalid value"
        }

        if (binding.heightTIET.text.isNullOrEmpty()) {
            valid = false
            binding.heightTIL.error = "Field cannot be empty"
        } else if (binding.heightTIET.text.toString().toFloatOrNull() == null) {
            valid = false
            binding.heightTIL.error = "Invalid value"
        }

        return valid
    }

    private fun registerPatient() {
        progressDialog.setMessage("Please wait while the patient is being registered.")
        progressDialog.show()

        val photo = photoBA

        val id = AESEncryption().encrypt(binding.idTIET.text.toString().trim().uppercase())

        var gender = 0
        when (binding.genderACTV.text.toString()) {
            "Male" -> gender = 1
            "Female" -> gender = 2
        }

        val patient = hashMapOf(
            "legalName" to AESEncryption().encrypt(
                binding.legalNameTIET.text.toString().uppercase()
            ),
            "dateOfBirth" to AESEncryption().encrypt(binding.dateOfBirthTIET.text.toString()),
            "gender" to gender,
            "address" to AESEncryption().encrypt(binding.addressTIET.text.toString().uppercase()),
            "weight" to AESEncryption().encrypt(binding.weightTIET.text.toString()),
            "height" to AESEncryption().encrypt(binding.heightTIET.text.toString()),
            "bpStage" to "N/A"
        )

        val docRef = db.collection("patients").document(id)
        docRef.get().addOnSuccessListener { documentSnapshot ->
            if (documentSnapshot.exists()) {
                MaterialAlertDialogBuilder(this)
                    .setIcon(R.drawable.ic_error)
                    .setTitle("Patient already registered")
                    .setMessage("Would you like to search the patient instead?")
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                        progressDialog.dismiss()
                    }
                    .setPositiveButton("OK") { _, _ ->
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                    .show()
            } else {
                val nricDecrypted = AESEncryption().decrypt(id)
                val storageRef = storage.reference.child("images/$nricDecrypted.jpg")
                storageRef.putBytes(photo).addOnSuccessListener {
                    storageRef.downloadUrl.addOnSuccessListener { uri ->
                        val photoUrl = uri.toString()
                        patient["photoUrl"] = photoUrl

                        docRef.set(patient)
                            .addOnSuccessListener {
                                progressDialog.dismiss()

                                Toast.makeText(
                                    this,
                                    "Successfully registered '${AESEncryption().decrypt(id)}' into the system.",
                                    Toast.LENGTH_SHORT
                                ).show()

                                val patientSharedPreferences =
                                    SecureSharedPreferences.getSharedPreferences(
                                        applicationContext
                                    )
                                patientSharedPreferences.edit().apply {
                                    putString("patientID", id)
                                    apply()
                                }

                                startActivity(Intent(this, ProfileActivity::class.java))
                                finish()
                            }
                            .addOnFailureListener { e ->
                                progressDialog.dismiss()

                                MaterialAlertDialogBuilder(this)
                                    .setTitle("Error accessing Firestore Database")
                                    .setMessage("The app is having trouble communicating with the Firestore Database.\n\nContact IT support with the following error code if issue persists: $e")
                                    .setNeutralButton("Back to Main") { _, _ ->
                                        startActivity(Intent(this, MainActivity::class.java))
                                        finish()
                                    }
                                    .setPositiveButton("Retry") { dialog, _ ->
                                        registerPatient()
                                        dialog.dismiss()
                                    }
                                    .show()
                            }
                    }
                }.addOnFailureListener { e ->
                    progressDialog.dismiss()

                    MaterialAlertDialogBuilder(this)
                        .setTitle("Error accessing Firebase Storage")
                        .setMessage("The app is having trouble communicating with the Firebase Storage.\n\nContact IT support with the following error code if issue persists: $e")
                        .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                        .show()
                }
            }
        }
    }
}