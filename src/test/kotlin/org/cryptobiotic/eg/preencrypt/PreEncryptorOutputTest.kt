package org.cryptobiotic.eg.preencrypt

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.encrypt.cast
import org.cryptobiotic.eg.cli.ManifestBuilder
import org.cryptobiotic.eg.election.Manifest
import org.cryptobiotic.eg.publish.json.import
import org.cryptobiotic.eg.publish.json.publishJson
import org.cryptobiotic.eg.publish.makePublisher
import org.cryptobiotic.eg.publish.readElectionRecord
import org.cryptobiotic.util.ErrorMessages
import org.cryptobiotic.util.Testing
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.fail

private val random = Random

class PreEncryptorOutputTest {

    // multiple selections per contest
    @Test
    fun testMultipleSelections() {
        val ebuilder = ManifestBuilder("testMultipleSelections")
        val manifest: Manifest = ebuilder.addContest("onlyContest")
            .setVoteVariationType(Manifest.VoteVariationType.n_of_m, 2)
            .addSelection("selection1", "candidate1")
            .addSelection("selection2", "candidate2")
            .addSelection("selection3", "candidate3")
            .done()
            .build()

        runCompleteOutput(
            "src/test/data/workflow/allAvailableEc",
            "${Testing.testOut}/preencrypt/PreEncryptorOutputJson",
            "testPreencrypt",
            manifest,
            ::markBallotToLimit,
            true)
    }

    internal fun runCompleteOutput(
        input: String,
        output:String,
        ballot_id: String,
        manifest: Manifest,
        markBallot: (manifest: Manifest, pballot: PreEncryptedBallot) -> MarkedPreEncryptedBallot,
        show: Boolean,
    ) {
        if (show) println("===================================================================")

        val primaryNonce = UInt256.random()

        // pre-encrypt
        val electionRecord = readElectionRecord( input)
        val publicKey = electionRecord.jointPublicKey()!!
        val extendedBaseHash = electionRecord.extendedBaseHash()!!

        val preEncryptor = PreEncryptor(electionRecord.group, manifest, publicKey, extendedBaseHash, ::sigma)
        val pballot = preEncryptor.preencrypt(ballot_id, "ballotStyle", primaryNonce)
        if (show) pballot.show()

        // vote
        val markedBallot = markBallot(manifest, pballot)
        if (show) markedBallot.show()

        // record
        val recorder = Recorder(electionRecord.group, manifest, publicKey, extendedBaseHash, "device", ::sigma)
        val errs = ErrorMessages("MarkedBallot ${markedBallot.ballotId}")
        val pair = with(recorder) {
            markedBallot.record(primaryNonce, errs)
        }
        if (errs.hasErrors()) {
            println(errs)
            fail()
        }
        val (recordedBallot, ciphertextBallot) = pair!!

        // show record results
        if (show) {
            println("\nCiphertextBallot ${ciphertextBallot.ballotId}")
            for (contest in ciphertextBallot.contests) {
                println(" contest ${contest.contestId}")
                contest.selections.forEach {
                    println("   selection ${it.selectionId} = ${it.ciphertext}")
                }
            }
            println()
            recordedBallot.show()
            println()
        }

        // roundtrip through the serialization, which combines the recordedBallot
        val encryptedBallot = ciphertextBallot.cast()
        val json = encryptedBallot.publishJson(recordedBallot)
        val fullEncryptedBallot = json.import(electionRecord.group, ErrorMessages(""))!!

        // show what ends up in the election record
        if (show) {
            println("\nEncryptedBallot ${fullEncryptedBallot.ballotId}")
            for (contest in fullEncryptedBallot.contests) {
                println(" contest ${contest.contestId}")
                contest.selections.forEach {
                    println("   selection ${it.selectionId} = ${it.encryptedVote}")
                }
                contest.preEncryption?.show()
            }
            println()
        }

        val publisher = makePublisher(output, true)
        publisher.writeElectionInitialized(electionRecord.electionInit()!!)
        val sink = publisher.encryptedBallotSink("PreEncryptorOutputTest")
        sink.writeEncryptedBallot(fullEncryptedBallot)
    }

    // pick all selections 0..limit-1
    internal fun markBallotToLimit(pballot: PreEncryptedBallot): MarkedPreEncryptedBallot {
        val pcontests = mutableListOf<MarkedPreEncryptedContest>()
        for (pcontest in pballot.contests) {
            val shortCodes = mutableListOf<String>()
            val selections = mutableListOf<String>()
            val nselections = pcontest.selections.size
            val doneIdx = mutableSetOf<Int>()

            while (doneIdx.size < pcontest.contestLimit) {
                val idx = random.nextInt(nselections)
                if (!doneIdx.contains(idx)) {
                    shortCodes.add(sigma(pcontest.selections[idx].selectionHash))
                    selections.add(pcontest.selections[idx].selectionId)
                    doneIdx.add(idx)
                }
            }

            pcontests.add(
                MarkedPreEncryptedContest(
                    pcontest.contestId,
                    shortCodes,
                    selections,
                )
            )
        }

        return MarkedPreEncryptedBallot(
            pballot.ballotId,
            pballot.ballotStyleId,
            pcontests,
        )
    }
}