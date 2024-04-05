package org.cryptobiotic.eg.keyceremony

import org.cryptobiotic.eg.core.*
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrapError

data class PublicKeys(
    val guardianId: String,
    val guardianXCoordinate: Int,
    val coefficientProofs: List<SchnorrProof>, // contain the coefficient public commitment and public key
) {
    init {
        require(guardianId.isNotEmpty())
        require(guardianXCoordinate > 0)
        require(coefficientProofs.isNotEmpty())
    }

    val publicKey: ElementModP  = coefficientProofs[0].publicCommitment

    fun coefficientCommitments(): List<ElementModP> {
        return coefficientProofs.map { it.publicCommitment }
    }

    fun validate(): Result<Boolean, String> {
        val checkProofs: MutableList<Result<Boolean, String>> = mutableListOf()
        for ((idx, proof) in this.coefficientProofs.withIndex()) {
            val result = proof.validate(guardianXCoordinate, idx)
            if (result is Err) {
                checkProofs.add(
                    Err("  Guardian $guardianId has invalid proof for coefficient $idx " + result.unwrapError()))
            }
        }
        return checkProofs.merge()
    }
}