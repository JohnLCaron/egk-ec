package org.cryptobiotic.eg.election

/** The ElectionGuard Manifest: defines the candidates, contests, and associated information for a specific election. */
class Manifest(
    val electionScopeId: String,
    val specVersion: String,
    val electionType: ElectionType,
    val startDate: String, // ISO 8601 formatted date/time
    val endDate: String, // ISO 8601 formatted date/time
    val geopoliticalUnits: List<GeopoliticalUnit>,
    ballotStylesInput: List<BallotStyle>,
    contestsInput: List<ContestDescription>,
    val candidates: List<Candidate>,
    val contactInformation: ContactInformation?,
    val name: List<Language>,
    val parties: List<Party>,
) : ManifestIF {
    // we need deterministic ordering
    override val ballotStyles: List<BallotStyle> = ballotStylesInput.sortedBy { it.ballotStyleId }
    override val contests: List<ContestDescription> = contestsInput.sortedBy { it.sequenceOrder }

    /** Map of ballotStyleId to all Contests that use it. */
    val styleToContestsMap = mutableMapOf<String, List<ContestDescription>>() // key = ballotStyleId
    init {
        val gpuToContests: Map<String, List<ContestDescription>> = contests.groupBy { it.geopoliticalUnitId } // key = geopoliticalUnitId
        ballotStylesInput.forEach { style ->
            val contestSet = mutableSetOf<ContestDescription>()
            style.geopoliticalUnitIds.forEach {
                val contestList = gpuToContests[it]
                if (contestList != null) {
                    contestSet.addAll(contestList)
                }
            }
            styleToContestsMap[style.ballotStyleId] = contestSet.toList().sortedBy {it.sequenceOrder}
        }
    }

    override fun contestsForBallotStyle(ballotStyleId: String): List<ManifestIF.Contest>? {
        return styleToContestsMap[ballotStyleId]
    }

    override fun findContest(contestId: String): ManifestIF.Contest? {
        return contestMap[contestId]
    }

    override fun contestLimit(contestId : String) : Int {
        return contestMap[contestId]?.contestSelectionLimit ?: 1
    }

    override fun optionLimit(contestId : String) : Int {
        return contestMap[contestId]?.optionSelectionLimit ?: 1
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Manifest) return false

        if (electionScopeId != other.electionScopeId) return false
        if (specVersion != other.specVersion) return false
        if (electionType != other.electionType) return false
        if (startDate != other.startDate) return false
        if (endDate != other.endDate) return false
        if (geopoliticalUnits != other.geopoliticalUnits) return false
        if (parties != other.parties) return false
        if (candidates != other.candidates) return false
        if (ballotStyles != other.ballotStyles) return false
        if (name != other.name) return false
        if (contactInformation != other.contactInformation) return false
        if (contests != other.contests) return false
        if (styleToContestsMap != other.styleToContestsMap) return false

        return true
    }

    override fun hashCode(): Int {
        var result = electionScopeId.hashCode()
        result = 31 * result + specVersion.hashCode()
        result = 31 * result + electionType.hashCode()
        result = 31 * result + startDate.hashCode()
        result = 31 * result + endDate.hashCode()
        result = 31 * result + geopoliticalUnits.hashCode()
        result = 31 * result + parties.hashCode()
        result = 31 * result + candidates.hashCode()
        result = 31 * result + ballotStyles.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + (contactInformation?.hashCode() ?: 0)
        result = 31 * result + contests.hashCode()
        result = 31 * result + styleToContestsMap.hashCode()
        return result
    }

    /** Map of contestId to contests. */
    val contestMap : Map<String, ContestDescription> by
    lazy {
        contests.associateBy { it.contestId }
    }

    /** Map "$contestId/$selectionId" to candidateId. */
    val selectionCandidate : Map<String, String> by
    lazy {
        contests.map { contest -> contest.selections.map { "${contest.contestId}/${it.selectionId}" to it.candidateId } }.flatten().toMap()
    }

    /**
     * The structure and type of one contest in the election.
     * @see [Civics Common Standard Data Specification](https://developers.google.com/elections-data/reference/contest)
     */
    class ContestDescription(
        override val contestId: String,
        override val sequenceOrder: Int,
        val geopoliticalUnitId: String,
        val voteVariation: VoteVariationType,
        val numberElected: Int,
        val contestSelectionLimit: Int, // contest selection limit = L, spec 2.0 p 17.
        val name: String,
        selectionsInput: List<SelectionDescription>,
        val ballotTitle: String?,
        val ballotSubtitle: String?,
        val optionSelectionLimit: Int = 1, // option selection limit = R, spec 2.0 p 17.
    ) : ManifestIF.Contest {
        override val selections: List<SelectionDescription> = selectionsInput.sortedBy { it.sequenceOrder }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ContestDescription) return false

            if (contestId != other.contestId) return false
            if (sequenceOrder != other.sequenceOrder) return false
            if (geopoliticalUnitId != other.geopoliticalUnitId) return false
            if (voteVariation != other.voteVariation) return false
            if (numberElected != other.numberElected) return false
            if (contestSelectionLimit != other.contestSelectionLimit) return false
            if (name != other.name) return false
            if (ballotTitle != other.ballotTitle) return false
            if (ballotSubtitle != other.ballotSubtitle) return false
            if (optionSelectionLimit != other.optionSelectionLimit) return false
            if (selections != other.selections) return false

            return true
        }

        override fun hashCode(): Int {
            var result = contestId.hashCode()
            result = 31 * result + sequenceOrder
            result = 31 * result + geopoliticalUnitId.hashCode()
            result = 31 * result + voteVariation.hashCode()
            result = 31 * result + numberElected
            result = 31 * result + contestSelectionLimit
            result = 31 * result + name.hashCode()
            result = 31 * result + (ballotTitle?.hashCode() ?: 0)
            result = 31 * result + (ballotSubtitle?.hashCode() ?: 0)
            result = 31 * result + optionSelectionLimit
            result = 31 * result + selections.hashCode()
            return result
        }
    }

    /**
     * A ballot selection for a specific candidate in a contest.
     * @see [Civics Common Standard Data Specification](https://developers.google.com/elections-data/reference/ballot-selection)
     */
    data class SelectionDescription(
        override val selectionId: String,
        override val sequenceOrder: Int,
        val candidateId: String,
    ) : ManifestIF.Selection

    /**
     * The type of election.
     * @see [Civics Common Standard Data Specification](https://developers.google.com/elections-data/reference/election-type)
     */
    enum class ElectionType {
        unknown,

        /** For an election held typically on the national day for elections. */
        general,

        /**
         * For a primary election that is for a specific party where voter eligibility is based on
         * registration.
         */
        partisan_primary_closed,

        /**
         * For a primary election that is for a specific party where voter declares desired party or
         * chooses in private.
         */
        partisan_primary_open,

        /** For a primary election without a specified type, such as a nonpartisan primary. */
        primary,

        /**
         * For an election to decide a prior contest that ended with no candidate receiving a
         * majority of the votes.
         */
        runoff,

        /**
         * For an election held out of sequence for special circumstances, for example, to fill a
         * vacated office.
         */
        special,

        /**
         * Used when the election type is not listed in this enumeration. If used, include a
         * specific value of the OtherType element.
         */
        other
    }

    /**
     * The type of geopolitical unit.
     * @see [Civics Common Standard Data Specification](https://developers.google.com/elections-data/reference/reporting-unit-type)
     */
    enum class ReportingUnitType {
        unknown,

        /** Used to report batches of ballots that might cross precinct boundaries. */
        ballot_batch,

        /** Used for a ballot-style area that's generally composed of precincts. */
        ballot_style_area,

        /** Used as a synonym for a county. */
        borough,

        /** Used for a city that reports results or for the district that encompasses it. */
        city,

        /** Used for city council districts. */
        city_council,

        /**
         * Used for one or more precincts that have been combined for the purposes of reporting. If
         * the term ward is used interchangeably with combined precinct, use combined-precinct for
         * the ReportingUnitType.
         */
        combined_precinct,

        /** Used for national legislative body districts. */
        congressional,

        /** Used for a country. */
        country,

        /**
         * Used for a county or for the district that encompasses it. Synonymous with borough and
         * parish in some localities.
         */
        county,

        /** Used for county council districts. */
        county_council,

        /** Used for a dropbox for absentee ballots. */
        drop_box,

        /** Used for judicial districts. */
        judicial,

        /**
         * Used as applicable for various units such as towns, townships, villages that report
         * votes, or for the district that encompasses them.
         */
        municipality,

        /** Used for a polling place. */
        polling_place,

        /** Used if the terms for ward or district are used interchangeably with precinct. */
        precinct,

        /** Used for a school district. */
        school,

        /** Used for a special district. */
        special,

        /** Used for splits of precincts. */
        split_precinct,

        /** Used for a state or for the district that encompasses it. */
        state,

        /** Used for a state house or assembly district. */
        state_house,

        /** Used for a state senate district. */
        state_senate,

        /**
         * Used for type of municipality that reports votes or for the district that encompasses it.
         */
        town,

        /**
         * Used for type of municipality that reports votes or for the district that encompasses it.
         */
        township,

        /** Used for a utility district. */
        utility,

        /**
         * Used for a type of municipality that reports votes or for the district that encompasses
         * it.
         */
        village,

        /** Used for a vote center. */
        vote_center,

        /** Used for combinations or groupings of precincts or other units. */
        ward,

        /** Used for a water district. */
        water,

        /**
         * Used for other types of reporting units that aren't included in this enumeration. If
         * used, provide the item's custom type in an OtherType element.
         */
        other
    }

    /**
     * Enumeration for contest algorithm or rules in the contest.
     * @see [Civics Common Standard Data Specification](https://developers.google.com/elections-data/reference/vote-variation)
     */
    enum class VoteVariationType {
        /** Each voter can select up to one option. */
        one_of_m,

        /** Approval voting, where each voter can select as many options as desired. */
        approval,

        /**
         * Borda count, where each voter can rank the options, and the rankings are assigned point
         * values.
         */
        borda,

        /** Cumulative voting, where each voter can distribute their vote to up to N options. */
        cumulative,

        /** A 1-of-m method where the winner needs more than 50% of the vote to be elected. */
        majority,

        /** A method where each voter can select up to N options. */
        n_of_m,

        /**
         * A 1-of-m method where the option with the most votes is elected, regardless of whether
         * the option has more than 50% of the vote.
         */
        plurality,

        /**
         * A proportional representation method, which is any system that elects winners in
         * proportion to the total vote. For the single transferable vote (STV) method, use rcv
         * instead.
         */
        proportional,

        /** Range voting, where each voter can select a score for each option. */
        range,

        /**
         * Ranked choice voting (RCV), where each voter can rank the options, and the ballots are
         * counted in rounds. Also known as instant-runoff voting (IRV) and the single transferable
         * vote (STV).
         */
        rcv,

        /**
         * A 1-of-m method where the winner needs more than some predetermined fraction of the vote
         * to be elected, and where the fraction is more than 50%. For example, the winner might
         * need three-fifths or two-thirds of the vote.
         */
        super_majority,

        /**
         * The vote variation is a type that isn't included in this enumeration. If used, provide
         * the item's custom type in an OtherType element.
         */
        other
    }

    /** Every ballot has a BallotStyle, which determines which contests are on the ballot. */
    data class BallotStyle(
        val ballotStyleId: String,
        val geopoliticalUnitIds: List<String>,
        val partyIds: List<String>,
        val imageUri: String?,
    ) {
        override fun toString() =
            buildString {
                append("'$ballotStyleId': $geopoliticalUnitIds")
            }
    }

    /** A candidate in a contest. */
    data class Candidate(
        val candidateId: String,
        val name: String?,
        val partyId: String?,
        val imageUri: String?,
        val isWriteIn: Boolean,
    )  {
        constructor(candidateId: String) : this(candidateId, null, null, null, false)
    }

    /**
     * Contact information about persons, boards of authorities, organizations, etc.
      */
    data class ContactInformation(
        val name: String?,
        val addressLine: List<String>,
        val email: String?,
        val phone: String?,
    )

    /**
     * A physical or virtual unit of representation or vote/seat aggregation. Use this entity to
     * define geopolitical units such as cities, districts, jurisdictions, or precincts to associate
     * contests, offices, vote counts, or other information with those geographies.
     * @see [Civics Common Standard Data Specification](https://developers.google.com/elections-data/reference/gp-unit)
     */
    data class GeopoliticalUnit(
        val geopoliticalUnitId: String,
        val name: String,
        val type: ReportingUnitType,
        val contactInformation: String?,
    ) {
        override fun toString() =
            buildString {
                append("$geopoliticalUnitId: '$name'")
                if (type != ReportingUnitType.unknown) append(", $type")

            }
    }

    /**
     * The ISO-639 language code.
     *
     * @see [ISO 639](https://en.wikipedia.org/wiki/ISO_639)
     */
    data class Language(
        val value: String,
        val language: String,
    )

    /**
     * A political party.
     * @see [Civics Common Standard Data Specification](https://developers.google.com/elections-data/reference/party)
     */
    data class Party(
        val partyId: String,
        val name: String,
        val abbreviation: String?,
        val color: String?,
        val logoUri: String?,
    ) {
        constructor(partyId: String) : this(partyId,"",null, null, null)
    }


}