package io.github.satoru2727.miteru.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class JikkyoForceTest {

    @Test
    fun forceTierFollowsKonomiTVThresholds() {
        assertEquals(JikkyoForceTier.NORMAL, jikkyoForceTier(null))
        assertEquals(JikkyoForceTier.NORMAL, jikkyoForceTier(0))
        assertEquals(JikkyoForceTier.NORMAL, jikkyoForceTier(99))
        assertEquals(JikkyoForceTier.MANY, jikkyoForceTier(100))
        assertEquals(JikkyoForceTier.MANY, jikkyoForceTier(199))
        assertEquals(JikkyoForceTier.SO_MANY, jikkyoForceTier(200))
        assertEquals(JikkyoForceTier.SO_MANY, jikkyoForceTier(499))
        assertEquals(JikkyoForceTier.FESTIVAL, jikkyoForceTier(500))
        assertEquals(JikkyoForceTier.FESTIVAL, jikkyoForceTier(9999))
    }
}
