package org.cryptobiotic.eg.election

/** Interface used in the crypto routines. */
interface ManifestIF {
    val ballotStyleIds: List<String> // in order by id
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

    /** get the sorted contests for the given ballotStyle */
    fun contestsForBallotStyle(ballotStyleId : String): List<Contest>?

    /** get the contest for the given contestId */
    fun findContest(contestId: String): Contest?

    /** get the contest selection limit (aka votesAllowed) for the given contest id */
    fun contestLimit(contestId : String): Int

    /** get the option selection limit for the given contest id */
    fun optionLimit(contestId : String): Int
}