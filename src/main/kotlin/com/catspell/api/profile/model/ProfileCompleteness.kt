package com.catspell.api.profile.model

object ProfileCompleteness {
    fun missingFields(profile: UserProfile?): List<String> {
        if (profile == null) return listOf("profile")

        val missing = mutableListOf<String>()
        if (profile.displayName.isBlank()) missing.add("displayName")
        if (profile.gender.isBlank()) missing.add("gender")
        if (profile.genderPreference.isBlank()) missing.add("genderPreference")
        if (profile.location == null) missing.add("location")
        return missing
    }
}
