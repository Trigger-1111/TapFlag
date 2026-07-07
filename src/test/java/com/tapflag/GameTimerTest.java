package com.tapflag;

import com.tapflag.timer.GameTimer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameTimerTest {

    private static final int TOTAL = 10800;    // 3시간
    private static final int CAPTURE = 3600;   // 마지막 1시간

    private static boolean isCapture(int elapsed) {
        return elapsed >= (TOTAL - CAPTURE);
    }

    @Test void notCaptureBefore() {
        assertFalse(isCapture(7199));
    }

    @Test void captureAtBoundary() {
        assertTrue(isCapture(7200));
    }

    @Test void captureDuringPhase() {
        assertTrue(isCapture(9000));
    }

    @Test void formatSeconds() {
        assertEquals("5초", GameTimer.formatTime(5));
    }

    @Test void formatMinutes() {
        assertEquals("1분 30초", GameTimer.formatTime(90));
    }

    @Test void formatHours() {
        assertEquals("1시간 0분 0초", GameTimer.formatTime(3600));
    }

    @Test void formatHoursMinutes() {
        assertEquals("2시간 30분 0초", GameTimer.formatTime(9000));
    }
}
