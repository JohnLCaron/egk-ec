package org.cryptobiotic.eg.decrypt

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.decrypt.CipherDecryptor.Companion
import org.cryptobiotic.eg.election.Guardian
import org.cryptobiotic.eg.keyceremony.calculateGexpPiAtL

/** All the guardians, not just the decrypting ones. */
data class Guardians(val group : GroupContext, val guardians: List<Guardian>) {
    val guardianMap = guardians.associateBy { it.guardianId }
    val guardianGexpP = mutableMapOf<String, ElementModP>()

    // eager evaluation so operation counts are simpler
    init {
        guardians.forEach {
            guardianGexpP[it.guardianId] = getGexpP(it.guardianId)
        }
    }

    fun guardianPublicKey(id:String): ElementModP? {
        return guardianMap[id]?.publicKey()
    }

    /**
     * g^P(ℓ) mod p = Prod_i( g^Pi(ℓ) ), i = 1..n
     *
     * g raised to P(xcoord), where P = P1(xcoord) + P2(xcoord) + .. + Pn(xcoord)
     * and xcoord is the xcoordinate of guardianId, aka ℓ in eq 21.
     * This is the inner factor in eqs 74, 83.
     */
    fun getGexpP(guardianId: String) : ElementModP {
        return guardianGexpP.getOrPut(guardianId) {
            val guardian = guardianMap[guardianId]
                ?: throw IllegalStateException("Guardians.getGexpP doesnt have guardian id = '$guardianId'")

            group.multP(
                guardians.map { calculateGexpPiAtL(it.guardianId, guardian.xCoordinate, it.coefficientCommitments()) }
            )
        }
    }

    fun buildLagrangeCoordinates(decryptingTrustees: List<DecryptingTrusteeIF>) : Map<String, LagrangeCoordinate> {
        // check that the DecryptingTrustee's match their public key
        val badTrustees = mutableListOf<String>()
        for (trustee in decryptingTrustees) {
            val guardian = guardianMap[trustee.id()]
            if (guardian == null) {
                badTrustees.add(trustee.id())
            } else {
                if (trustee.guardianPublicKey().key != guardian.publicKey()) {
                    badTrustees.add(trustee.id())
                    logger.error { "trustee public key = ${trustee.guardianPublicKey()} not equal guardian = ${guardian.publicKey()}" }
                }
            }
        }
        if (badTrustees.isNotEmpty()) {
            throw RuntimeException("DecryptingTrustee(s) ${badTrustees.joinToString(",")} do not match the public record")
        }

        // build lagrange coeff for each trustee
        val lagrange = decryptingTrustees.map { trustee ->
            val present: List<Int> = // available trustees minus me
                decryptingTrustees.filter { it.id() != trustee.id() }.map { it.xCoordinate() }
            val coeff: ElementModQ = computeLagrangeCoefficient(group, trustee.xCoordinate(), present)
            LagrangeCoordinate(trustee.id(), trustee.xCoordinate(), coeff)
        }

        return lagrange.associateBy { it.guardianId }
    }

    companion object {
        private val logger = KotlinLogging.logger("Guardians")
    }

}

//////////////////////////////////////////////////////////////////////////////

data class LagrangeCoordinate(
    var guardianId: String,
    var xCoordinate: Int,
    var lagrangeCoefficient: ElementModQ, // wℓ, spec 2.0.0 eq 67
) {
    init {
        require(guardianId.isNotEmpty())
        require(xCoordinate > 0)
    }
}

/** Compute the lagrange coefficient, now that we know which guardians are present; 2.0, section 3.6.2, eq 67. */
fun computeLagrangeCoefficient(group: GroupContext, coordinate: Int, present: List<Int>): ElementModQ {
    val others: List<Int> = present.filter { it != coordinate }
    if (others.isEmpty()) {
        return group.ONE_MOD_Q
    }
    val numerator: Int = others.reduce { a, b -> a * b }

    val diff: List<Int> = others.map { degree -> degree - coordinate }
    val denominator = diff.reduce { a, b -> a * b }

    val denomQ =
        if (denominator > 0) denominator.toElementModQ(group) else (-denominator).toElementModQ(group)
            .unaryMinus()

    return numerator.toElementModQ(group) / denomQ
}