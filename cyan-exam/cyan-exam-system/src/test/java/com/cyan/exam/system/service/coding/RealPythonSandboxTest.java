package com.cyan.exam.system.service.coding;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in local test. Student programs execute only in the external Linux sandbox. */
@EnabledIfEnvironmentVariable(named = "CYAN_REAL_SANDBOX_TEST", matches = "1")
class RealPythonSandboxTest {
    @Test void actualPythonOutputErrorsAndIsolation() throws Exception {
        var client = new Judge0Client();
        ReflectionTestUtils.setField(client, "enabled", true);
        ReflectionTestUtils.setField(client, "provider", "go-judge");
        ReflectionTestUtils.setField(client, "url", System.getenv("CYAN_SANDBOX_URL"));
        ReflectionTestUtils.setField(client, "token",
                Files.readString(Path.of(System.getenv("CYAN_SANDBOX_TOKEN_FILE"))).trim());
        var success = client.run("print(int(input()) * 2)", "21\n");
        assertEquals(3, success.getJSONObject("status").getIntValue("id"));
        assertEquals("42\n", Judge0Client.decode(success.getString("stdout")));
        assertEquals(11, client.run("raise ValueError('test')", "").getJSONObject("status").getIntValue("id"));
        assertEquals(11, client.run("if True print('syntax')", "").getJSONObject("status").getIntValue("id"));
        assertEquals(5, client.run("while True: pass", "").getJSONObject("status").getIntValue("id"));
        assertEquals("Output Limit Exceeded", client.run("print('x' * 100000)", "").getString("verdict"));
        assertNotEquals(3, client.run("x = bytearray(256 * 1024 * 1024)", "").getJSONObject("status").getIntValue("id"));
        var isolation = client.run("import os,socket\n"
                + "assert 'ES_AUTH_TOKEN' not in os.environ\n"
                + "assert not os.path.exists('/var/run/docker.sock')\n"
                + "assert not os.path.exists('/mnt/d/work')\n"
                + "s=socket.socket(); s.settimeout(0.3)\n"
                + "assert s.connect_ex(('1.1.1.1',80)) != 0\n"
                + "print('isolated')", "");
        assertEquals(3, isolation.getJSONObject("status").getIntValue("id"));
        assertEquals("isolated\n", Judge0Client.decode(isolation.getString("stdout")));
        ReflectionTestUtils.setField(client, "token", "invalid-token");
        assertThrows(IllegalStateException.class, () -> client.run("print(1)", ""));
    }
}
