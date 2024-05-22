package org.cryptobiotic.eg.verifier

import com.github.michaelbull.result.*

import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.core.ecgroup.VecGroups
import org.cryptobiotic.eg.core.intgroup.Primes4096
import org.cryptobiotic.eg.election.*
import org.cryptobiotic.eg.publish.ElectionRecord
import org.cryptobiotic.util.ErrorMessages
import org.cryptobiotic.util.Stats
import org.cryptobiotic.util.Stopwatch

class Verifier(val record: ElectionRecord, val nthreads: Int = 11) {
    val manifest: ManifestIF
    val jointPublicKey: ElGamalPublicKey
    val He: UInt256
    val group = record.group

    init {
        manifest = record.manifest()

        if (record.stage() < ElectionRecord.Stage.INIT) { // fake
            He = UInt256.random()
            jointPublicKey = ElGamalPublicKey(group.ONE_MOD_P)
        } else {
            jointPublicKey = record.jointPublicKey()!!
            He = record.extendedBaseHash()!!
        }
    }

    fun verify(stats : Stats, showTime : Boolean = false): Boolean {
        println("\n****Verify election record in = ${record.topdir()}\n")
        val stopwatch = Stopwatch()
        val config = record.config()

        var parametersOk = verifyParameters(config, record.manifestBytes());
        println(" 1. verifyParameters= $parametersOk")

        if (record.stage() < ElectionRecord.Stage.INIT) {
            println("election record stage = ${record.stage()}, stopping verification now\n")
            if (showTime) println("   verify ${stopwatch.took()}")
            return (parametersOk is Ok)
        }

        val guardiansOk = verifyGuardianPublicKey()
        println(" 2. verifyGuardianPublicKeys= $guardiansOk")

        val publicKeyOk = verifyElectionPublicKey()
        println(" 3. verifyElectionPublicKey= $publicKeyOk")

        val baseHashOk = verifyExtendedBaseHash()
        println(" 4. verifyExtendedBaseHash= $baseHashOk")

        val initOk = (parametersOk is Ok) && (guardiansOk is Ok) && (publicKeyOk is Ok) && (baseHashOk is Ok)
        if (record.stage() < ElectionRecord.Stage.ENCRYPTED) {
            println("election record stage = ${record.stage()}, stopping verification now\n")
            if (showTime) println("   verify 2,3,4 ${stopwatch.took()}")
            return initOk
        }

        // encryption and vote limits
        val encryptionVerifier = VerifyEncryptedBallots(group, manifest, jointPublicKey, He, config, nthreads)
        // Note we are validating all ballots, not just CAST, and including preencrypted
        val eerrs = ErrorMessages("")
        val (ballotsOk, nballots) = encryptionVerifier.verifyBallots(record.encryptedAllBallots { true }, eerrs, stats)
        println(" 5,6,15,16,17,18. verify $nballots EncryptedBallots = $ballotsOk")
        if (!ballotsOk) {
            println(eerrs)
        }

        val chainOk = if (!config.chainConfirmationCodes) true else {
            val chainErrs = ErrorMessages("")
            val ok = encryptionVerifier.verifyConfirmationChain(record.consumer(), chainErrs)
            println(" 7. verifyConfirmationChain $ok")
            if (!ok) {
                println(chainErrs)
            }
            ok
        }

        if (record.stage() < ElectionRecord.Stage.TALLIED) {
            println("election record stage = ${record.stage()}, stopping verification now\n")
            return initOk && ballotsOk && chainOk
        }

        // tally accumulation
        val tallyVerifier = VerifyTally(group, encryptionVerifier.aggregator)
        val tallyErrs = ErrorMessages("")
        val tallyOk = tallyVerifier.verify(record.encryptedTally()!!, tallyErrs, showTime)
        println(" 8. verifyBallotAggregation $tallyOk")
        if (!tallyOk) {
            println(tallyErrs)
        }

        if (record.stage() < ElectionRecord.Stage.DECRYPTED) {
            println("election record stage = ${record.stage()}, stopping verification now\n")
            return initOk && ballotsOk && chainOk && tallyOk
        }

        // tally decryption
        val decryptionVerifier = VerifyDecryption(group, manifest, jointPublicKey, He)
        val tdErrs = ErrorMessages("")
        val tdOk = decryptionVerifier.verify(record.decryptedTally()!!, isBallot = false, tdErrs, stats)
        println(" 9,10,11. verifyTallyDecryption $tdOk")
        if (!tdOk) {
            println(tdErrs)
        }

        // 12, 13, 14 challenged ballots
        val challengedErrs = ErrorMessages("")
        val (challengedOk, nchallenged)  = decryptionVerifier.verifyChallengedBallots(record.challengedBallots(), nthreads, challengedErrs, stats, showTime)
        println(" 12,13,14. verify $nchallenged ChallengedBallots $challengedOk")
        if (!challengedOk) {
            println(challengedErrs)
        }

        val allOk = initOk && ballotsOk && chainOk && tallyOk && tdOk && challengedOk

        println("verify allOK = $allOk\n")
        return allOk
    }

