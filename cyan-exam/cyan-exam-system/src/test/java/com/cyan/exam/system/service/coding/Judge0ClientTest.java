package com.cyan.exam.system.service.coding;

import com.alibaba.fastjson.JSON;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class Judge0ClientTest {
    @Test void adapterPinsLimitsAndDisablesNetwork() throws Exception {
        var body = new AtomicReference<String>();
        var auth = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/submissions", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            auth.set(exchange.getRequestHeaders().getFirst("X-Auth-Token"));
            byte[] response = "{\"token\":\"local-test-token\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, response.length);
            try (var stream = exchange.getResponseBody()) { stream.write(response); }
        });
        server.start();
        try {
            var client = configured(server);
            assertEquals("local-test-token", client.submit("print(input())", "hello"));
            var request = JSON.parseObject(body.get());
            assertEquals("test-secret", auth.get());
            assertFalse(request.getBooleanValue("enable_network"));
            assertEquals(131072, request.getIntValue("memory_limit"));
            assertEquals(2, request.getIntValue("cpu_time_limit"));
            assertEquals(16, request.getIntValue("max_processes_and_or_threads"));
            assertEquals("hello", Judge0Client.decode(request.getString("stdin")));
            assertFalse(request.containsKey("expected_output"));
            assertFalse(request.containsKey("additional_files"));
            assertFalse(request.containsKey("callback_url"));
        } finally { server.stop(0); }
    }

    @Test void oversizedRunnerResponseIsRejected() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/submissions", exchange -> {
            byte[] response = "x".repeat(262145).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var stream = exchange.getResponseBody()) { stream.write(response); }
        });
        server.start();
        try {
            assertThrows(IllegalStateException.class, () -> configured(server).poll("test-token"));
        } finally { server.stop(0); }
    }

    @Test void decodedOutputIsNeverTruncatedBeforeComparison() {
        String output = "x".repeat(70000);
        assertEquals(output, Judge0Client.decode(java.util.Base64.getEncoder()
                .encodeToString(output.getBytes(StandardCharsets.UTF_8))));
    }

    private Judge0Client configured(HttpServer server) {
        var client = new Judge0Client();
        ReflectionTestUtils.setField(client, "enabled", true);
        ReflectionTestUtils.setField(client, "url", "http://127.0.0.1:" + server.getAddress().getPort());
        ReflectionTestUtils.setField(client, "token", "test-secret");
        ReflectionTestUtils.setField(client, "languageId", 71);
        return client;
    }

    @Test void goJudgeUsesBoundedIsolatedCommandAndActualVerdicts() throws Exception {
        var verdict = new AtomicReference<>("Nonzero Exit Status");
        var request = new AtomicReference<String>();
        var auth = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/run", exchange -> {
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = ("[{\"status\":\"" + verdict.get()
                    + "\",\"files\":{\"stdout\":\"4\\n\",\"stderr\":\"error\"},\"time\":1000000,\"memory\":1024}]")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        try {
            var client = new Judge0Client();
            ReflectionTestUtils.setField(client, "enabled", true);
            ReflectionTestUtils.setField(client, "provider", "go-judge");
            ReflectionTestUtils.setField(client, "url", "http://127.0.0.1:" + server.getAddress().getPort());
            ReflectionTestUtils.setField(client, "token", "test-secret");
            assertEquals(11, client.run("print(4)", "").getJSONObject("status").getIntValue("id"));
            assertEquals("Bearer test-secret", auth.get());
            var command = JSON.parseObject(request.get()).getJSONArray("cmd").getJSONObject(0);
            assertEquals("/usr/local/bin/python3", command.getJSONArray("args").getString(0));
            assertEquals("-I", command.getJSONArray("args").getString(1));
            assertEquals(134217728, command.getIntValue("memoryLimit"));
            assertEquals(16, command.getIntValue("procLimit"));
            assertEquals(5000000000L, command.getLongValue("clockLimit"));
            verdict.set("Accepted");
            assertEquals("4\n", Judge0Client.decode(client.run("print(4)", "").getString("stdout")));
            verdict.set("Internal Error");
            assertEquals(13, client.run("print(4)", "").getJSONObject("status").getIntValue("id"));
        } finally { server.stop(0); }
    }
}
