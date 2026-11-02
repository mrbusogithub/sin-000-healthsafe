package co.wethinkcode.healthsafe.api;

import co.wethinkcode.healthsafe.application.AlertLevelService;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

class AlertLevelRoutesTest {

    private static final HttpClient CLIENT =
            HttpClient.newHttpClient();

    private Javalin app;
    private int port;

    @BeforeEach
    void setUp() throws IOException {

        port = findAvailablePort();

        app = Javalin.create();

        AlertLevelService service = new AlertLevelService();

        new AlertLevelRoutes(service).register(app);

        app.start(port);
    }

    @AfterEach
    void tearDown() {

        if (app != null) {
            app.stop();
        }
    }

    @Test
    void getReturnsInitialLevel() throws Exception {

        HttpResponse<String> response = send(
                "GET",
                "/alert-level",
                null
        );

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"level\":0"));
    }

    @Test
    void postAcceptsValidLevel() throws Exception {

        HttpResponse<String> response = send(
                "POST",
                "/alert-level",
                "{\"level\":5}"
        );

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"level\":5"));
    }

    @Test
    void putAcceptsValidLevel() throws Exception {

        HttpResponse<String> response = send(
                "PUT",
                "/alert-level",
                "{\"level\":8}"
        );

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"level\":8"));
    }

    @Test
    void rejectsMissingLevel() throws Exception {

        HttpResponse<String> response = send(
                "POST",
                "/alert-level",
                "{}"
        );

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("missing"));
    }

    @Test
    void rejectsMalformedJson() throws Exception {

        HttpResponse<String> response = send(
                "POST",
                "/alert-level",
                "{not-valid-json"
        );

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("invalid JSON"));
    }

    @Test
    void rejectsEmptyRequestBody() throws Exception {

        HttpResponse<String> response = send(
                "POST",
                "/alert-level",
                ""
        );

        assertEquals(400, response.statusCode());
    }

    @Test
    void rejectsOutOfRangeLevel() throws Exception {

        HttpResponse<String> response = send(
                "POST",
                "/alert-level",
                "{\"level\":9}"
        );

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("between 0 and 8"));
    }

    private HttpResponse<String> send(
            String method,
            String path,
            String body
    ) throws Exception {

        String requestBody = body == null ? "" : body;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://localhost:" + port + path
                ))
                .header("Content-Type", "application/json")
                .method(
                        method,
                        HttpRequest.BodyPublishers.ofString(requestBody)
                )
                .build();

        return CLIENT.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    /**
     * Finds a currently unused local port for the HTTP test server.
     */
    private int findAvailablePort() throws IOException {

        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}