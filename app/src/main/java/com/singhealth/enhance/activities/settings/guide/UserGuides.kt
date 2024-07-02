package com.singhealth.enhance.activities.settings.guide

data class UserGuides(
    val title: String,
    val desc: String,
    var isExpandable: Boolean = false
)