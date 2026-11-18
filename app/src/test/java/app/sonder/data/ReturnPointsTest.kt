package app.sonder.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ReturnPointsTest {
    @Test fun firstSkipRemembersWhereListeningWas() {
        assertEquals(ReturnPoint(1,9_000_000,7_200_000),ReturnPoints.chain(null,1,9_000_000,7_200_000))
    }
    @Test fun skippingAgainRightAwayKeepsTheOriginalPlace() {
        // An accidental previous, then previous again trying to fix it.
        val first=ReturnPoints.chain(null,1,9_000_000,7_200_000)
        assertEquals(ReturnPoint(1,9_000_000,3_600_000),ReturnPoints.chain(first,1,7_201_000,3_600_000))
        // Next to get back still leaves the original place to return to.
        assertEquals(ReturnPoint(1,9_000_000,7_200_000),ReturnPoints.chain(ReturnPoint(1,9_000_000,3_600_000),1,3_605_000,7_200_000))
    }
    @Test fun skipAfterListeningOnStartsAFreshPoint() {
        val old=ReturnPoint(1,9_000_000,7_200_000)
        assertEquals(ReturnPoint(1,8_000_000,7_200_000),ReturnPoints.chain(old,1,8_000_000,7_200_000))
    }
    @Test fun anotherBookReplacesThePoint() {
        assertEquals(ReturnPoint(2,50_000,0),ReturnPoints.chain(ReturnPoint(1,9_000_000,7_200_000),2,50_000,0))
    }
}
