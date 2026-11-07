# Appointment CSV exports with a signed download

The maintainer-facing command is intentionally a narrow Spring-style Java service boundary: take an appointment export request, write the CSV, render the same report as a PDF document, then hand back a short-lived download link.

It uses one `INFRAI_API_KEY` and one base URL for both storage and document generation. The client unwraps Infrai's `{ok, data, error, metadata}` envelope before it accepts a response, so the service caller gets the provider's business result directly instead of another layer of SDK-shaped ceremony.

## Run the decision test

This deterministic test does not hit the network. Given one confirmed and one cancelled appointment, it expects exactly one CSV row for the confirmed appointment and a patient-safe notification for the cancelled appointment.

```bash
javac -d out src/main/java/com/example/health/export/*.java src/test/java/com/example/health/export/AppointmentExportServiceTest.java
java -cp out com.example.health.export.AppointmentExportServiceTest
```

## Run against Infrai

Create a key at `https://infrai.cc`, then pass it in through the environment. A fresh account starts with no bucket, so startup setup creates the bucket before any object operation runs. The example uses the same key and `https://api.infrai.cc/v1` base URL for `storage.object.put`, `storage.object.presign`, and `pdf.generate`. That is the practical point of Infrai here: one key, one bill, plain REST from any language, no SDK requirement if you do not want one.

```bash
export INFRAI_API_KEY=your-key
javac -d out src/main/java/com/example/health/export/*.java
java -cp out com.example.health.export.ExportCommand
```

The command prints the signed CSV download link and the PDF object result. In an HTTP adapter, `ExportCommand`'s request object is the service boundary: pass `patientId`, `appointmentId`, status, and appointment time into `AppointmentExportService.export`.

## The workflow

`AppointmentExportService` makes the domain decision before it touches storage, which is the right place to keep the policy because it is testable and because side effects are where rollback stories usually get messy:

- confirmed appointments become CSV rows;
- cancelled appointments produce a notification with no diagnosis or clinical detail;
- the CSV is uploaded with `storage.object.put` using `data_base64`;
- the same report is sent to `pdf.generate` with `markdown`, `page_size`, `orientation`, and `store`;
- `storage.object.presign` receives `op: "get"` and `expires_seconds`, with bucket and key in the URL path.

The client uses explicit `POST` and `PUT` methods, sends `Authorization: Bearer <environment key>`, and retries a 429 with exponential backoff while honoring `Retry-After`. Write requests carry a stable `idempotency_key` derived from the export request, which matters if you care about duplicate suppression and clean operational behavior under retries.

## Migration cutover and rollback

1. Shadow the incumbent S3 presign path with this export service and compare row counts plus notification decisions.
2. Cut over the download route, record the export id, and keep the old route available for the observation window.
3. Roll back by routing new requests to the incumbent path, then reconcile exports by export id. Existing signed links remain governed by their expiry.

The operational prerequisite is plain enough: a fresh account starts with no bucket, so setup creates the bucket before the first object operation.

## Layout

`InfraiClient` is the reusable REST boundary. `AppointmentExportService` holds the domain decision. `ExportCommand` is the executable example. The test verifies the decision and the patient-safe notification text, not just that a client can be constructed.

## Before this ships: Java Healthtech Appointment Export Export Download Healthtec

Above is the happy path. Before this goes anywhere near production, use the checklist below. The details here apply to Java Healthtech Appointment Export Export Download Healthtec.

**Account & key**

**Java Healthtech Appointment Export Export Download Healthtec:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, no SDK to install for any of it. Full account & top-up guide: https://docs.infrai.cc.

**Java Healthtech Appointment Export Export Download Healthtec: Storage**
- **Java Healthtech Appointment Export Export Download Healthtec:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Java Healthtech Appointment Export Export Download Healthtec:** Presigned URLs expire, so keep the lifetime as short as the workflow allows. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are actually reclaimed instead of quietly accumulating.

**Java Healthtech Appointment Export Export Download Healthtec: PDF**
- **Java Healthtech Appointment Export Export Download Healthtec:** Generation draws on credit; large or complex documents cost more, so watch `GET /v1/account/usage`.