    // Verification Box 1
    private fun verifyParameters(config : ElectionConfig, manifestBytes: ByteArray): Result<Boolean, String> {
        val check: MutableList<Result<Boolean, String>> = mutableListOf()
        val configConstants = config.constants

        if (configConstants.protocolVersion != "v2.0.0" && configConstants.protocolVersion != "v2.1.0") {
            check.add(Err("  1.A The election record protocolVersion is unknown: '${configConstants.protocolVersion}'"))
        }

        if (group.constants.type == GroupType.IntegerGroup) {
            val largePrime = configConstants.constants["largePrime"]
            if (largePrime == null || !largePrime.toByteArray().normalize(512).contentEquals(Primes4096.largePrimeBytes)) {
                check.add(Err("  1.B The large prime is not equal to p defined in Section 3.1.1"))
            }
            val smallPrime = configConstants.constants["smallPrime"]
            if (smallPrime == null || !smallPrime.toByteArray().normalize(32).contentEquals(Primes4096.smallPrimeBytes)) {
                check.add(Err("  1.C The small prime is not equal to q defined in Section 3.1.1"))
            }
            val cofactor = configConstants.constants["cofactor"]
            if (cofactor == null || !cofactor.toByteArray().normalize(512).contentEquals(Primes4096.residualBytes)) {
                check.add(Err("  1.D The cofactor is not equal to r defined in Section 3.1.1"))
            }
            val generator = configConstants.constants["generator"]
            if (generator == null || !generator.toByteArray().normalize(512).contentEquals(Primes4096.generatorBytes)) {
                check.add(Err("  1.E The generator is not equal to g defined in Section 3.1.1"))
            }
        } else {
            val vecGroup: VecGroups = VecGroups.NAMED_PARAMS.get("P-256")!!

            val primeModulus = configConstants.constants["primeModulus"]!!

            if (primeModulus == null || !primeModulus.toByteArray().contentEquals(vecGroup.p.toByteArray())) {
                check.add(Err("  1.B The primeModulus is not equal to p defined in P-256"))
            }
            val order = configConstants.constants["order"]
            if (order == null || !order.toByteArray().contentEquals(vecGroup.n.toByteArray())) {
                check.add(Err("  1.C The order is not equal to n defined in P-256"))
            }
            val a = configConstants.constants["a"]
            if (a == null || !a.toByteArray().contentEquals(vecGroup.a.toByteArray())) {
                check.add(Err("  1.D The factor a is not equal to a defined in P-256"))
            }
            val b = configConstants.constants["b"]
            if (b == null || !b.toByteArray().contentEquals(vecGroup.b.toByteArray())) {
                check.add(Err("  1.D The factor b is not equal to b defined in P-256"))
            }
            val gx = configConstants.constants["g.x"]
            if (gx == null || !gx.toByteArray().contentEquals(vecGroup.gx.toByteArray())) {
                check.add(Err("  1.E The generatr g.x is not equal to g.x defined in P-256"))
            }
            val gy = configConstants.constants["g.y"]
            if (gy == null || !gy.toByteArray().contentEquals(vecGroup.gy.toByteArray())) {
                check.add(Err("  1.E The generatr g.y is not equal to g.y defined in P-256"))
            }
        }

        val Hp = parameterBaseHash(config.constants)
        if (Hp != config.parameterBaseHash) {
            check.add(Err("  1.F The parameter base hash does not match eq 4"))
        }
        val Hm = manifestHash(Hp, manifestBytes)
        if (Hm != config.manifestHash) {
            check.add(Err("  1.G The manifest hash does not match eq 5"))
        }
        val Hb = electionBaseHash(Hp, Hm, config.numberOfGuardians, config.quorum)
        if (Hb != config.electionBaseHash) {
            check.add(Err("  1.H The election base hash does not match eq 6"))
        }

        return check.merge()
    }

