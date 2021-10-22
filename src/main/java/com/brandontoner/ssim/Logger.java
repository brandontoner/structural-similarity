package com.brandontoner.ssim;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

enum Logger {
    ;

    static void log(String message) {
        System.err.format("[%s] %s%n", Instant.now().truncatedTo(ChronoUnit.MILLIS), message);
    }
}
