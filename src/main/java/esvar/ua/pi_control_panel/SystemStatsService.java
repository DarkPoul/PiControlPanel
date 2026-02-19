package esvar.ua.pi_control_panel;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

@Service
public class SystemStatsService {

    private final SystemInfo si = new SystemInfo();
    private final CentralProcessor cpu = si.getHardware().getProcessor();
    private final GlobalMemory mem = si.getHardware().getMemory();
    private long[] prevCpuTicks = cpu.getSystemCpuLoadTicks();

    private static final Path PI_TEMP = Path.of("/sys/class/thermal/thermal_zone0/temp");

    public SystemStats read() {
        long uptimeSeconds = si.getOperatingSystem().getSystemUptime();

        long totalMem = mem.getTotal();
        long availMem = mem.getAvailable();

        Disk disk = readMainDisk();

        double cpuLoad = cpu.getSystemCpuLoadBetweenTicks(prevCpuTicks);
        prevCpuTicks = cpu.getSystemCpuLoadTicks();

        OptionalDouble tempC = readPiCpuTempC();

        ThrottledFlags tf = readThrottledFlags();

        // Тривоги (поки тільки по temp + throttled; далі додамо Wi-Fi/swap/disk/etc)
        List<String> warn = new ArrayList<>();
        List<String> crit = new ArrayList<>();

        // Температура
        if (tempC.isPresent()) {
            double t = tempC.getAsDouble();
            if (t >= 80.0) crit.add("Температура CPU критична: %.1f°C".formatted(t));
            else if (t >= 70.0) warn.add("Температура CPU висока: %.1f°C".formatted(t));
        }

        // Throttling / undervoltage
        if (tf.undervoltageNow) crit.add("Недостатня напруга живлення (undervoltage) — ЗАРАЗ");
        else if (tf.undervoltageOccurred) warn.add("Було undervoltage раніше (живлення просідало)");

        if (tf.throttledNow) crit.add("CPU тротлиться — ЗАРАЗ (throttling)");
        else if (tf.throttledOccurred) warn.add("Тротлінг був раніше");

        if (tf.freqCappedNow) crit.add("Частота CPU обмежена — ЗАРАЗ (frequency capped)");
        else if (tf.freqCappedOccurred) warn.add("Обмеження частоти було раніше");

        return new SystemStats(
                uptimeSeconds,
                totalMem,
                availMem,
                disk.totalBytes,
                disk.usableBytes,
                clamp01(cpuLoad),
                tempC,
                tf.undervoltageNow,
                tf.freqCappedNow,
                tf.throttledNow,
                tf.undervoltageOccurred,
                tf.freqCappedOccurred,
                tf.throttledOccurred,
                warn,
                crit
        );
    }

    private Disk readMainDisk() {
        FileSystem fs = si.getOperatingSystem().getFileSystem();
        List<OSFileStore> stores = fs.getFileStores();

        OSFileStore best = null;
        for (OSFileStore s : stores) {
            if (best == null || s.getTotalSpace() > best.getTotalSpace()) {
                best = s;
            }
        }
        if (best == null) return new Disk(0, 0);
        return new Disk(best.getTotalSpace(), best.getUsableSpace());
    }

    private OptionalDouble readPiCpuTempC() {
        try {
            if (!Files.exists(PI_TEMP)) return OptionalDouble.empty();
            String raw = Files.readString(PI_TEMP).trim(); // "42000"
            if (raw.isBlank()) return OptionalDouble.empty();
            double milli = Double.parseDouble(raw);
            return OptionalDouble.of(milli / 1000.0);
        } catch (Exception ignored) {
            return OptionalDouble.empty();
        }
    }

    private ThrottledFlags readThrottledFlags() {
        try {
            Process p = new ProcessBuilder("bash", "-lc", "vcgencmd get_throttled").start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = br.readLine(); // throttled=0x50005
                if (line == null) return ThrottledFlags.empty();

                int idx = line.indexOf("0x");
                if (idx < 0) return ThrottledFlags.empty();

                String hex = line.substring(idx + 2).trim();
                int value = (int) Long.parseLong(hex, 16);

                boolean undervoltageNow = (value & 0x1) != 0;
                boolean freqCappedNow   = (value & 0x2) != 0;
                boolean throttledNow    = (value & 0x4) != 0;

                boolean undervoltageOccurred = (value & 0x10000) != 0;
                boolean freqCappedOccurred   = (value & 0x20000) != 0;
                boolean throttledOccurred    = (value & 0x40000) != 0;

                return new ThrottledFlags(
                        undervoltageNow, freqCappedNow, throttledNow,
                        undervoltageOccurred, freqCappedOccurred, throttledOccurred
                );
            }
        } catch (Exception ignored) {
            // якщо ти тестуєш на ПК — vcgencmd не буде, і це ок
            return ThrottledFlags.empty();
        }
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    private record Disk(long totalBytes, long usableBytes) {}

    private record ThrottledFlags(
            boolean undervoltageNow,
            boolean freqCappedNow,
            boolean throttledNow,
            boolean undervoltageOccurred,
            boolean freqCappedOccurred,
            boolean throttledOccurred
    ) {
        static ThrottledFlags empty() {
            return new ThrottledFlags(false, false, false, false, false, false);
        }
    }
}
