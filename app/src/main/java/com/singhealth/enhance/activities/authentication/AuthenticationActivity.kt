package com.singhealth.enhance.activities.authentication

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.singhealth.enhance.activities.MainActivity
import com.singhealth.enhance.R
import com.singhealth.enhance.databinding.ActivityAuthenticationBinding
import com.singhealth.enhance.security.AESEncryption
import java.util.concurrent.TimeUnit

class AuthenticationActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAuthenticationBinding

    private var verificationCode: String? = null
    private var resendingToken: PhoneAuthProvider.ForceResendingToken? = null
    private var mAuth = FirebaseAuth.getInstance()
    private var timeoutSeconds: Long = 60

    private var staffID: String? = null
    private var password: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAuthenticationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val userInfoBundle = intent.extras
        staffID = userInfoBundle!!.getString("staffID")
        password = userInfoBundle.getString("password")
        // Retrieve phone number defined in LoginActivity
        val staffPhoneNumber = "+65${
            AESEncryption().decrypt(
                userInfoBundle.getString("staffPhoneNumber").toString()
            )
        }"

        sendOtp(staffPhoneNumber, false)

        binding.codeTIET.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.codeTIL.error = null
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Hardcoded OTP is "112288" for staff ID "staff" with phone number "12345678"
        binding.verifyCodeBtn.setOnClickListener {
            if (binding.codeTIET.text.isNullOrEmpty()) {
                binding.codeTIL.error = "Code cannot be empty"
            } else {
                val code = binding.codeTIET.text.toString()
                val credential = PhoneAuthProvider.getCredential(verificationCode!!, code)

                signIn(credential)
            }
        }

        binding.resendCodeTV.setOnClickListener {
            sendOtp(staffPhoneNumber, true)
        }
    }

    private fun sendOtp(phoneNumber: String?, isResend: Boolean) {
        setInProgress(true)

        val builder = phoneNumber?.let {
            PhoneAuthOptions.newBuilder(mAuth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    override fun onVerificationCompleted(phoneAuthCredential: PhoneAuthCredential) {
                        signIn(phoneAuthCredential)
                        setInProgress(false)
                    }

                    override fun onVerificationFailed(e: FirebaseException) {
                        MaterialAlertDialogBuilder(this@AuthenticationActivity)
                            .setIcon(R.drawable.ic_error)
                            .setTitle("SMS server communication error")
                            .setMessage("The app is currently experiencing difficulties in establishing a connection with the SMS server.\n\nIf this issue persist, please reach out to your IT helpdesk and provide them with the following error code for further assistance:\n\n${e.message}")
                            .setPositiveButton(resources.getString(R.string.ok_dialog)) { _, _ ->
                                startActivity(
                                    Intent(
                                        this@AuthenticationActivity,
                                        LoginActivity::class.java
                                    )
                                )
                                finish()
                            }
                            .show()
                    }

                    override fun onCodeSent(
                        s: String,
                        forceResendingToken: PhoneAuthProvider.ForceResendingToken
                    ) {
                        super.onCodeSent(s, forceResendingToken)
                        verificationCode = s
                        resendingToken = forceResendingToken

                        MaterialAlertDialogBuilder(this@AuthenticationActivity)
                            .setIcon(R.drawable.ic_textsms)
                            .setTitle("Verification code sent")
                            .setMessage("Enter the unique verification code sent to your registered mobile number via SMS to proceed.")
                            .setPositiveButton(resources.getString(R.string.ok_dialog)) { dialog, _ -> dialog.dismiss() }
                            .show()

                        startResendTimer()
                        setInProgress(false)
                    }
                })
        }

        if (isResend) {
            if (builder != null) {
                resendingToken?.let { builder.setForceResendingToken(it).build() }
                    ?.let { PhoneAuthProvider.verifyPhoneNumber(it) }
            }
        } else {
            if (builder != null) {
                PhoneAuthProvider.verifyPhoneNumber(builder.build())
            }
        }
    }

    private fun setInProgress(inProgress: Boolean) {
        if (inProgress) {
            binding.verifyCodeBtn.visibility = View.GONE
            binding.progressBar.visibility = View.VISIBLE
            binding.resendCodeTV.visibility = View.GONE
        } else {
            binding.verifyCodeBtn.visibility = View.VISIBLE
            binding.progressBar.visibility = View.GONE
            binding.resendCodeTV.visibility = View.VISIBLE
        }
    }

    private fun signIn(phoneAuthCredential: PhoneAuthCredential?) {
        mAuth.signInWithCredential(phoneAuthCredential!!).addOnCompleteListener { task ->
            setInProgress(false)
            if (task.isSuccessful) {
                val mainIntent = Intent(this, MainActivity::class.java)
                startActivity(mainIntent)
                finish()
            } else {
                MaterialAlertDialogBuilder(this@AuthenticationActivity)
                    .setIcon(R.drawable.ic_error)
                    .setTitle("Code validation unsuccessful")
                    .setMessage("The code you have provided is invalid.")
                    .setNegativeButton(resources.getString(R.string.cancel_dialog)) { _, _ ->
                        startActivity(
                            Intent(
                                this@AuthenticationActivity,
                                LoginActivity::class.java
                            )
                        )
                        finish()
                    }
                    .setPositiveButton(resources.getString(R.string.try_again_dialog)) { dialog, _ -> dialog.dismiss() }
                    .show()
            }
        }
    }

    private fun startResendTimer() {
        binding.resendCodeTV.isEnabled = false
        val timeoutMillis = 60000L

        object : CountDownTimer(timeoutMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsUntilFinished = millisUntilFinished / 1000
                binding.resendCodeTV.text =
                    if (secondsUntilFinished > 1) {
                        "You may request another verification code in $secondsUntilFinished seconds."
                    } else {
                        "You may request another verification code in $secondsUntilFinished second."
                    }
            }

            override fun onFinish() {
                binding.resendCodeTV.text = getString(R.string.resend_verification_code)
                binding.resendCodeTV.isEnabled = true
            }
        }.start()
    }
}