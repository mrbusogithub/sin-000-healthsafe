package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WardServiceApp {

    static final String INGESTION_URL = "http://localhost:7030/wards";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, Ward> CACHE = new LinkedHashMap<>();

    public static void main(String[] args) {
        loadFromIngestionWithRetry(5, 2_000);

        Javalin app = Javalin.create().start(7031);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/wards", ctx -> ctx.json(new ArrayList<>(CACHE.values())));

        app.get("/wards/{id}", ctx -> {
            String id = ctx.pathParam("id").trim().toUpperCase();
            Ward w = CACHE.get(id);
            if (w == null) {
                ctx.status(404).json(Map.of("error", "unknown ward: " + id));
            } else {
                ctx.json(w);
            }
        });

        app.get("/admin/refresh", ctx -> {
            boolean ok = loadFromIngestion();
            ctx.status(ok ? 200 : 503)
                    .json(Map.of("refreshed", ok, "size", CACHE.size()));
        });
    }

    private static void loadFromIngestionWithRetry(int attempts, long sleepMs) {
        for (int i = 1; i <= attempts; i++) {
            if (loadFromIngestion()) return;
            System.err.printf("[ward-service] ingestion load attempt %d/%d failed; retrying in %dms%n",
                    i, attempts, sleepMs);
            try { Thread.sleep(sleepMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
        System.err.println("[ward-service] giving up on ingestion; starting with empty cache");
    }

    static boolean loadFromIngestion() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(INGESTION_URL))
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                System.err.println("[ward-service] ingestion returned " + resp.statusCode());
                return false;
            }
            List<Ward> wards = JSON.readValue(resp.body(), new TypeReference<List<Ward>>() {});
            synchronized (CACHE) {
                CACHE.clear();
                for (Ward w : wards) CACHE.put(w.getWardId(), w);
            }
            System.out.printf("[ward-service] cached %d wards from ingestion%n", CACHE.size());
            return true;
        } catch (Exception e) {
            System.err.println("[ward-service] ingestion call failed: " + e.getMessage());
            return false;
        }
    }
}