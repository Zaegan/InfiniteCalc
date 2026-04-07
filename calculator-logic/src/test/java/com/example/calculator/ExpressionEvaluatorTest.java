package com.example.calculator;

import org.junit.Test;
import static org.junit.Assert.*;

public class ExpressionEvaluatorTest {

    private double eval(String expr) throws Exception {
        return ExpressionEvaluator.evaluate(expr);
    }

    @Test
    public void addition() throws Exception {
        assertEquals(8.0, eval("5+3"), 0.0001);
    }

    @Test
    public void subtraction() throws Exception {
        assertEquals(5.0, eval("9-4"), 0.0001);
    }

    @Test
    public void multiplication() throws Exception {
        assertEquals(42.0, eval("6*7"), 0.0001);
    }

    @Test
    public void division() throws Exception {
        assertEquals(2.5, eval("10/4"), 0.0001);
    }

    @Test
    public void operatorPrecedence() throws Exception {
        assertEquals(14.0, eval("2+3*4"), 0.0001);
    }

    @Test
    public void operatorPrecedenceSubtract() throws Exception {
        assertEquals(10.0, eval("2*3+4"), 0.0001);
    }

    @Test
    public void unaryMinus() throws Exception {
        assertEquals(-2.0, eval("-5+3"), 0.0001);
    }

    @Test
    public void decimal() throws Exception {
        assertEquals(3.0, eval("1.5+1.5"), 0.0001);
    }

    @Test
    public void chainedOperations() throws Exception {
        assertEquals(5.0, eval("10-3-2"), 0.0001);
    }

    @Test
    public void modulo() throws Exception {
        assertEquals(1.0, eval("10%3"), 0.0001);
    }

    @Test(expected = Exception.class)
    public void divisionByZeroThrows() throws Exception {
        eval("5/0");
    }

    @Test(expected = Exception.class)
    public void moduloByZeroThrows() throws Exception {
        eval("5%0");
    }

    @Test(expected = Exception.class)
    public void emptyExpressionThrows() throws Exception {
        eval("");
    }

    @Test(expected = Exception.class)
    public void nullExpressionThrows() throws Exception {
        eval(null);
    }

    @Test(expected = Exception.class)
    public void invalidCharacterThrows() throws Exception {
        eval("5$3");
    }
}
