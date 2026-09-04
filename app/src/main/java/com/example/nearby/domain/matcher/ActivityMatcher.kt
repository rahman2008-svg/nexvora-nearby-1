package com.example.nearby.domain.matcher

import com.example.nearby.domain.model.AvailabilityStatus
import com.example.nearby.domain.model.UserProfile
import kotlin.math.min

data class MatchBreakdown(
    val totalScore: Int, // 0 - 100
    val sharedActivities: List<String>,
    val sharedInterests: List<String>,
    val sharedLanguages: List<String>,
    val availabilityMatch: Boolean
)

object ActivityMatcher {

    /**
     * Calculates deterministic local activity compatibility score between 0 and 100%.
     * No AI or remote algorithms used.
     */
    fun calculateMatch(
        myProfile: UserProfile,
        peerPrimaryActivity: String,
        peerActivities: List<String>,
        peerInterests: List<String>,
        peerLanguages: List<String>,
        peerAvailability: AvailabilityStatus
    ): MatchBreakdown {
        var rawScore = 0

        // 1. Same primary activity = +30
        val myActivitiesLower = myProfile.activities.map { it.trim().lowercase() }
        val peerPrimaryLower = peerPrimaryActivity.trim().lowercase()
        val hasPrimaryMatch = peerPrimaryLower.isNotEmpty() && myActivitiesLower.contains(peerPrimaryLower)
        if (hasPrimaryMatch) {
            rawScore += 30
        }

        // Shared activities: find intersection
        val peerActivitiesLower = peerActivities.map { it.trim().lowercase() }
        val sharedAct = myProfile.activities.filter { myAct ->
            peerActivitiesLower.contains(myAct.trim().lowercase())
        }
        // Additional secondary shared activities if not primary
        val extraActivities = sharedAct.size - (if (hasPrimaryMatch) 1 else 0)
        if (extraActivities > 0) {
            rawScore += min(extraActivities * 10, 20)
        }

        // 2. Shared interests = +10 each (up to +30)
        val peerInterestsLower = peerInterests.map { it.trim().lowercase() }
        val sharedInt = myProfile.interests.filter { myInt ->
            peerInterestsLower.contains(myInt.trim().lowercase())
        }
        rawScore += min(sharedInt.size * 10, 30)

        // 3. Shared languages = +10 each (up to +20)
        val peerLanguagesLower = peerLanguages.map { it.trim().lowercase() }
        val sharedLang = myProfile.languages.filter { myLang ->
            peerLanguagesLower.contains(myLang.trim().lowercase())
        }
        rawScore += min(sharedLang.size * 10, 20)

        // 4. Compatible availability = +20
        val isAvailabilityCompatible = when {
            myProfile.availability == AvailabilityStatus.OFFLINE || peerAvailability == AvailabilityStatus.OFFLINE -> false
            myProfile.availability == AvailabilityStatus.DO_NOT_DISTURB || peerAvailability == AvailabilityStatus.DO_NOT_DISTURB -> false
            myProfile.availability == AvailabilityStatus.AVAILABLE && peerAvailability == AvailabilityStatus.AVAILABLE -> true
            else -> false
        }
        if (isAvailabilityCompatible) {
            rawScore += 20
        }

        val finalScore = min(100, rawScore)

        return MatchBreakdown(
            totalScore = finalScore,
            sharedActivities = sharedAct,
            sharedInterests = sharedInt,
            sharedLanguages = sharedLang,
            availabilityMatch = isAvailabilityCompatible
        )
    }
}
