package org.cryptobiotic.eg.keyceremony

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.eg.publish.makeConsumer
import org.cryptobiotic.eg.cli.RunTrustedTallyDecryption.Companion.readDecryptingTrustees
import org.cryptobiotic.eg.decrypt.Guardians
import org.junit.jupiter.api.Assertions.assertEquals

import kotlin.test.Test

class RunKeyCeremonyTrusteeTest {
    val baseDir = "src/test/data/keyceremony"

    @Test
    fun testKeyCeremony() {
        listOf("$baseDir/runFakeKeyCeremonyAll",
            "$baseDir/runFakeKeyCeremonySome",
            "$baseDir/runFakeKeyCeremonyAllEc",
            "$baseDir/runFakeKeyCeremonySomeEc",
        ).forEach { testKeyCeremony(it) }
    }

    fun testKeyCeremony(topDir: String) {
        println("testKeyCeremony in $topDir")
        val privateDir = "$topDir/private_data"
        val trusteeDir = "${privateDir}/trustees"
        val consumer = makeConsumer(topDir)
        val init = consumer.readElectionInitialized().unwrap()
        val guardians = Guardians( consumer.group, init.guardians)
        // guardians.guardians.forEach { println( "  $it")}

        val trustees =  readDecryptingTrustees(topDir, trusteeDir)
        trustees.forEach { trustee ->
            val tpkey = trustee.guardianPublicKey()
            val gkey = guardians.guardianPublicKey(trustee.id())
            println( "  trustee ${trustee.id()}" )
            println( "    trustee key= $tpkey" )
            println( "   guardian key= $gkey" )
            assertEquals(tpkey, gkey)
        }
        println()
    }

}

