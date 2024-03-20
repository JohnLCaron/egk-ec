package org.cryptobiotic.eg.election

import com.github.michaelbull.result.*
import org.cryptobiotic.eg.core.*
import org.cryptobiotic.eg.decrypt.DecryptingTrustee
import org.cryptobiotic.eg.keyceremony.KeyCeremonyTrustee
import org.cryptobiotic.eg.keyceremony.PublicKeys

fun makeDoerreTrustee(ktrustee: KeyCeremonyTrustee, electionId : UInt256): DecryptingTrustee {
    return DecryptingTrustee(
        ktrustee.id,
        ktrustee.xCoordinate,
        ElGamalPublicKey(ktrustee.guardianPublicKey()),
        ktrustee.computeSecretKeyShare(),
    )
}

fun makeGuardian(trustee: KeyCeremonyTrustee): Guardian {
    val publicKeys = trustee.publicKeys().unwrap()
    return Guardian(
        trustee.id,
        trustee.xCoordinate,
        publicKeys.coefficientProofs,
    )
}

fun makeGuardian(publicKeys: PublicKeys): Guardian {
    return Guardian(
        publicKeys.guardianId,
        publicKeys.guardianXCoordinate,
        publicKeys.coefficientProofs,
    )
}