package com.singhealth.enhance.activities.validation

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.storage.StorageReference
import com.singhealth.enhance.R
import com.singhealth.enhance.activities.MainActivity

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

fun errorDialogBuilder(context: Context, title: String, message: String) {
    MaterialAlertDialogBuilder(context)
        .setIcon(R.drawable.ic_error)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(ResourcesHelper.getString(context, R.string.ok_dialog)) { dialog, _ -> dialog.dismiss() }
        .show()
}

fun errorDialogBuilder(context: Context, title: String, message: String, activity: Class<*>) {
    MaterialAlertDialogBuilder(context)
        .setIcon(R.drawable.ic_error)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(ResourcesHelper.getString(context, R.string.yes_dialog)) { _, _ ->
            context.startActivity(Intent(context, activity))
            if (context is Activity) {
                context.finish()
            }
        }
        .setNegativeButton(ResourcesHelper.getString(context, R.string.no_dialog)) { dialog, _ -> dialog.dismiss() }
        .show()
}

fun errorDialogBuilder(context: Context, title: String, message: String, activity: Class<*>, icon: Int) {
    MaterialAlertDialogBuilder(context)
        .setIcon(icon)
        .setTitle(title)
        .setMessage(message)
        .setNegativeButton(ResourcesHelper.getString(context, R.string.no_dialog)) { dialog, _ -> dialog.dismiss() }
        .setPositiveButton(ResourcesHelper.getString(context, R.string.yes_dialog)) { _, _ ->
            context.startActivity(Intent(context, activity))
            if (context is Activity) {
                context.finish()
            }
        }
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

fun ocrTextErrorDialog(context: Context) {
    MaterialAlertDialogBuilder(context)
        .setIcon(R.drawable.ic_error)
        .setTitle(ResourcesHelper.getString(context, R.string.ocr_text_error_header))
        .setMessage(ResourcesHelper.getString(context, R.string.ocr_text_error_body))
        .setPositiveButton(ResourcesHelper.getString(context, R.string.dialog_positive_ok)) { dialog, _ -> dialog.dismiss() }
        .show()
}

