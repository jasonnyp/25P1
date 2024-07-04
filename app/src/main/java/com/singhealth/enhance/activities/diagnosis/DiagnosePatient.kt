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

fun showControlStatus(documents: QuerySnapshot, patientAge: Int, date : String?): String {
    // P1 2024
    // Control Status: How well the patient can control their BP (maintain BP under a limit),
    // <140/90 for >18 yrs and <150/90 for >60 yrs. Determined by taking the average of last 6
    // records (incl. most recent BP recording), if the average is under the limit, the patient
    // exhibits good BP Control, else they have bad BP Control
    var totalSys: Long = 0
    var totalDia: Long = 0

    // Returns an array of objects containing the Sys/Dia BP values and date
    var sortedVisits = sortPatientVisits(documents)

    // Check if a date is specified
    if (date != null) {
        // newSortedList contains all visits before and including the specified date
        val (newSortedList) = sortedVisits.partition{ it.date!! <= date }
        sortedVisits = newSortedList
    }


    // Fixed len represents number of visits to refer to when determining control status
    var len = sortedVisits.size - 1
    // When number of visits is less than 6, make len the size of list
    if (len > 5) {
        len = 5
    }

    // Sum all of the Sys and Dia BP Values from last 6 records (incl. scan)
    for (i in 0..len) {
        val entry = sortedVisits[i]
        println(entry.date)
        val sysData = entry.avgSysBP
        val diaData = entry.avgDiaBP
        if (sysData != null && diaData != null) {
            totalSys += sysData
            totalDia += diaData
        }
    }

    len += 1
    // Average Sys BP throughout all visits
    val avgSys = totalSys / len
    // Average Dia BP throughout all visits
    val avgDia = totalDia / len
    // Different Sys and Dia limits for different age groups
    if (patientAge >= 60) {
        val sysLimit: Long = 150
        val diaLimit: Long = 90
        return if (avgSys >= sysLimit || avgDia >= diaLimit) { // If either Sys or Dia BP exceed limit, patient has poor bp control
            controlStatusOutput(len, sysLimit, diaLimit, true)
        } else {
            controlStatusOutput(len, sysLimit, diaLimit, false)
        }
    } else if (patientAge >= 18) {
        val sysLimit: Long = 140
        val diaLimit: Long = 90
        return if (avgSys >= sysLimit || avgDia >= diaLimit) { // If either Sys or Dia BP exceed limit, patient has poor bp control
            controlStatusOutput(len - 1, sysLimit, diaLimit, true)
        } else {
            controlStatusOutput(len - 1, sysLimit, diaLimit, false)
        }
    } else {
        return controlStatusOutput(len - 1, avgSys, avgDia)
    }
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

fun controlStatusOutput(len: Int, sysLimit: Long, diaLimit: Long, hasPoorBlood: Boolean):String {
    val controlStat = when (len) {
        0 -> {
            "Patient's average blood pressure on the current visit is above ${sysLimit}/${diaLimit} mmHg"
        }
        1 -> {
            "Patient's average blood pressure on the current and last visit is above ${sysLimit}/${diaLimit} mmHg"
        }
        else -> {
            "Patient's average blood pressure on the current and last $len visits is above ${sysLimit}/${diaLimit} mmHg"
        }
    }

    return if (hasPoorBlood) {
        "Poor BP Control. $controlStat"
    } else {
        "Good BP Control. $controlStat"
    }
}

fun controlStatusOutput(len: Int, avgSys: Long, avgDia: Long):String {
    val controlStat = when (len) {
        0 -> {
            "Patient's average blood pressure on the current visit is ${avgSys}/${avgDia} mmHg"
        }
        1 -> {
            "Patient's average blood pressure on the current and last visit is above ${avgSys}/${avgDia} mmHg"
        }
        else -> {
            "Patient's average blood pressure on the current and last $len visits is above ${avgSys}/${avgDia} mmHg"
        }
    }
    return "Patient is too young to be relevant for the analysis. $controlStat"
}

fun bpControlStatus(context: Context, recentSys: Long, recentDia: Long, targetSys: Long, targetDia: Long): String {
    val defaultTargetSys: Long = when {
        targetSys != 0.toLong() -> targetSys
        else -> 135
    }
    val defaultTargetDia: Long = when {
        targetDia != 0.toLong() -> targetDia
        else -> 85
    }

    val bpStage: String = when {
        recentSys < defaultTargetSys && recentDia < defaultTargetDia -> ResourcesHelper.getString(context, R.string.controlled_bp)
        else -> ResourcesHelper.getString(context, R.string.uncontrolled_bp)
    }
    return bpStage
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
