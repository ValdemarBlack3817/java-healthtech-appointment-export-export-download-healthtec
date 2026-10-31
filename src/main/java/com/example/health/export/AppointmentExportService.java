package com.example.health.export;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class AppointmentExportService {
    public record Appointment(String patientId, String appointmentId, String status, Instant startsAt) {}
    public record ExportResult(String csv, List<String> notifications) {}

    public ExportResult buildExport(List<Appointment> appointments) {
        StringBuilder csv = new StringBuilder("appointment_id,patient_id,starts_at,status\n");
        List<String> notifications = new ArrayList<>();
        for (Appointment appointment : appointments) {
            if ("confirmed".equals(appointment.status())) {
                csv.append(appointment.appointmentId()).append(',').append(appointment.patientId()).append(',')
                        .append(appointment.startsAt()).append(",confirmed\n");
            } else if ("cancelled".equals(appointment.status())) {
                notifications.add("Appointment " + appointment.appointmentId() + " was cancelled. Contact the clinic for help.");
            }
        }
        return new ExportResult(csv.toString(), List.copyOf(notifications));
    }

    public String export(String bucket, String key, List<Appointment> appointments, InfraiClient client, String exportId)
            throws IOException, InterruptedException {
        ExportResult result = buildExport(appointments);
        client.createBucket(bucket, exportId + "-bucket");
        client.putObject(bucket, key, result.csv(), exportId);
        client.renderPdf("Appointment export\n\n" + result.csv(), exportId + "-pdf");
        return client.presignDownload(bucket, key);
    }
}
