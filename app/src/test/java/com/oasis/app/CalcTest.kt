package com.oasis.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalcTest {
    @Test fun basics() {
        assertEquals(7.0, Calc.eval("1+2*3")!!, 1e-9)
        assertEquals(9.0, Calc.eval("(1+2)*3")!!, 1e-9)
        assertEquals(8.0, Calc.eval("2^3")!!, 1e-9)
        assertEquals(2.5, Calc.eval("5/2")!!, 1e-9)
        assertEquals(6.0, Calc.eval("2x3")!!, 1e-9)
    }
    @Test fun notMath() {
        assertNull(Calc.eval("netflix"))
        assertNull(Calc.eval("123"))
        assertNull(Calc.eval("1+"))
        assertNull(Calc.eval("1/0"))
    }
}
