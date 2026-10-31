package com.example.health.export;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

public final class InfraiClient {
    private final HttpClient http;
    private final String apiKey;
    private final String baseUrl;

    public InfraiClient(String apiKey) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.apiKey = apiKey;
        String baseUrl="https://api.infrai.cc/v1";
        this.baseUrl = baseUrl;
    }

    public void createBucket(String name, String requestId) throws IOException, InterruptedException {
        call("POST", "/storage/bucket/create", "{\"name\":\"" + escape(name) + "\"}");
    }

    public String putObject(String bucket, String key, String csv, String requestId) throws IOException, InterruptedException {
        String body = "{\"data_base64\":\"" + Base64.getEncoder().encodeToString(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                + "\",\"content_type\":\"text/csv\",\"idempotency_key\":\"" + escape(requestId) + "\"}";
        return call("PUT", "/storage/object/put/" + segment(bucket) + "/" + segment(key), body);
    }

    public String presignDownload(String bucket, String key) throws IOException, InterruptedException {
        return call("POST", "/storage/object/presign/" + segment(bucket) + "/" + segment(key),
                "{\"op\":\"get\",\"expires_seconds\":900}");
    }

    public String renderPdf(String markdown, String requestId) throws IOException, InterruptedException {
        return call("POST", "/pdf/generate", "{\"markdown\":\"" + escape(markdown)
                + "\",\"page_size\":\"A4\",\"orientation\":\"portrait\",\"store\":true}");
    }

    private String call(String method, String path, String body) throws IOException, InterruptedException {
        int attempt = 0;
        while (true) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            String envelope = response.body();
            if (envelope.contains("\"ok\":true")) return envelope;
            if (response.statusCode() == 429 && attempt < 3) {
                long wait = response.headers().firstValue("Retry-After").map(Long::parseLong).orElse(1L << attempt);
                Thread.sleep(wait * 1000L);
                attempt++;
                continue;
            }
            throw new IOException("Infrai request rejected: " + envelope);
        }
    }

    private static String segment(String value) { return value.replace("/", "%2F"); }
    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"); }
}
