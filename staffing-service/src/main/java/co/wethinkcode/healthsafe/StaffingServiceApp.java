package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Computes doctor on-call rosters by validating the ward with ward-service and
 * reading the current Emergency Status from alert-level-service.
 *
 * Deliberately fails soft:
 *   - ward-service 404           → propagate 404
 *   - ward-service 5xx/timeout   → 503 "ward-service unavailable"
 *   - alert-level-service 5xx    → 503 "alert-level-service unavailable"
 *   - alert-level invalid body   → 502 "invalid upstream response"
 */
public class StaffingServiceApp {

    static final String WARD_URL        = "http://localhost:7031/wards/";
    static final String ALERT_LEVEL_URL = "http://localhost:7032/alert-level";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7033);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/schedule/{wardId}", ctx -> {
            String wardId = ctx.pathParam("wardId").trim().toUpperCase();
            ctx.json(computeSchedule(wardId));
        });

        app.exception(HttpFailure.class, (e, ctx) -> ctx.status(e.status).json(Map.of("error", e.getMessage())));

        app.exception(Exception.class, (e, ctx) ->
                ctx.status(500).json(Map.of("error", e.getMessage())));
    }

    /** Pure-ish: throws HttpFailure for non-200 upstream. */
    static Map<String, Object> computeSchedule(String wardId) {
        // 1) ward lookup
        UpstreamResponse ward = call(WARD_URL + wardId);
        if (ward.status == 404) {
            throw new HttpFailure(404, "unknown ward: " + wardId);
        }
        if (ward.status != 200) {
            throw new HttpFailure(503, "ward-service returned " + ward.status);
        }

        // 2) alert level
        UpstreamResponse al = call(ALERT_LEVEL_URL);
        if (al.status != 200) {
            throw new HttpFailure(503, "alert-level-service returned " + al.status);
        }
        int level;
        try {
            level = JSON.readTree(al.body).path("level").asInt(-1);
        } catch (Exception e) {
            throw new HttpFailure(502, "invalid alert-level response: " + e.getMessage());
        }
        if (level < 0 || level > 8) {
            throw new HttpFailure(502, "alert-level out of range: " + level);
        }

        // 3) scheduling heuristic
        int doctors = 2 + level;                   // 2 @ L0 → 10 @ L8
        String dept = "unknown";
        try {
            JsonNode wardJson = JSON.readTree(ward.body);
            dept = wardJson.path("department").asText("unknown");
            switch (dept) {
                case "ICU":        doctors += 2; break;
                case "Cardiology": doctors += 1; break;
                case "Maternity":  doctors += 1; break;
                default: break;
            }
        } catch (Exception ignored) {
        }

        Map<String, Object> schedule = new LinkedHashMap<>();
        schedule.put("wardId", wardId);
        schedule.put("department", dept);
        schedule.put("alertLevel", level);
        schedule.put("doctorsOnCall", doctors);
        schedule.put("scheduleFor", "next-24h");
        schedule.put("generatedAt", Instant.now().toString());
        return schedule;
    }

    private static UpstreamResponse call(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            return new UpstreamResponse(resp.statusCode(), resp.body());
        } catch (Exception e) {
            return new UpstreamResponse(503, e.getMessage());
        }
    }

    /** Thrown by computeSchedule() to map to a specific HTTP status. */
    static class HttpFailure extends RuntimeException {
        final int status;
        HttpFailure(int status, String message) { super(message); this.status = status; }
    }

    static class UpstreamResponse {
        final int status;
        final String body;
        UpstreamResponse(int status, String body) { this.status = status; this.body = body; }
    }
}