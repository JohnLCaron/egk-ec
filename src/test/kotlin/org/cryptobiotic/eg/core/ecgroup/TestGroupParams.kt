package org.cryptobiotic.eg.core.ecgroup

import org.cryptobiotic.eg.core.ecgroup.VecGroups
import kotlin.test.Test

class TestGroupParams {

    @Test
    fun showParamNames() {
        println(buildString {
            VecGroups.curveNames.forEach { println(it) }
        })
    }

}