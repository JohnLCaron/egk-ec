package org.cryptobiotic.eg.election

/** Interface used in the crypto routines for easy mocking. */
interface ManifestIF {
    val ballotStyles: List<Manifest.BallotStyle> // in order by id
    val contests: List<Contest> // in order by sequence number

    interface Contest {
        val contestId: String
        val sequenceOrder: Int
        val selections: List<Selection> // in order by sequence number
    }

    interface Selection {
        val selectionId: String
        val sequenceOrder: Int
    }

    /** get the list of valid contests for the given ballotStyle name */
    fun contestsForBallotStyle(ballotStyleId : String): List<Contest>?

    fun findContest(contestId: String): Contest?

    /** get the contest selection limit (aka votesAllowed) for the given contest id */
    fun contestLimit(contestId : String): Int

    /** get the option selection limit for the given contest id */
    fun optionLimit(contestId : String): Int
}