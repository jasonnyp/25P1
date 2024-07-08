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
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.singhealth.enhance.R
import com.singhealth.enhance.activities.MainActivity
import com.singhealth.enhance.activities.error.firebaseErrorDialog
import com.singhealth.enhance.activities.error.internetConnectionCheck
import com.singhealth.enhance.activities.error.noPatientPhotoErrorDialog
import com.singhealth.enhance.activities.error.patientExistsErrorDialog
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

        internetConnectionCheck(this)

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
        val genderItems = listOf(getString(R.string.register_gender_male), getString(R.string.register_gender_female))
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
                    getString(R.string.register_field_verification),
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
            noPatientPhotoErrorDialog(this)
        }

        if (binding.legalNameTIET.text.isNullOrEmpty()) {
            valid = false
            binding.legalNameTIL.error = getString(R.string.register_empty_field_verification)
        }

        if (binding.idTIET.text.isNullOrEmpty()) {
            valid = false
            binding.idTIL.error = getString(R.string.register_empty_field_verification)
        }

        if (binding.dateOfBirthTIET.text.isNullOrEmpty()) {
            valid = false
            binding.dateOfBirthTIL.error = getString(R.string.register_empty_field_verification)
        }

        if (binding.genderACTV.text.isNullOrEmpty()) {
            valid = false
            binding.genderTIL.error = getString(R.string.register_empty_field_verification)
        }

        if (binding.addressTIET.text.isNullOrEmpty()) {
            valid = false
            binding.addressTIL.error = getString(R.string.register_empty_field_verification)
        }

        if (binding.weightTIET.text.isNullOrEmpty()) {
            valid = false
            binding.weightTIL.error = getString(R.string.register_empty_field_verification)
        } else if (binding.weightTIET.text.toString().toFloatOrNull() == null) {
            valid = false
            binding.weightTIL.error = getString(R.string.register_invalid_value_verification)
        }

        if (binding.heightTIET.text.isNullOrEmpty()) {
            valid = false
            binding.heightTIL.error = getString(R.string.register_empty_field_verification)
        } else if (binding.heightTIET.text.toString().toFloatOrNull() == null) {
            valid = false
            binding.heightTIL.error = getString(R.string.register_invalid_value_verification)
        }

        return valid
    }

    private fun registerPatient() {
        progressDialog.setMessage(getString(R.string.register_loading_dialog))
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
                patientExistsErrorDialog(this, progressDialog)
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
                                    getString(R.string.register_successful, AESEncryption().decrypt(id)),
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
                                firebaseErrorDialog(this, e, docRef)

                            }
                    }
                }.addOnFailureListener { e ->
                    progressDialog.dismiss()

                    firebaseErrorDialog(this, e, storageRef, photo)
                }
            }
        }
    }
}