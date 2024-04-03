package org.cryptobiotic.eg.core.ecgroup

import kotlin.test.Test

class TestVecGroups {

    @Test
    fun showParamNames() {
        println(buildString {
            VecGroups.curveNames.forEach { println(it) }
        })
    }

    @Test
    fun testPis3mod4() {
        VecGroups.curveNames.forEach { name ->
            val vg = VecGroups.getEcGroup(name, false)
            println("$name: pIs3mod4=${vg.pIs3mod4}")
        }
    }
    //P-192: pIs3mod4=true
    //P-224: pIs3mod4=false
    //P-256: pIs3mod4=true
    //P-384: pIs3mod4=true
    //P-521: pIs3mod4=true
    //brainpoolp192r1: pIs3mod4=true
    //brainpoolp224r1: pIs3mod4=true
    //brainpoolp256r1: pIs3mod4=true
    //brainpoolp320r1: pIs3mod4=true
    //brainpoolp384r1: pIs3mod4=true
    //brainpoolp512r1: pIs3mod4=true
    //prime192v1: pIs3mod4=true
    //prime192v2: pIs3mod4=true
    //prime192v3: pIs3mod4=true
    //prime239v1: pIs3mod4=true
    //prime239v2: pIs3mod4=true
    //prime239v3: pIs3mod4=true
    //prime256v1: pIs3mod4=true
    //secp192k1: pIs3mod4=true
    //secp192r1: pIs3mod4=true
    //secp224k1: pIs3mod4=false
    //secp224r1: pIs3mod4=false
    //secp256k1: pIs3mod4=true
    //secp256r1: pIs3mod4=true
    //secp384r1: pIs3mod4=true
    //secp521r1: pIs3mod4=true

}