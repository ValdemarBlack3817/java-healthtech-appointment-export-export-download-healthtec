package com.example.health.export;

import java.time.Instant;
import java.util.List;

public final class ExportCommand {
    public static void main(String[] args) throws Exception {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalStateException("INFRAI_API_KEY is required");
        var appointments = List.of(
                new AppointmentExportService.Appointment("p-104", "a-9001", "confirmed", Instant.parse("2026-10-23T09:00:00Z")),
                new AppointmentExportService.Appointment("p-208", "a-9002", "cancelled", Instant.parse("2026-10-23T10:00:00Z")));
        String link = new AppointmentExportService().export("health-exports", "appointments/a-9001.csv", appointments,
                new InfraiClient(key), "export-20261023-001");
        System.out.println(link);
    }
}
