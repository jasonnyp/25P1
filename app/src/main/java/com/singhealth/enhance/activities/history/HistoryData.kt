package com.singhealth.enhance.activities.history

data class HistoryData(
    val date: String?,
    val dateFormatted: String?,
    val avgSysBP: Long?,
    val avgDiaBP: Long?,
    val homeSysBPTarget: Long?,
    val homeDiaBPTarget: Long?,
    val clinicSysBPTarget: Long?,
    val clinicDiaBPTarget: Long?
)