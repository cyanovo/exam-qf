package com.cyan.exam.system.service.coding;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cyan.exam.common.exception.ServiceException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Judge0Client {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    @Value("${exam.judge.enabled:false}") private boolean enabled;
    @Value("${exam.judge.url:}") private String url;
    @Value("${exam.judge.token:}") private String token;
    @Value("${exam.judge.python-language-id:71}") private int languageId;

    public boolean enabled() { return enabled; }
    public void requireConfigured() {
        if (!enabled || url.isBlank() || token.isBlank()) throw new ServiceException("独立评测服务未启用，请联系管理员");
        URI endpoint = URI.create(url);
        if (!Set.of("http", "https").contains(endpoint.getScheme()) || endpoint.getHost() == null
                || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null) {
            throw new ServiceException("评测服务地址配置无效");
        }
    }

    public String submit(String source, String input) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source_code", encode(source)); payload.put("stdin", encode(input));
        payload.put("language_id", languageId);
        payload.put("cpu_time_limit", 2); payload.put("cpu_extra_time", 0.5); payload.put("wall_time_limit", 5);
        payload.put("memory_limit", 131072); payload.put("stack_limit", 65536);
        payload.put("max_processes_and_or_threads", 16); payload.put("max_file_size", 64);
        payload.put("enable_network", false); payload.put("number_of_runs", 1);
        var response = request("/submissions?base64_encoded=true&wait=false", JSON.toJSONString(payload));
        String value = response.getString("token");
        if (value == null || !value.matches("[a-zA-Z0-9-]{1,100}")) throw new IllegalStateException("Invalid runner token");
        return value;
    }

    public JSONObject poll(String id) throws Exception {
        if (!id.matches("[a-zA-Z0-9-]{1,100}")) throw new IllegalArgumentException();
        return request("/submissions/" + id + "?base64_encoded=true&fields=status,stdout,stderr,compile_output,time,memory", null);
    }

    private JSONObject request(String path, String body) throws Exception {
        requireConfigured();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url.replaceAll("/+$", "") + path))
                .timeout(Duration.ofSeconds(8)).header("X-Auth-Token", token).header("Content-Type", "application/json");
        if (body != null) builder.POST(HttpRequest.BodyPublishers.ofString(body));
        else builder.GET();
        // Read a bounded stream: a malfunctioning runner must not exhaust the business server.
        var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        ScheduledExecutorService deadline = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "judge-response-deadline");
            thread.setDaemon(true);
            return thread;
        });
        var timeout = deadline.schedule(() -> {
            try { response.body().close(); } catch (java.io.IOException ignored) { }
        }, 8, TimeUnit.SECONDS);
        try (var input = response.body()) {
            byte[] bytes = input.readNBytes(262145);
            if (response.statusCode() < 200 || response.statusCode() >= 300 || bytes.length > 262144) {
                throw new IllegalStateException("Runner request failed");
            }
            return JSON.parseObject(new String(bytes, StandardCharsets.UTF_8));
        } finally {
            timeout.cancel(false);
            deadline.shutdownNow();
        }
    }
    private static String encode(String value) { return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    public static String decode(String value) {
        if (value == null) return "";
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
