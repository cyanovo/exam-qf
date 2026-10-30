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
    @Value("${exam.judge.provider:judge0}") private String provider = "judge0";
    @Value("${exam.judge.runtime-id:unspecified}") private String runtimeId = "unspecified";

    public boolean enabled() { return enabled; }
    public boolean synchronous() { return "go-judge".equals(provider); }
    public String runtimeIdentity() { return provider + ":" + runtimeId; }
    public void requireConfigured() {
        if (!enabled || url.isBlank() || token.isBlank()) throw new ServiceException("独立评测服务未启用，请联系管理员");
        if (!Set.of("judge0", "go-judge").contains(provider)) throw new ServiceException("未知评测服务类型");
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

    public JSONObject run(String source, String input) throws Exception {
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("args", List.of("/usr/local/bin/python3", "-I", "-B", "/w/main.py"));
        command.put("env", List.of("PATH=/usr/local/bin:/usr/bin:/bin", "LANG=C.UTF-8", "HOME=/w"));
        command.put("files", List.of(Map.of("content", input),
                Map.of("name", "stdout", "max", 32768, "pipe", true),
                Map.of("name", "stderr", "max", 32768, "pipe", true)));
        command.put("copyIn", Map.of("main.py", Map.of("content", source)));
        command.put("cpuLimit", 2_000_000_000L);
        command.put("clockLimit", 5_000_000_000L);
        command.put("memoryLimit", 134217728);
        command.put("stackLimit", 67108864);
        command.put("procLimit", 16);
        command.put("copyOutMax", 65536);
        var responses = JSON.parseArray(requestBody("/run", JSON.toJSONString(Map.of("cmd", List.of(command)))));
        if (responses == null || responses.size() != 1) throw new IllegalStateException("Invalid runner response");
        var raw = responses.getJSONObject(0);
        String verdict = raw.getString("status");
        if (verdict == null) throw new IllegalStateException("Missing runner verdict");
        int status = switch (verdict) {
            case "Accepted" -> 3;
            case "Time Limit Exceeded" -> 5;
            case "Memory Limit Exceeded", "Output Limit Exceeded", "Nonzero Exit Status", "Non Zero Exit Status",
                    "Signalled", "Dangerous Syscall" -> 11;
            default -> 13;
        };
        JSONObject result = new JSONObject();
        result.put("status", Map.of("id", status));
        result.put("verdict", verdict);
        JSONObject files = raw.getJSONObject("files");
        result.put("stdout", encode(files == null ? "" : Objects.toString(files.get("stdout"), "")));
        result.put("stderr", encode(files == null ? "" : Objects.toString(files.get("stderr"), "")));
        result.put("time", raw.getLongValue("time") / 1_000_000_000.0);
        result.put("memory", raw.getLongValue("memory") / 1024);
        return result;
    }

    private JSONObject request(String path, String body) throws Exception {
        return JSON.parseObject(requestBody(path, body));
    }

    private String requestBody(String path, String body) throws Exception {
        requireConfigured();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url.replaceAll("/+$", "") + path))
                .timeout(Duration.ofSeconds(8)).header(synchronous() ? "Authorization" : "X-Auth-Token",
                        synchronous() ? "Bearer " + token : token).header("Content-Type", "application/json");
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
            int limit = synchronous() ? 524288 : 262144;
            byte[] bytes = input.readNBytes(limit + 1);
            if (response.statusCode() < 200 || response.statusCode() >= 300 || bytes.length > limit) {
                throw new IllegalStateException("Runner request failed");
            }
            return new String(bytes, StandardCharsets.UTF_8);
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
