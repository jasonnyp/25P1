package com.singhealth.enhance.activities.error

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.storage.StorageReference
import com.singhealth.enhance.R
import com.singhealth.enhance.activities.MainActivity
import com.singhealth.enhance.activities.ocr.ScanActivity

object ResourcesHelper {
    // Retrieves the string from the res/values/strings.xml file, the same as getString() on activity files
    fun getString(context: Context, resId: Int): String {
        return context.getString(resId)
    }
    // Same as above, but includes a single string input for string values that depend on an input
    fun getString(context: Context, @StringRes resId: Int, vararg formatArgs: Any): String {
        return context.getString(resId, *formatArgs)
    }
}

fun internetConnectionCheck(context: Context) {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val hasConnection: Boolean

    val network = connectivityManager.activeNetwork
    val capabilities = connectivityManager.getNetworkCapabilities(network)
    hasConnection = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false

    if (!hasConnection) {
        noInternetErrorDialog(context)
    }
}

// Error Dialogs

fun noInternetErrorDialog(context: Context) {
    MaterialAlertDialogBuilder(context)
        .setTitle(ResourcesHelper.getString(context, R.string.reconnect_error_header))
        .setMessage(ResourcesHelper.getString(context, R.string.reconnect_error_body))
        .setPositiveButton(ResourcesHelper.getString(context, R.string.ok_dialog)) { dialog, _ ->
            dialog.dismiss()
        }
        .setNeutralButton(ResourcesHelper.getString(context, R.string.reconnect_dialog_button)) { dialog, _ ->
            dialog.dismiss()
            internetConnectionCheck(context)
        }
        .show()
}

fun firebaseErrorDialog(context: Context, e: Exception, function:(String) -> Unit, string: String) {
    MaterialAlertDialogBuilder(context)
        .setTitle(ResourcesHelper.getString(context, R.string.firebase_error_header))
        .setMessage(ResourcesHelper.getString(context, R.string.firebase_error_body, e))
        .setPositiveButton(ResourcesHelper.getString(context, R.string.ok_dialog)) { dialog, _ -> dialog.dismiss() }
        .setNeutralButton(ResourcesHelper.getString(context, R.string.reconnect_dialog_button)) { dialog, _ ->
            dialog.dismiss()
            function(string)
        }
        .show()
}

fun firebaseErrorDialog(context: Context, e: Exception, document: DocumentReference) {
    MaterialAlertDialogBuilder(context)
        .setTitle(ResourcesHelper.getString(context, R.string.firebase_error_header))
        .setMessage(ResourcesHelper.getString(context, R.string.firebase_error_body, e))
        .setPositiveButton(ResourcesHelper.getString(context, R.string.ok_dialog)) { dialog, _ -> dialog.dismiss() }
        .setNeutralButton(ResourcesHelper.getString(context, R.string.reconnect_dialog_button)) { dialog, _ ->
            dialog.dismiss()
            document.get()
        }
        .show()
}

fun firebaseErrorDialog(context: Context, e: Exception, storage: StorageReference, photo: ByteArray) {
    MaterialAlertDialogBuilder(context)
        .setTitle(ResourcesHelper.getString(context, R.string.firebase_error_header))
        .setMessage(ResourcesHelper.getString(context, R.string.firebase_error_body, e))
        .setPositiveButton(ResourcesHelper.getString(context, R.string.ok_dialog)) { dialog, _ -> dialog.dismiss() }
        .setNeutralButton(ResourcesHelper.getString(context, R.string.reconnect_dialog_button)) { dialog, _ ->
            dialog.dismiss()
            storage.putBytes(photo)
        }
        .show()
}

fun patientNotFoundErrorDialog(context: Context) {
    MaterialAlertDialogBuilder(context)
        .setIcon(R.drawable.ic_error)
        .setTitle(ResourcesHelper.getString(context, R.string.main_activity_patient_header))
        .setMessage(ResourcesHelper.getString(context, R.string.main_activity_patient_header))
        .setPositiveButton(ResourcesHelper.getString(context, R.string.ok_dialog)) { dialog, _ -> dialog.dismiss() }
        .show()
}

fun loginFailedErrorDialog(context: Context) {
    MaterialAlertDialogBuilder(context)
        .setIcon(R.drawable.ic_error)
        .setTitle(ResourcesHelper.getString(context, R.string.login_error_header))
        .setMessage(ResourcesHelper.getString(context, R.string.login_error_body))
        .setPositiveButton(ResourcesHelper.getString(context, R.string.ok_dialog)) { dialog, _ -> dialog.dismiss() }
        .show()
}

