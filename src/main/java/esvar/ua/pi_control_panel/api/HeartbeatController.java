package esvar.ua.pi_control_panel.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class HeartbeatController {

    @GetMapping("/api/heartbeat")
    public Map<String, Object> heartbeat() {
        return Map.of(
                "ok", true,
                "ts", Instant.now().toString()
        );
    }
}