    // Verification Box 2
    private fun verifyGuardianPublicKey(): Result<Boolean, String> {
        val checkProofs: MutableList<Result<Boolean, String>> = mutableListOf()
        for (guardian in this.record.guardians()) {
            guardian.coefficientProofs.forEachIndexed { index, proof ->
                val result = proof.validate(guardian.xCoordinate, index)
                if (result is Err) {
                    checkProofs.add(Err("  2. Guardian ${guardian.guardianId} has invalid proof for coefficient $index " +
                        result.unwrapError()
                    ))
                }
            }
        }
        return checkProofs.merge()
    }

    // Verification 3 (Election public-key validation)
    // An election verifier must verify the correct computation of the joint election public key.
    // (3.A) The value Ki is in Z_p^r and K_i  != 1 mod p
    private fun verifyElectionPublicKey(): Result<Boolean, String> {
        val errors = mutableListOf<Result<Boolean, String>>()

        val guardiansSorted = this.record.guardians().sortedBy { it.xCoordinate }
        guardiansSorted.forEach {
            val Ki = it.publicKey()
            if (!Ki.isValidElement()) {
                errors.add(Err("  3.A publicKey Ki (${it.guardianId} is not in Zp^r"))
            }
            if (Ki == group.ONE_MOD_P) {
                errors.add(Err("  3.A publicKey Ki is equal to ONE_MOD_P"))
            }
        }

        val jointPublicKeyComputed = guardiansSorted.map { it.publicKey() }.reduce { a, b -> a * b }
        if (!jointPublicKey.equals(jointPublicKeyComputed)) {
            errors.add(Err("  3.B jointPublicKey K does not equal computed K = Prod(K_i)"))
        }
        return errors.merge()
    }

    private fun verifyExtendedBaseHash(): Result<Boolean, String> {
        val errors = mutableListOf<Result<Boolean, String>>()
        val guardiansSorted = this.record.guardians().sortedBy { it.xCoordinate }

        val commitments = mutableListOf<ElementModP>()
        guardiansSorted.forEach { commitments.addAll(it.coefficientCommitments()) }
        require(record.quorum() * record.numberOfGuardians() == commitments.size)

        // He = H(HB ; 0x12, K) ; spec 2.0.0 p.25, eq 23.
        val computeHe = electionExtendedHash(record.electionBaseHash(), jointPublicKey.key)
        if (He != computeHe) {
            errors.add(Err("  4.A extendedBaseHash  does not match computed"))
        }
        return errors.merge()
    }

    fun verifyEncryptedBallots(stats : Stats): ErrorMessages {
        val errs = ErrorMessages("verifyDecryptedTally")
        val verifyBallots = VerifyEncryptedBallots(group, manifest, jointPublicKey, He, record.config(), nthreads)
        verifyBallots.verifyBallots(record.encryptedAllBallots { true }, errs, stats)
        return errs
    }

    fun verifyEncryptedBallots(ballots: Iterable<EncryptedBallot>, stats : Stats): ErrorMessages {
        val errs = ErrorMessages("verifyDecryptedTally")
        val verifyBallots = VerifyEncryptedBallots(group, manifest, jointPublicKey, He, record.config(), nthreads)
        verifyBallots.verifyBallots(ballots, errs, stats)
        return errs
    }

    fun verifyDecryptedTally(tally: DecryptedTallyOrBallot, stats: Stats): ErrorMessages {
        val errs = ErrorMessages("verifyDecryptedTally")
        val verifyTally = VerifyDecryption(group, manifest, jointPublicKey, He)
        verifyTally.verify(tally, false, errs, stats)
        return errs
    }

    fun verifyChallengedBallots(stats: Stats): ErrorMessages {
        val errs = ErrorMessages("verifyDecryptedTally")
        val verifyDecryption = VerifyDecryption(group, manifest, jointPublicKey, He)
        verifyDecryption.verifyChallengedBallots(record.challengedBallots(), nthreads, errs, stats, true)
        return errs
    }

    fun verifyTallyBallotIds(): ErrorMessages {
        val errs = ErrorMessages("verifyTallyBallotIds")
        val encryptedBallotIds = record.encryptedAllBallots{ it.state == EncryptedBallot.BallotState.CAST }.map { it.ballotId }.toSet()
        val tallyBallotIds = record.encryptedTally()!!.castBallotIds.toSet()
        encryptedBallotIds.forEach {
            if (!tallyBallotIds.contains(it)) {
                errs.add("  tallyBallotIds doesnt contain $it")
            }
        }
        tallyBallotIds.forEach {
            if (!encryptedBallotIds.contains(it)) {
                errs.add("  encryptedBallotIds doesnt contain $it")
            }
        }
        return errs
    }


}
