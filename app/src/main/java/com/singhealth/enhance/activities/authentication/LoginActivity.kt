package com.singhealth.enhance.activities.authentication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.singhealth.enhance.R
import com.singhealth.enhance.activities.MainActivity
import com.singhealth.enhance.activities.validation.errorDialogBuilder
import com.singhealth.enhance.databinding.ActivityLoginBinding
import com.singhealth.enhance.security.AESEncryption
import com.singhealth.enhance.security.StaffSharedPreferences
import java.util.Calendar

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        auth = Firebase.auth
        if (isDarkMode()) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setGreeting()
        //setupDefaultAccount()

        binding.staffIDTIET.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.staffIDTIL.error = null
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.passwordTIET.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.passwordTIL.error = null
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.loginBtn.setOnClickListener {
            if (validateFields()) {
                val email = binding.staffIDTIET.text.toString().trim() + "@enhance.com"
                val password = binding.passwordTIET.text.toString().trim()
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            val user = auth.currentUser
                            user?.let {
                                val uid = it.uid
                                val query = db.collection("staff").get()
                                query.addOnSuccessListener { querySnapshot ->
                                    var staffFound = false
                                    for (document in querySnapshot.documents) {
                                        val staffData = document.data
                                        val accountNumber = staffData?.get("account_number") as? String
                                        if (accountNumber == uid) {
                                            val clinicId = staffData["clinicId"] as? String
                                            if (clinicId != null) {
                                                StaffSharedPreferences.getSharedPreferences(applicationContext).edit().apply {
                                                    putString("clinicId", clinicId)
                                                    apply()
                                                }
                                                println("Clinic ID successfully saved: ${StaffSharedPreferences.getSharedPreferences(applicationContext).getString("clinicId", "")}")
                                                Toast.makeText(
                                                    this,
                                                    getString(R.string.login_success_header),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                startActivity(Intent(this, MainActivity::class.java))
                                                finish()
                                                staffFound = true
                                                break
                                            }
                                        }
                                    }
                                    if (!staffFound) {
                                        println("No matching staff document found")
                                        errorDialogBuilder(this, getString(R.string.login_error_header), getString(R.string.login_error_body))
                                    }
                                }.addOnFailureListener { exception ->
                                    println("Firestore query failed: ${exception.message}")
                                    errorDialogBuilder(this, getString(R.string.login_error_header), getString(R.string.login_error_body))
                                }
                            }
                        } else {
                            val exception = task.exception
                            println("Firebase Authentication failed: ${exception?.message}")
                            errorDialogBuilder(this, getString(R.string.login_error_header), getString(R.string.login_error_body))
                        }
                    }
            }
        }


    }

    private fun setGreeting() {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..11 -> binding.greetingTV.text = getString(R.string.good_morning)
            in 12..17 -> binding.greetingTV.text = getString(R.string.good_afternoon)
            else -> binding.greetingTV.text = getString(R.string.good_evening)
        }
    }

    private fun validateFields(): Boolean {
        var valid = true

        if (binding.staffIDTIET.editableText.isNullOrEmpty()) {
            binding.staffIDTIL.error = getString(R.string.login_validate_staff)
            valid = false
        }
        if (binding.passwordTIET.editableText.isNullOrEmpty()) {
            binding.passwordTIL.error = getString(R.string.login_validate_password)
            valid = false
        }

        return valid
    }

    private fun isDarkMode(): Boolean {
        val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return sharedPref.getBoolean("is_dark_mode", false) // default to light mode
    }
}
