package org.cryptobiotic.eg.input

import org.cryptobiotic.eg.election.*
import kotlin.math.abs
import kotlin.random.Random

/** Create nballots randomly generated fake Ballots, used for testing.  */
class RandomBallotProvider(val manifest: Manifest, val nballots: Int = 11) {
    var addWriteIns = false
    var useSequential = false
    var sequentialId = 1

    fun withWriteIns() : RandomBallotProvider {
        this.addWriteIns = true
        return this
    }

    fun withSequentialIds(): RandomBallotProvider {
        this.useSequential = true
        return this
    }

    fun ballots(ballotStyleId: String? = null): List<PlaintextBallot> {
        val ballots = mutableListOf<PlaintextBallot>()
        for (i in 0 until nballots) {
            val styleIdx = Random.nextInt(manifest.ballotStyles.size)
            val useStyle = ballotStyleId ?: manifest.ballotStyles[styleIdx].ballotStyleId
            val ballotId = if (useSequential) "id-" + sequentialId++ else "id" + Random.nextInt()
            ballots.add(getFakeBallot(manifest, useStyle, ballotId))
        }
        return ballots
    }

    fun makeBallot(): PlaintextBallot {
        val styleIdx = Random.nextInt(manifest.ballotStyles.size)
        val useStyle = manifest.ballotStyles[styleIdx].ballotStyleId
        val ballotId = if (useSequential) "id-" + sequentialId++ else "id" + Random.nextInt()
        return getFakeBallot(manifest, useStyle, ballotId)
    }

    fun getFakeBallot(manifest: Manifest, ballotStyleId: String?, ballotId: String): PlaintextBallot {
        val useStyle = if (ballotStyleId != null) {
            if (manifest.ballotStyles.find { it.ballotStyleId == ballotStyleId } == null) {
                throw RuntimeException("BallotStyle '$ballotStyleId' not found in manifest ballotStyles= ${manifest.ballotStyles}")
            }
            ballotStyleId
        } else {
            val styleIdx = Random.nextInt(manifest.ballotStyles.size)
            manifest.ballotStyles[styleIdx].ballotStyleId
        }
        val contests = mutableListOf<PlaintextBallot.Contest>()
        for (contestp in manifest.contestsForBallotStyle(useStyle)!!) {
            contests.add(makeContestFrom(contestp as Manifest.ContestDescription))
        }
        val sn = abs(Random.nextLong())
        return PlaintextBallot(ballotId, useStyle, contests, sn)
    }

    fun makeContestFrom(contest: Manifest.ContestDescription): PlaintextBallot.Contest {
        var voted = 0
        val selections =  mutableListOf<PlaintextBallot.Selection>()
        val nselections = contest.selections.size

        for (selection_description in contest.selections) {
            val selection: PlaintextBallot.Selection = getRandomVoteForSelection(selection_description, nselections, voted < contest.contestSelectionLimit)
            selections.add(selection)
            voted += selection.vote
        }
        val choice = Random.nextInt(nselections)
        val writeins = if (!addWriteIns || choice != 0) emptyList() else {
            listOf("writein")
        }
        return PlaintextBallot.Contest(
            contest.contestId,
            contest.sequenceOrder,
            selections,
            writeins,
        )
    }

    companion object {
        fun getRandomVoteForSelection(description: Manifest.SelectionDescription, nselections: Int, moreAllowed : Boolean): PlaintextBallot.Selection {
            val choice = Random.nextInt(nselections)
            return PlaintextBallot.Selection(
                description.selectionId, description.sequenceOrder,
                if (choice == 0 && moreAllowed) 1 else 0,
            )
        }
    }
}