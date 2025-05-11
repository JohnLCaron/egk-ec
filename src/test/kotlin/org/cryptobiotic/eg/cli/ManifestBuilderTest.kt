package org.cryptobiotic.eg.cli

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.cryptobiotic.eg.input.ManifestInputValidation
import kotlin.test.*

// test manifests with multiple styles
class ManifestBuilderTest {

    @Test
    fun buildManifestWithStyles() {
        runTest {
            checkAll(
                // propTestFastConfig,
                Arb.int(min = 1, max = 10),
                Arb.int(min = 1, max = 27),
                Arb.int(min = 2, max = 5),
            ) { nstyle, ncontest, nselection ->
                println("buildTestManifest($nstyle, $ncontest, $nselection)")
                val manifest = buildTestManifest(nstyle, ncontest, nselection)
                println(RunShowElectionRecord.show(manifest, false))

                val validator = ManifestInputValidation(manifest)
                val errs = validator.validate()
                if (errs.hasErrors()) {
                    println(errs)
                    fail()
                } else {
                    println("ManifestInputValidation succeeded")
                }
            }
        }
    }
}