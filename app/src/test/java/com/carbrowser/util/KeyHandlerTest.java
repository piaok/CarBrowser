package com.carbrowser.util;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for KeyHandler result constants and logic.
 */
public class KeyHandlerTest {

    @Test
    public void testResultConstants() {
        assertEquals(0, KeyHandler.RESULT_NOT_HANDLED);
        assertEquals(1, KeyHandler.RESULT_GO_BACK);
        assertEquals(2, KeyHandler.RESULT_OPEN_MENU);
    }
}
