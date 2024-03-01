package org.cryptobiotic.eg.core.ecgroup

import kotlin.test.Test

class TestGroupParams {

    @Test
    fun showParamNames() {
        println(buildString {
            VecGroups.curveNames.forEach { println(it) }
        })
    }

}