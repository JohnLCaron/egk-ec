package org.cryptobiotic.eg.core

import org.junit.jupiter.api.Assertions.assertFalse
import kotlin.test.Test
import kotlin.test.assertTrue

class TestSetMembership {
    val groups = listOf(productionGroup(), productionGroup("P-256"))

    @Test
    fun testSetMembershipZero() {
        groups.forEach{
            testSetMembershipZero(it)
            testSetMembershipOne(it)
            testSetMembershipG(it)
            testSetMembershiNotG(it)
        }
    }

    fun testSetMembershipZero(group: GroupContext) {
        val zero = group.binaryToElementModP(ByteArray(11))
        assertFalse(checkMembership(zero))
    }

    fun testSetMembershipOne(group: GroupContext) {
        assertTrue(checkMembership(group.ONE_MOD_P))
    }

    fun testSetMembershipG(group: GroupContext) {
        val anyq = group.randomElementModQ()
        val checkit = group.gPowP(anyq)
        assertTrue(checkMembership(checkit))
    }

    fun testSetMembershiNotG(group: GroupContext) {
        val two = group.binaryToElementModP(ByteArray(1) { 2 })
        val checkit = two?.powP(group.TWO_MOD_Q)
        assertFalse( checkMembership(checkit) )
    }

    fun checkMembership(x: ElementModP?): Boolean {
        return (x != null) && x.isValidElement()
    }

}