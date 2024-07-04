package com.singhealth.enhance.activities.diagnosis

import com.google.firebase.firestore.QuerySnapshot
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Determine patient's BP Stage based on the given Systolic and Diastolic values. Set recentDate to null if not necessary
// recentDate param is just for confirmation that the data is most recent (for profile updates)
fun diagnosePatient(recentSys: Long, recentDia: Long, recentDate: String): String {
    // Log recent date, sys and dia data in Logcat
    println("Date: $recentDate, Most Recent Sys: $recentSys, Most Recent Dia: $recentDia")

    // Determine patient BP Stage
    val bpStage: String = when {
        recentSys < 90 || recentDia < 60 -> "Low BP"
        recentSys in 90..119 && recentDia in 60..79 -> "Normal BP"
        recentSys in 120..129 && recentDia < 80 -> "Elevated BP"
        recentSys in 130..139 || recentDia in 80..89 -> "Hypertension Stage 1"
        recentSys in 140..179 || recentDia in 90..119 -> "Hypertension Stage 2"
        recentSys > 180 || recentDia > 120 -> "Hypertensive Crisis"
        else -> "N/A"
    }
    return bpStage
}

fun diagnosePatient(recentSys: Long, recentDia: Long): String {
    // Log recent date, sys and dia data in Logcat
    println("Most Recent Sys: $recentSys, Most Recent Dia: $recentDia")

    // Determine patient BP Stage
    val bpStage: String = when {
        recentSys < 90 || recentDia < 60 -> "Low BP"
        recentSys in 90..119 && recentDia in 60..79 -> "Normal BP"
        recentSys in 120..129 && recentDia < 80 -> "Elevated BP"
        recentSys in 130..139 || recentDia in 80..89 -> "Hypertension Stage 1"
        recentSys in 140..179 || recentDia in 90..119 -> "Hypertension Stage 2"
        recentSys > 180 || recentDia > 120 -> "Hypertensive Crisis"
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

fun showRecommendation(bpStage: String) : ArrayList<String>{
    // P1 2024 Version
    // Provide categories of recommendation based on the patient's current BP Stage
    var dietText = "No Recommendations\n"
    var lifestyleText = "No Recommendations\n"
    var medicalText = "No Recommendations\n"
    val ouputList = ArrayList <String>()
    when (bpStage) {

        "Low BP" -> { dietText = "Continue maintaining healthy lifestyle.\n"
            lifestyleText = "Continue maintaining healthy lifestyle.\n"
            medicalText = "Continue maintaining healthy lifestyle.\n" }

        "Normal BP" -> { dietText = "Continue maintaining healthy lifestyle.\n"
                       lifestyleText = "Continue maintaining healthy lifestyle.\n"
                       medicalText = "Continue maintaining healthy lifestyle.\n" }

        "Elevated BP" -> {
            dietText= "- Lower sodium intake (< 3.6g / day)\n"
            lifestyleText = "- Increase physical activity (2.5 - 5 hours / week)\n" +
                    "- Maintain healthy weight (BMI < 22.9)\n" +
                    "- Need sufficient sleep (>7 hours / night)\n"}

        "Hypertension Stage 1" -> {
            dietText = "-Have a healthy diet\n" +
                "- Lower sodium intake (< 2g / day)\n" +
                "- Limit caffeine\n\n"
            lifestyleText = "- Manage stress\n" +
                "- Increase physical activity (2.5 - 5 hours / week)\n" +
                "- Maintain healthy weight (BMI < 22.9)\n" +
                "- Stop smoking and/or drinking\n" +
                "- Need sufficient sleep (>7 hours / night)\n\n"
             medicalText = "-Go for checkup regularly\n"
        }

        "Hypertension Stage 2" -> {
            dietText = "- Healthy diet\n" +
                "- Lower sodium intake (< 1.5g / day)\n" +
                "- Limit caffeine\n"
            lifestyleText = "- Manage stress\n" +
                "- Increase physical activity (2.5 - 5 hours / week)\n" +
                "- Maintain healthy weight (BMI < 22.9)\n" +
                "- Stop smoking and/or drinking\n" +
                "- Need sufficient sleep (>7 hours / night)\n"
            medicalText="- Take prescribed medications\n" +
                "- Go for check up regularly\n"
        }

        "Hypertensive Crisis" -> {
            dietText = "GO HOSPITAL NOW\n"
            lifestyleText = "GO HOSPITAL NOW\n"
            medicalText="GO HOSPITAL NOW\n"
        }
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
