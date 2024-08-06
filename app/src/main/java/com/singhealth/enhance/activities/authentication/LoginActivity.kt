package com.singhealth.enhance.activities.authentication

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.singhealth.enhance.R
import com.singhealth.enhance.activities.MainActivity
import com.singhealth.enhance.activities.error.errorDialogBuilder
import com.singhealth.enhance.databinding.ActivityLoginBinding
import java.util.Calendar

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth


    private val db = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        auth = Firebase.auth
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
                val email = binding.staffIDTIET.text.toString() + "@enhance.com"
                val password = binding.passwordTIET.text.toString()
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        } else {
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
}