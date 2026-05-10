package com.carbrowser.video;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for VideoPlayerService speed options.
 */
public class VideoPlayerServiceTest {

    @Test
    public void testSpeedOptionsLength() {
        float[] speeds = VideoPlayerService.getSpeedOptions();
        assertEquals(8, speeds.length);
    }

    @Test
    public void testSpeedOptionsRange() {
        float[] speeds = VideoPlayerService.getSpeedOptions();
        assertEquals(0.5f, speeds[0], 0.01f);
        assertEquals(5.0f, speeds[speeds.length - 1], 0.01f);
    }

    @Test
    public void testSpeedOptionsOrder() {
        float[] speeds = VideoPlayerService.getSpeedOptions();
        for (int i = 1; i < speeds.length; i++) {
            assertTrue("Speeds should be in ascending order",
                speeds[i] > speeds[i - 1]);
        }
    }

    @Test
    public void testDefaultSpeedIs1x() {
        float[] speeds = VideoPlayerService.getSpeedOptions();
        assertEquals(1.0f, speeds[2], 0.01f); // Index 2 = 1.0x
    }
}
