package org.cryptobiotic.eg.cli

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.decrypt.DecryptingTrusteeIF
import org.cryptobiotic.eg.election.*
import org.cryptobiotic.eg.publish.Consumer
import org.cryptobiotic.eg.publish.ElectionRecord
import org.cryptobiotic.eg.publish.makeConsumer
import org.cryptobiotic.eg.publish.readElectionRecord
import org.cryptobiotic.util.ErrorMessages

/** Show information from the election record. */
class RunShowElectionRecord {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val parser = ArgParser("RunElectionRecordShow")
            val inputDir by parser.option(
                ArgType.String,
                shortName = "in",
                description = "Directory containing input Election Record"
            ).required()
            val show by parser.option(
                ArgType.String,
                shortName = "show",
                description = "[all,constants,manifest,guardians,trustees,ballots,tally]"
            )
            val details by parser.option(
                ArgType.Boolean,
                shortName = "details",
                description = "show details"
            )
            val ballotStyle by parser.option(
                ArgType.String,
                description = "for just one ballot style",
                shortName = "ballotStyle"
            )
            parser.parse(args)

            val showSet = if (show == null) emptySet() else show!!.split(",").toSet()

            showElectionRecord(inputDir, ShowSet(showSet), details ?: false, ballotStyle)
        }

        class ShowSet(val want: Set<String>) {
            fun has(show: String) = want.contains("all") || want.contains(show)
        }

        fun showElectionRecord(inputDir: String, showSet: ShowSet, details: Boolean, ballotStyle : String?) {
            val consumer = makeConsumer(inputDir)
            val electionRecord = readElectionRecord(consumer)
            println("ShowElectionRecord from $inputDir, stage = ${electionRecord.stage()}")
            if (ballotStyle != null) println("  for just ballot style='${ballotStyle}'")

            val config = electionRecord.config()
            println(" config numberOfGuardians = ${config.numberOfGuardians}")
            println(" config quorum  = ${config.quorum}")
            println(" config metadata  = ${config.metadata}")
            if (showSet.has("constants")) {
                print(" ${config.constants.show()}")

            }
            if (showSet.has("manifest")) {
                print( show(electionRecord.manifest(), details, ballotStyle) )
            }
            println()

            if (electionRecord.stage() == ElectionRecord.Stage.CONFIG) {
                return
            }

            val init = electionRecord.electionInit()
            if (init != null) {
                println(" init guardians.size = ${init.guardians.size}")
                println(" init metadata  = ${init.metadata}")
                if (showSet.has("guardians")) {
                    print(init.guardians.showGuardians(details))
                }
                println()

                if (showSet.has("trustees")) {
                    val trusteeDir = "$inputDir/private_data/trustees"
                    print(init.guardians.showTrustees(consumer, trusteeDir))
                }
                println()
            }

            if (electionRecord.stage() == ElectionRecord.Stage.INIT) {
                return
            }

            val ecount = consumer.iterateAllEncryptedBallots { true }.count()
            println(" $ecount encryptedBallots")
            println()

            if (showSet.has("ballots")) {
                consumer.iterateAllEncryptedBallots { true }.forEach {
                    println(" ballot ${it.ballotId} style ${it.ballotStyleId}")
                }
            }

            if (electionRecord.stage() == ElectionRecord.Stage.ENCRYPTED) {
                return
            }

            val tally = electionRecord.tallyResult()
            if (tally != null) {
                println(" encryptedTally ncontests = ${tally.encryptedTally.contests.size}")
                val nselections = tally.encryptedTally.contests.sumOf { it.selections.size }
                println(" encryptedTally nselections = $nselections")
                println(" metadata  = ${tally.metadata}")
                println()
            }

            if (electionRecord.stage() == ElectionRecord.Stage.TALLIED) {
                return
            }

            val dtally = electionRecord.decryptionResult()
            if (dtally != null) {
                println(" decryptedTally ${dtally.decryptedTally.show(details, electionRecord.manifest())}")
            }
        }

        fun ElectionConstants.show(): String {
            val constants = this
            return buildString {
                appendLine("ElectionConstants name= ${constants.name} type= ${constants.type}")
                constants.constants.forEach { key, value ->
                    appendLine("  $key == $value")
                }
            }
        }

        fun show(manifest: Manifest, details: Boolean, wantBallotStyle: String?=null): String {
            return buildString {
                appendLine("\nElection Manifest scopeId=${manifest.electionScopeId} type=${manifest.electionType} spec=${manifest.specVersion}")
                appendLine("  gpus: ${manifest.geopoliticalUnits}")
                if (wantBallotStyle == null) {
                    appendLine("  styles: [")
                    manifest.ballotStyleIds.forEach {
                        val count = manifest.contestsForBallotStyle(it)!!.map { it.selections.size }.sum()
                        appendLine("    $it, ncontests = ${manifest.contestsForBallotStyle(it)!!.size}, nselections= $count")
                    }
                    appendLine("  ]")
                } else {
                    val ballotStyle = manifest.ballotStyleIds.find { it == wantBallotStyle }
                    if (ballotStyle == null) {
                        appendLine("  NOT FOUND ballot style '$wantBallotStyle'")
                        return toString()
                    } else {
                        appendLine("  ballot style: ${ballotStyle}")
                    }
                }
                val wantContests =
                    if (wantBallotStyle == null) manifest.contests else manifest.styleToContestsMap[wantBallotStyle]
                        ?: emptyList()
                append(wantContests.showContests(details))
            }
        }

        fun List<Manifest.ContestDescription>.showContests(details: Boolean): String {
            val builder = StringBuilder(5000)
            this.forEach { contest ->
                if (details) {
                    builder.append("  Contest ${contest.sequenceOrder} '${contest.contestId}': geo=${contest.geopoliticalUnitId}, ")
                    if (contest.contestSelectionLimit != 1 || contest.optionSelectionLimit != 1)  builder.append("limits=${contest.contestSelectionLimit}/${contest.optionSelectionLimit}")
                    if (contest.voteVariation != Manifest.VoteVariationType.one_of_m) builder.append(", ${contest.voteVariation}")
                    builder.appendLine()
                    contest.selections.forEach {
                        builder.append("   ${it.sequenceOrder} '${it.selectionId}'")
                        if (it.candidateId != it.selectionId) builder.append(" cand=${it.candidateId}")
                        builder.appendLine()
                    }
                    builder.appendLine()
                } else {
                    builder.append("  ${contest.contestId}: ")
                    contest.selections.forEach {
                        builder.append(" ${it.candidateId} (${it.selectionId});")
                    }
                    builder.appendLine()
                }
            }
            return builder.toString()
        }

        fun List<Guardian>.showGuardians(details: Boolean): String {
            val builder = StringBuilder(5000)
            builder.appendLine(" Guardians")
            this.sortedBy { it.guardianId }.forEach { guardian ->
                builder.appendLine("  ${guardian.guardianId} xcoord=${guardian.xCoordinate} nproofs=${guardian.coefficientProofs.size}")
                if (details) {
                    guardian.coefficientProofs.forEach { proof ->
                        builder.appendLine("   ${proof.show()}")
                    }
                }
            }
            return builder.toString()
        }

        fun List<Guardian>.showTrustees(consumer: Consumer, trusteeDir: String): String {
            val builder = StringBuilder(5000)
            builder.appendLine(" Trustees")
            this.sortedBy { it.guardianId }.forEach { guardian ->
                val result: Result<DecryptingTrusteeIF, ErrorMessages> =
                    consumer.readTrustee(trusteeDir, guardian.guardianId)
                if (result is Ok) {
                    val trustee = result.unwrap()
                    builder.appendLine("  ${trustee.id()} xcoord=${trustee.xCoordinate()}")
                } else {
                    builder.appendLine(result.getError().toString())
                }
            }
            return builder.toString()
        }

        fun SchnorrProof.show(): String {
            val builder = StringBuilder(5000)
            builder.append("SchnorrProof key=${this.publicCommitment.toStringShort()}")
            builder.append(" challenge=${this.challenge}")
            builder.append(" response=${this.response}")
            return builder.toString()
        }
    }
}