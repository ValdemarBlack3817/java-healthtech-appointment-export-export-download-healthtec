package com.example.health.export;

import java.time.Instant;
import java.util.List;

public final class AppointmentExportServiceTest {
    public static void main(String[] args) {
        var input = List.of(
                new AppointmentExportService.Appointment("p-1", "a-1", "confirmed", Instant.parse("2026-10-23T09:00:00Z")),
                new AppointmentExportService.Appointment("p-2", "a-2", "cancelled", Instant.parse("2026-10-23T10:00:00Z")));
        var result = new AppointmentExportService().buildExport(input);
        check(result.csv().contains("a-1,p-1,2026-10-23T09:00:00Z,confirmed"), "confirmed appointment must be exported");
        check(!result.csv().contains("a-2"), "cancelled appointment must not enter the export");
        check(result.notifications().equals(List.of("Appointment a-2 was cancelled. Contact the clinic for help.")), "notification must be patient-safe");
        System.out.println("appointment export decision: PASS");
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
