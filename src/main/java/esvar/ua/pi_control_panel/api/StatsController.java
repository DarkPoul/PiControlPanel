package esvar.ua.pi_control_panel.api;

import esvar.ua.pi_control_panel.SystemStats;
import esvar.ua.pi_control_panel.SystemStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatsController {

    private final SystemStatsService systemStatsService;

    public StatsController(SystemStatsService systemStatsService) {
        this.systemStatsService = systemStatsService;
    }

    @GetMapping("/api/stats")
    public SystemStats readStats() {
        return systemStatsService.read();
    }
}
