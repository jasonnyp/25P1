package com.singhealth.enhance.activities.patient

import android.annotation.SuppressLint
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
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.singhealth.enhance.R
import com.singhealth.enhance.activities.MainActivity
import com.singhealth.enhance.activities.validation.errorDialogBuilder
import com.singhealth.enhance.activities.validation.firebaseErrorDialog
import com.singhealth.enhance.activities.validation.internetConnectionCheck
import com.singhealth.enhance.databinding.ActivityEditPatientBinding
import com.singhealth.enhance.security.AESEncryption
import com.singhealth.enhance.security.SecureSharedPreferences
import com.singhealth.enhance.security.StaffSharedPreferences
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class EditProfileActivity : AppCompatActivity() {
    lateinit var binding: ActivityEditPatientBinding

    private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle
    private lateinit var progressDialog: ProgressDialog

    private lateinit var photoBA: ByteArray
    private lateinit var id: String

    private val db = Firebase.firestore
    private val storage = Firebase.storage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityEditPatientBinding.inflate(layoutInflater)
        setContentView(binding.root)

        internetConnectionCheck(this)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                startActivity(Intent(this@EditProfileActivity, EditProfileActivity::class.java))
                finish()
            }
        })

        // Upload photo
        binding.editPhotoIV.setOnClickListener { uploadPhoto() }

        // Date of birth
        val cal = Calendar.getInstance()

        val dobSetListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, month)
            cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)

            val calFormat = "dd/MM/yyyy"
            val sdf = SimpleDateFormat(calFormat, Locale.ENGLISH)
            binding.editDateOfBirthTIET.setText(sdf.format(cal.time).toString())
        }

        binding.editDateOfBirthTIET.setOnClickListener {
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
        binding.editGenderACTV.setAdapter(genderAdapter)

        // Dismiss error messages
        binding.editLegalNameTIET.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.editLegalNameTIL.error = null
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.editClinicIdTIET.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.editClinicIdTIET.error = null
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.editDateOfBirthTIET.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.editDateOfBirthTIL.error = null
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.editGenderACTV.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.editGenderTIL.error = null
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.editWeightTIET.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.editWeightTIL.error = null
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.editHeightTIET.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.editHeightTIL.error = null
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.editRegisterHomeSysInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.editRegisterHomeSys.error = null
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.editRegisterHomeDiaInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.editRegisterHomeDia.error = null
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        progressDialog = ProgressDialog(this)
        progressDialog.setCanceledOnTouchOutside(false)

        // Load patient data
        loadPatientData()

        // Update patient
        binding.updateBtn.setOnClickListener {
            if (validateFields()) {
                updatePatient()
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
        return when (item.itemId) {
            android.R.id.home -> {
                startActivity(Intent(this, ProfileActivity::class.java))
                finish()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
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
                        binding.editPhotoIV.setImageBitmap(squareBitmap)

                        binding.editPhotoTV.text = getString(R.string.profile_picture_info)
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
            errorDialogBuilder(this, getString(R.string.register_image_verification_header), getString(R.string.register_image_verification_body))
        }

        if (binding.editLegalNameTIET.text.isNullOrEmpty()) {
            valid = false
            binding.editLegalNameTIL.error = getString(R.string.register_empty_field_verification)
        }

        if (binding.editClinicIdTIET.text.isNullOrEmpty()) {
            valid = false
            binding.editClinicIdTIET.error = getString(R.string.register_empty_field_verification)
        }

        if (binding.editDateOfBirthTIET.text.isNullOrEmpty()) {
            valid = false
            binding.editDateOfBirthTIL.error = getString(R.string.register_empty_field_verification)
        }

        if (binding.editGenderACTV.text.isNullOrEmpty()) {
            valid = false
            binding.editGenderTIL.error = getString(R.string.register_empty_field_verification)
        }

        if (binding.editWeightTIET.text.isNullOrEmpty()) {
            valid = false
            binding.editWeightTIL.error = getString(R.string.register_empty_field_verification)
        } else if (binding.editWeightTIET.text.toString().toFloatOrNull() == null) {
            valid = false
            binding.editWeightTIL.error = getString(R.string.register_invalid_value_verification)
        }

        if (binding.editHeightTIET.text.isNullOrEmpty()) {
            valid = false
            binding.editHeightTIL.error = getString(R.string.register_empty_field_verification)
        } else if (binding.editHeightTIET.text.toString().toFloatOrNull() == null) {
            valid = false
            binding.editHeightTIL.error = getString(R.string.register_invalid_value_verification)
        }

        if (binding.editRegisterHomeSysInput.text.isNullOrEmpty()) {
            valid = false
            binding.editRegisterHomeSys.error = getString(R.string.register_empty_bp_verification, 135)
        } else if (binding.editRegisterHomeSysInput.text.toString().toFloatOrNull() == null) {
            valid = false
            binding.editRegisterHomeSys.error = getString(R.string.register_invalid_value_verification)
        } else if (binding.editRegisterHomeSysInput.text.toString().toInt() > 200 || binding.editRegisterHomeSysInput.text.toString().toInt() < 0) {
            valid = false
            binding.editRegisterHomeSys.error = getString(R.string.register_invalid_number_verification)
        }

        if (binding.editRegisterHomeDiaInput.text.isNullOrEmpty()) {
            valid = false
            binding.editRegisterHomeDia.error = getString(R.string.register_empty_bp_verification, 85)
        } else if (binding.editRegisterHomeDiaInput.text.toString().toFloatOrNull() == null) {
            valid = false
            binding.editRegisterHomeDia.error = getString(R.string.register_invalid_value_verification)
        } else if (binding.editRegisterHomeDiaInput.text.toString().toInt() > 200 || binding.editRegisterHomeDiaInput.text.toString().toInt() < 0) {
            valid = false
            binding.editRegisterHomeDia.error = getString(R.string.register_invalid_number_verification)
        }

        return valid
    }

    private fun loadPatientData() {
        progressDialog.setMessage(getString(R.string.loading_patient_data))
        progressDialog.show()

        val patientSharedPreferences = SecureSharedPreferences.getSharedPreferences(applicationContext)
        val patientId = patientSharedPreferences.getString("patientID", null)
        id = patientId ?: ""

        if (patientId.isNullOrEmpty()) {
            progressDialog.dismiss()
            errorDialogBuilder(this, getString(R.string.patient_info_session_error_header), getString(R.string.patient_info_not_found), MainActivity::class.java)
            return
        }

        db.collection("patients").document(patientId).get()
            .addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    val patientData = documentSnapshot.data ?: return@addOnSuccessListener

                    binding.editLegalNameTIET.setText(AESEncryption().decrypt(patientData["legalName"].toString()))
                    binding.editClinicIdTIET.setText(patientData["clinicId"].toString())
                    binding.editDateOfBirthTIET.setText(AESEncryption().decrypt(patientData["dateOfBirth"].toString()))
                    binding.editGenderACTV.setText(
                        when (patientData["gender"].toString().toInt()) {
                            1 -> getString(R.string.register_gender_male)
                            2 -> getString(R.string.register_gender_female)
                            else -> ""
                        },
                        false
                    )
                    binding.editWeightTIET.setText(AESEncryption().decrypt(patientData["weight"].toString()))
                    binding.editHeightTIET.setText(AESEncryption().decrypt(patientData["height"].toString()))
                    binding.editRegisterHomeSysInput.setText(AESEncryption().decrypt(patientData["targetSys"].toString()))
                    binding.editRegisterHomeDiaInput.setText(AESEncryption().decrypt(patientData["targetDia"].toString()))

                    val photoUrl = patientData["photoUrl"].toString()
                    if (photoUrl.isNotEmpty()) {
                        storage.getReferenceFromUrl(photoUrl).getBytes(Long.MAX_VALUE).addOnSuccessListener { bytes ->
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            binding.editPhotoIV.setImageBitmap(bitmap)
                            binding.editPhotoTV.text = getString(R.string.profile_picture_info)
                            photoBA = bytes
                        }
                    }

                    progressDialog.dismiss()
                } else {
                    progressDialog.dismiss()
                    errorDialogBuilder(this, getString(R.string.patient_info_session_error_header), getString(R.string.patient_info_not_found), MainActivity::class.java)
                }
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                firebaseErrorDialog(this, e, db.collection("patients").document(patientId))
            }
    }

    @SuppressLint("StringFormatInvalid")
    private fun updatePatient() {
        progressDialog.setMessage(getString(R.string.update_loading_dialog))
        progressDialog.show()

        val photo = photoBA

        var gender = 0
        when (binding.editGenderACTV.text.toString()) {
            getString(R.string.register_gender_male) -> gender = 1
            getString(R.string.register_gender_female) -> gender = 2
        }

        val patient: MutableMap<String, Any> = hashMapOf(
            "legalName" to AESEncryption().encrypt(binding.editLegalNameTIET.text.toString().uppercase()),
            "dateOfBirth" to AESEncryption().encrypt(binding.editDateOfBirthTIET.text.toString()),
            "gender" to gender,
            "weight" to AESEncryption().encrypt(binding.editWeightTIET.text.toString()),
            "height" to AESEncryption().encrypt(binding.editHeightTIET.text.toString()),
            "targetSys" to AESEncryption().encrypt(binding.editRegisterHomeSysInput.text.toString()),
            "targetDia" to AESEncryption().encrypt(binding.editRegisterHomeDiaInput.text.toString()),
            "clinicId" to binding.editClinicIdTIET.text.toString().trim(),
            "bpStage" to "N/A"
        )

        val docRef = db.collection("patients").document(id)
        docRef.get().addOnSuccessListener { documentSnapshot ->
            if (documentSnapshot.exists()) {
                val nricDecrypted = AESEncryption().decrypt(id)
                val storageRef = storage.reference.child("images/$nricDecrypted.jpg")
                storageRef.putBytes(photo).addOnSuccessListener {
                    storageRef.downloadUrl.addOnSuccessListener { uri ->
                        val photoUrl = uri.toString()
                        patient["photoUrl"] = photoUrl

                        docRef.update(patient)
                            .addOnSuccessListener {
                                // Retrieve the document again to check the clinic ID
                                docRef.get().addOnSuccessListener { updatedSnapshot ->
                                    val updatedClinicId = updatedSnapshot.getString("clinicId").toString()

                                    // Check if the clinic ID matches the one in StaffSharedPreferences
                                    val staffClinicId = StaffSharedPreferences.getSharedPreferences(applicationContext).getString("clinicId", "")

                                    if (updatedClinicId != staffClinicId) {
                                        errorDialogBuilder(
                                            this,
                                            getString(R.string.enhance_edit_clinic_id_mismatch),
                                            getString(R.string.enhance_edit_clinic_id_error),
                                            MainActivity::class.java
                                        )
                                    } else {
                                        // If everything is fine, show success toast and navigate to ProfileActivity
                                        progressDialog.dismiss()

                                        Toast.makeText(
                                            this,
                                            getString(R.string.update_successful, AESEncryption().decrypt(id)),
                                            Toast.LENGTH_SHORT
                                        ).show()

                                        val profileActivityIntent = Intent(this, ProfileActivity::class.java)
                                        profileActivityIntent.extras?.putString("Source", "EditProfileActivity")
                                        startActivity(profileActivityIntent)
                                        finish()
                                    }
                                }.addOnFailureListener { e ->
                                    progressDialog.dismiss()
                                    firebaseErrorDialog(this, e, docRef)
                                }
                            }
                            .addOnFailureListener { e ->
                                progressDialog.dismiss()
                                firebaseErrorDialog(this, e, docRef)
                            }
                    }.addOnFailureListener { e ->
                        progressDialog.dismiss()
                        firebaseErrorDialog(this, e, storageRef, photo)
                    }
                }.addOnFailureListener { e ->
                    progressDialog.dismiss()
                    firebaseErrorDialog(this, e, storageRef, photo)
                }
            } else {
                progressDialog.dismiss()
                errorDialogBuilder(this, getString(R.string.update_error_header), getString(R.string.update_error_body), MainActivity::class.java)
            }
        }
    }

}