fun patientNotFoundInSessionErrorDialog(context: Context) {
    MaterialAlertDialogBuilder(context)
        .setTitle(ResourcesHelper.getString(context, R.string.patient_info_session_error_header))
        .setMessage(ResourcesHelper.getString(context, R.string.patient_info_session_error_body))
        .setPositiveButton(ResourcesHelper.getString(context, R.string.ok_dialog)) { dialog, _ ->
            dialog.dismiss()
            context.startActivity(Intent(context, MainActivity::class.java))
            if (context is Activity) {
                context.finish()
            }
        }
        .show()
}

fun patientHistoryNotFoundErrorDialog(context: Context, e: Exception) {
    MaterialAlertDialogBuilder(context)
        .setIcon(R.drawable.ic_error)
        .setTitle(ResourcesHelper.getString(context, R.string.patient_history_not_found_error_header))
        .setMessage(ResourcesHelper.getString(context, R.string.patient_history_not_found_error_body, e))
        .setPositiveButton(ResourcesHelper.getString(context, R.string.ok_dialog)) { dialog, _ -> dialog.dismiss() }
        .show()
}

fun noPatientPhotoErrorDialog(context: Context) {
    MaterialAlertDialogBuilder(context)
        .setIcon(R.drawable.ic_error)
        .setTitle(ResourcesHelper.getString(context, R.string.register_image_verification_header))
        .setMessage(ResourcesHelper.getString(context, R.string.register_image_verification_body))
        .setPositiveButton(ResourcesHelper.getString(context, R.string.ok_dialog)) { dialog, _ -> dialog.dismiss() }
        .show()
}

fun patientExistsErrorDialog(context: Context, progressDialog: ProgressDialog) {
    MaterialAlertDialogBuilder(context)
        .setIcon(R.drawable.ic_error)
        .setTitle(ResourcesHelper.getString(context, R.string.register_exist_error_header))
        .setMessage(ResourcesHelper.getString(context, R.string.register_exist_error_body))
        .setNegativeButton(ResourcesHelper.getString(context, R.string.cancel_dialog)) { dialog, _ ->
            dialog.dismiss()
            progressDialog.dismiss()
        }
        .setPositiveButton(ResourcesHelper.getString(context, R.string.ok_dialog)) { _, _ ->
            context.startActivity(Intent(context, MainActivity::class.java))
            if (context is Activity) {
                context.finish()
            }
        }
        .show()
}

fun ocrImageErrorDialog(context: Context, e: Exception) {
    MaterialAlertDialogBuilder(context)
        .setIcon(R.drawable.ic_error)
        .setTitle(ResourcesHelper.getString(context, R.string.ocr_image_error_header))
        .setMessage(ResourcesHelper.getString(context, R.string.ocr_image_error_body, e))
        .setPositiveButton(ResourcesHelper.getString(context, R.string.ok_dialog)) { dialog, _ -> dialog.dismiss() }
        .show()
}

fun ocrTextErrorDialog(context: Context) {
    MaterialAlertDialogBuilder(context)
        .setIcon(R.drawable.ic_error)
        .setTitle(ResourcesHelper.getString(context, R.string.ocr_text_error_header))
        .setMessage(ResourcesHelper.getString(context, R.string.ocr_text_error_body))
        .setPositiveButton(ResourcesHelper.getString(context, R.string.dialog_positive_ok)) { dialog, _ -> dialog.dismiss() }
        .show()
}

fun ocrInadequateReadingErrorDialog(context: Context) {
    MaterialAlertDialogBuilder(context)
        .setIcon(R.drawable.ic_error)
        .setTitle(ResourcesHelper.getString(context, R.string.verify_scan_inadequate_reading_header))
        .setMessage(ResourcesHelper.getString(context, R.string.verify_scan_inadequate_reading_body))
        .setNegativeButton(ResourcesHelper.getString(context, R.string.no_dialog)) { _, _ ->
            context.startActivity(Intent(context, ScanActivity::class.java))
            if (context is Activity) {
                context.finish()
            }
        }
        .setPositiveButton(ResourcesHelper.getString(context, R.string.yes_dialog)) { dialog, _ -> dialog.dismiss() }
        .show()
}
