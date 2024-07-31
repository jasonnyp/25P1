package com.singhealth.enhance.activities.diagnosis

import android.content.Context
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.QuerySnapshot
import com.singhealth.enhance.R
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Determine patient's BP Stage based on the given Systolic and Diastolic values. Set recentDate to null if not necessary
// recentDate param is just for confirmation that the data is most recent (for profile updates)
object ResourcesHelper {
    fun getString(context: Context, resId: Int): String {
        return context.getString(resId)
    }
}

// Used to sort all patient records stored in db
// documents param passed into function after get() function from Firestore db
fun sortPatientVisits(documents: QuerySnapshot) : List<Diag> {
    // Add all BP readings into array
    val arr = ArrayList<Diag>()
    val inputDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
    // Adding all documents with Sys and Dia data into an array as a "Diag" Object
    for (document in documents) {
        val dateTimeString = document.get("date") as? String
        val dateTime = LocalDateTime.parse(dateTimeString, inputDateFormatter)
        val sysBP = document.get("averageSysBP") as? Long
        val diaBP = document.get("averageDiaBP") as? Long
        val clinicSys = document.get("clinicSysBP") as? Long
        val clinicDia = document.get("clinicDiaBP") as? Long
        val targetHomeSys = document.get("homeSysBPTarget") as? Long
        val targetHomeDia = document.get("homeDiaBPTarget") as? Long
        arr.add(
            Diag(
                dateTime.toString(),
                sysBP,
                diaBP,
                clinicSys,
                clinicDia,
                targetHomeSys,
                targetHomeDia
            )
        )
    }

    // Sort array by date in descending order
    val sortedArr = arr.sortedByDescending { it.date }

    return sortedArr
}

fun colourSet(context: Context, bp: Long, targetBP: Long): Int {
    return if (bp > targetBP) { // >=
        ContextCompat.getColor(context, R.color.diet)
    } else if (bp < targetBP){
        ContextCompat.getColor(context, R.color.lifestyle)
    } else {
        ContextCompat.getColor(context, R.color.yellow)
    }
}

fun showRecommendation(context: Context, optimal: String): String{
    return when (optimal) {
        ResourcesHelper.getString(context, R.string.well_controlled) -> {
            ResourcesHelper.getString(context, R.string.well_controlled_bp_recommendation_medical)
        }
        ResourcesHelper.getString(context, R.string.suboptimum) -> {
            ResourcesHelper.getString(context, R.string.suboptimum_bp_recommendation_medical)
        }
        else -> {
            ResourcesHelper.getString(context, R.string.no_recommendations)
        }
    }
}

fun bpControlStatus(
    context: Context,
    recentSys: Long, recentDia: Long,
    targetSys: Long, targetDia: Long
): String {

    val bpStage: String = when {
        recentSys < targetSys && recentDia < targetDia -> ResourcesHelper.getString(context, R.string.controlled_bp)
        else -> ResourcesHelper.getString(context, R.string.uncontrolled_bp)
    }
    return bpStage
}

fun bpControlStatus(context: Context, hypertensionLevel: String):String {
    return when (hypertensionLevel) {
        ResourcesHelper.getString(context, R.string.well_controlled_hypertension) -> {
            ResourcesHelper.getString(context, R.string.well_controlled)
        }
        ResourcesHelper.getString(context, R.string.white_coat_uncontrolled_hypertension) -> {
            ResourcesHelper.getString(context, R.string.well_controlled)
        }
        ResourcesHelper.getString(context, R.string.masked_hypertension) -> {
            ResourcesHelper.getString(context, R.string.suboptimum)
        }
        ResourcesHelper.getString(context, R.string.uncontrolled_hypertension) -> {
            ResourcesHelper.getString(context, R.string.suboptimum)
        }
        else -> {
            ResourcesHelper.getString(context, R.string.unknown_hypertension_status)
        }
    }
}

fun hypertensionStatus(
    context: Context,
    avgHomeSys: Long, avgHomeDia: Long,
    clinicSys: Long, clinicDia: Long,
    targetHomeSys: Long, targetHomeDia: Long,
):String {
    val targetClinicSys = targetHomeSys + 5
    val targetClinicDia = targetHomeDia + 5

    val controlledHomeBP: Boolean = (avgHomeSys < targetHomeSys && avgHomeDia < targetHomeDia)
    val controlledClinicBP: Boolean = (clinicSys < targetClinicSys && clinicDia < targetClinicDia)

    val hypertensionStatus: String = when {
        controlledHomeBP && controlledClinicBP -> ResourcesHelper.getString(context, R.string.well_controlled_hypertension)
        controlledHomeBP && !controlledClinicBP -> ResourcesHelper.getString(context, R.string.white_coat_uncontrolled_hypertension)
        !controlledHomeBP && controlledClinicBP -> ResourcesHelper.getString(context, R.string.masked_hypertension)
        else -> ResourcesHelper.getString(context, R.string.uncontrolled_hypertension)
    }
    return hypertensionStatus
}
