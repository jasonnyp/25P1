package com.singhealth.enhance.activities.diagnosis

import android.content.Context
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

fun diagnosePatient(context: Context, recentSys: Long, recentDia: Long, recentDate: String): String {
    // Log recent date, sys and dia data in Logcat
    println("Date: $recentDate, Most Recent Sys: $recentSys, Most Recent Dia: $recentDia")

    val defaultTargetSys: Long = 135
    val defaultTargetDia: Long = 85

    // Determine patient BP Stage
    val bpStage: String = when {
        recentSys < defaultTargetSys && recentDia < defaultTargetDia -> ResourcesHelper.getString(context, R.string.well_controlled_hypertension)
        recentSys > defaultTargetSys && recentDia < defaultTargetDia -> ResourcesHelper.getString(context, R.string.white_coat_uncontrolled_hypertension)
        recentSys < defaultTargetSys && recentDia > defaultTargetDia -> ResourcesHelper.getString(context, R.string.masked_hypertension)
        recentSys > defaultTargetSys && recentDia > defaultTargetDia -> ResourcesHelper.getString(context, R.string.uncontrolled_hypertension)
        else -> "N/A"
    }
    return bpStage
}

fun diagnosePatient(context: Context, recentSys: Long, recentDia: Long): String {
    // Log recent date, sys and dia data in Logcat
    println("Most Recent Sys: $recentSys, Most Recent Dia: $recentDia")

    val defaultTargetSys: Long = 135
    val defaultTargetDia: Long = 85

    // Determine patient BP Stage
    val bpStage: String = when {
        recentSys < defaultTargetSys && recentDia < defaultTargetDia -> ResourcesHelper.getString(context, R.string.well_controlled_hypertension)
        recentSys > defaultTargetSys && recentDia < defaultTargetDia -> ResourcesHelper.getString(context, R.string.white_coat_uncontrolled_hypertension)
        recentSys < defaultTargetSys && recentDia > defaultTargetDia -> ResourcesHelper.getString(context, R.string.masked_hypertension)
        recentSys > defaultTargetSys && recentDia > defaultTargetDia -> ResourcesHelper.getString(context, R.string.uncontrolled_hypertension)
        else -> "N/A"
    }

    return bpStage
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
        arr.add(
            Diag(
                dateTime.toString(),
                sysBP,
                diaBP
            )
        )
    }

    // Sort array by date in descending order
    val sortedArr = arr.sortedByDescending { it.date }

    return sortedArr
}

fun showRecommendation(context: Context, bpStage: String) : ArrayList<String>{
    var dietText = ResourcesHelper.getString(context, R.string.no_recommendations)
    var lifestyleText = ResourcesHelper.getString(context, R.string.no_recommendations)
    var medicalText = ResourcesHelper.getString(context, R.string.no_recommendations)
    val ouputList = ArrayList <String>()
    println(bpStage)
    println(ResourcesHelper.getString(context, R.string.well_controlled_hypertension))
    println(ResourcesHelper.getString(context, R.string.masked_hypertension))
    println(ResourcesHelper.getString(context, R.string.white_coat_uncontrolled_hypertension))
    println(ResourcesHelper.getString(context, R.string.uncontrolled_hypertension))
    when (bpStage) {
        ResourcesHelper.getString(context, R.string.well_controlled_hypertension) ->
            { dietText = ResourcesHelper.getString(context, R.string.well_controlled_bp_recommendation_diet)
            lifestyleText = ResourcesHelper.getString(context, R.string.well_controlled_bp_recommendation_lifestyle)
            medicalText = ResourcesHelper.getString(context, R.string.well_controlled_bp_recommendation_medical) }

        ResourcesHelper.getString(context, R.string.masked_hypertension) ->
        { dietText = ResourcesHelper.getString(context, R.string.well_controlled_bp_recommendation_diet)
            lifestyleText = ResourcesHelper.getString(context, R.string.well_controlled_bp_recommendation_lifestyle)
            medicalText = ResourcesHelper.getString(context, R.string.well_controlled_bp_recommendation_medical) }

        ResourcesHelper.getString(context, R.string.white_coat_uncontrolled_hypertension) ->
        { dietText = ResourcesHelper.getString(context, R.string.suboptimum_bp_recommendation_diet)
            lifestyleText = ResourcesHelper.getString(context, R.string.suboptimum_bp_recommendation_lifestyle)
            medicalText = ResourcesHelper.getString(context, R.string.suboptimum_bp_recommendation_medical) }

        ResourcesHelper.getString(context, R.string.uncontrolled_hypertension) ->
        { dietText = ResourcesHelper.getString(context, R.string.suboptimum_bp_recommendation_diet)
            lifestyleText = ResourcesHelper.getString(context, R.string.suboptimum_bp_recommendation_lifestyle)
            medicalText = ResourcesHelper.getString(context, R.string.suboptimum_bp_recommendation_medical) }

    }
    ouputList.add(dietText)
    ouputList.add(lifestyleText)
    ouputList.add(medicalText)

    return ouputList
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

fun hypertensionStatus(context: Context, avgHomeSys: Long, avgHomeDia: Long):String {
    val targetHomeSys = 135
    val targetHomeDia = 85

    val hypertensionStatus: String = when {
        avgHomeSys < targetHomeSys && avgHomeDia < targetHomeDia -> ResourcesHelper.getString(context, R.string.well_controlled_hypertension)
        else -> ResourcesHelper.getString(context, R.string.uncontrolled_hypertension)
    }

    return hypertensionStatus
}

fun hypertensionStatus(
    context: Context,
    avgHomeSys: Long, avgHomeDia: Long,
    officeSys: Long, officeDia: Long,
    targetHomeSys: Long, targetHomeDia: Long
):String {
    val targetOfficeSys = targetHomeSys + 5
    val targetOfficeDia = targetHomeDia + 5

    val controlledHomeBP: Boolean = (avgHomeSys < targetHomeSys && avgHomeDia < targetHomeDia)
    val controlledOfficeBP: Boolean = (officeSys < targetOfficeSys && officeDia < targetOfficeDia)
    val uncontrolledHomeBP: Boolean = (avgHomeSys > targetHomeSys || avgHomeDia > targetHomeDia)
    val uncontrolledOfficeBP: Boolean = (officeSys > targetOfficeSys || officeDia > targetOfficeDia)

    val hypertensionStatus: String = when {
        controlledHomeBP && controlledOfficeBP -> ResourcesHelper.getString(context, R.string.well_controlled_hypertension)
        controlledHomeBP && uncontrolledOfficeBP -> ResourcesHelper.getString(context, R.string.white_coat_uncontrolled_hypertension)
        uncontrolledHomeBP && controlledOfficeBP -> ResourcesHelper.getString(context, R.string.masked_hypertension)
        uncontrolledHomeBP && uncontrolledOfficeBP -> ResourcesHelper.getString(context, R.string.uncontrolled_hypertension)
        else -> "N/A"
    }
    return hypertensionStatus
}
