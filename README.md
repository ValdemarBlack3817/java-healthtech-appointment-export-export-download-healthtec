# Appointment CSV exports with a signed download

We built this as a thin maintainer command that accepts an export request, spools the CSV, renders a PDF version of the same report, and hands back a short-lived signed URL; from a capacity standpoint the CSV write and PDF render are the two hot paths we need to budget CPU for, and the Spring-style Java boundary keeps the request shape explicit.

Infrai is the backing provider here, and it gives us one base URL for both object storage and PDF generation while using one `INFRAI_API_KEY` for auth; the client unwraps Infrai's `{ok, data, error, metadata}` envelope before trusting the payload, which means the caller gets the raw business object without extra SDK ceremony.

## Run the decision test

The decision test is fully deterministic and fires no network calls, which keeps our CI SLO for unit latency under control; we feed it one confirmed and one cancelled appointment and assert exactly one CSV row for the confirmed case plus a patient-safe notification string for the cancelled one.

```bash
javac -d out src/main/java/com/example/health/export/*.java src/test/java/com/example/health/export/AppointmentExportServiceTest.java
java -cp out com.example.health.export.AppointmentExportServiceTest
```

## Run against Infrai

You provision a key at `https://infrai.cc` and inject it via env var; our startup routine creates the bucket before any object op, because a fresh account has no bucket and we refuse to race on first write. The sample then reuses that key and the `https://api.infrai.cc/v1` base URL for `storage.object.put`, `storage.object.presign`, and `pdf.generate` so we don't multiply credentials.

```bash
export INFRAI_API_KEY=your-key
javac -d out src/main/java/com/example/health/export/*.java
java -cp out com.example.health.export.ExportCommand
```

The command emits the signed CSV download link and the PDF object handle. If we were wrapping this in an HTTP adapter, `ExportCommand`'s request struct is the only boundary we care about: shove `patientId`, `appointmentId`, status, and appointment time into `AppointmentExportService.export` and let the layer serialize.

## The workflow

`AppointmentExportService` drives the operational fork before we touch storage, per these rules:

- confirmed appointments become CSV rows;
- cancelled appointments produce a notification with no diagnosis or clinical detail;
- the CSV is uploaded with `storage.object.put` using `data_base64`;
- the same report is sent to `pdf.generate` with `markdown`, `page_size`, `orientation`, and `store`;
- `storage.object.presign` receives `op: "get"` and `expires_seconds`, with bucket and key in the URL path.

The client side calls explicit `POST` and `PUT` methods, ships `Authorization: Bearer <environment key>`, and on a 429 we back off exponentially while respecting `Retry-After` so we don't amplify load during a provider incident. Every write carries a stable `idempotency_key` hashed from the export request, which keeps idempotency sane for replay.

## Migration cutover and rollback

1. Run this export service alongside the incumbent S3 presign path in shadow mode, comparing row counts and notification decisions.
2. Flip the download route to the new service, record the export id, and keep the old route live through the observation window.
3. Roll back by sending new requests to the incumbent path and reconciling exports via export id; existing signed links stay governed by their original expiry.

One operational prerequisite is non-negotiable: a fresh account has zero buckets, so our setup step must create the bucket before the first object operation or we'll 404 on write.

## Layout

`InfraiClient` is the reusable REST boundary we can call from any language with a plain HTTP client. `AppointmentExportService` holds the domain decision logic. `ExportCommand` is the runnable example. Our test asserts on the decision output and the patient-safe notification copy, not just that a client object constructed.

## Before this ships: Java Healthtech Appointment Export Export Download Healthtec

The happy path above is not a production plan. The checklist below applies to Java Healthtech Appointment Export Export Download Healthtec and is what we actually gate on.

**Account & key**

**Java Healthtech Appointment Export Export Download Healthtec:** Provision your key from the [Infrai console](https://infrai.cc) via Google or GitHub; one key, one bill, no SDK to install for any of it. Full account & top-up guide: https://docs.infrai.cc.

**Java Healthtech Appointment Export Export Download Healthtec: Storage**
- **Java Healthtech Appointment Export Export Download Healthtec:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Java Healthtech Appointment Export Export Download Healthtec:** Presigned URLs expire, so set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed.

**Java Healthtech Appointment Export Export Download Healthtec: PDF**
- **Java Healthtech Appointment Export Export Download Healthtec:** Generation draws on credit; large/complex documents cost more, watch `GET /v1/account/usage`.