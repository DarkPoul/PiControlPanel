package esvar.ua.pi_control_panel;

import java.util.List;

public record SystemStats(
        long uptimeSeconds,

        long ramTotalBytes,
        long ramAvailableBytes,

        long diskTotalBytes,
        long diskUsableBytes,

        double cpuLoad01,

        Double cpuTempC,

        // throttling flags (vcgencmd get_throttled)
        boolean undervoltageNow,
        boolean freqCappedNow,
        boolean throttledNow,

        boolean undervoltageOccurred,
        boolean freqCappedOccurred,
        boolean throttledOccurred,

        // зручно для панелі тривог (можеш не використовувати — але корисно)
        List<String> alertsWarn,
        List<String> alertsCrit
) {}
