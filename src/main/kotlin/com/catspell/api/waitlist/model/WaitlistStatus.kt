package com.catspell.api.waitlist.model

/** Lifecycle of a waitlist entry: joined (PENDING) → double opt-in confirmed (CONFIRMED) → converted (INVITED). */
enum class WaitlistStatus {
    PENDING,
    CONFIRMED,
    INVITED
}
