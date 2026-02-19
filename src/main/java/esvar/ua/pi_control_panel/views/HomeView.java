package esvar.ua.pi_control_panel.views;

import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.router.Route;
import esvar.ua.pi_control_panel.SystemStats;
import esvar.ua.pi_control_panel.SystemStatsService;

import java.time.Duration;
import java.util.Timer;
import java.util.TimerTask;

@Route("")
public class HomeView extends VerticalLayout {

    private final SystemStatsService statsService;

    private final Span uptime = new Span();

    private final ProgressBar ramBar = new ProgressBar();
    private final Span ramText = new Span();

    private final ProgressBar diskBar = new ProgressBar();
    private final Span diskText = new Span();

    private final ProgressBar cpuBar = new ProgressBar();
    private final Span cpuText = new Span();

    // Нове: панель тривог
    private final Div alertBar = new Div();

    // Нове: додаткова “стрічка” деталей у CPU картці
    private final Span cpuDetails = new Span();

    private Div ramCard;
    private Div diskCard;
    private Div cpuCard;

    private Timer timer;

    public HomeView(SystemStatsService statsService) {
        this.statsService = statsService;

        addClassName("cp-root");
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        setAlignItems(FlexComponent.Alignment.STRETCH);

        Div bg = new Div();
        bg.addClassName("cp-bg");

        Div shell = new Div();
        shell.addClassName("cp-shell");

        Div header = new Div();
        header.addClassName("cp-header");

        H1 title = new H1("PI CONTROL PANEL");
        title.addClassName("cp-title");

        Span subtitle = new Span("НЕЙРО-HUD • ТЕЛЕМЕТРІЯ СИСТЕМИ");
        subtitle.addClassName("cp-subtitle");

        Div titleBlock = new Div(title, subtitle);
        titleBlock.addClassName("cp-title-block");

        Div statusPill = new Div(
                new Span("●"),
                new Span("ОНЛАЙН"),
                new Span("—"),
                uptime
        );
        statusPill.addClassName("cp-pill");
        uptime.addClassName("cp-pill-uptime");

        header.add(titleBlock, statusPill);

        // Панель тривог (широка, під заголовком)
        alertBar.addClassName("cp-alert-bar");

        Div grid = new Div();
        grid.addClassName("cp-grid");

        ramCard  = metricCard("🧠", "ОЗП",  "Тиск памʼяті",        ramBar,  ramText,  "cp-ram");
        diskCard = metricCard("💾", "ДИСК", "Цілісність сховища",  diskBar, diskText, "cp-disk");
        cpuCard  = metricCard("⚡", "CPU",  "Обчислювальне ядро",  cpuBar,  cpuText,  "cp-cpu");

        // Додаємо деталі CPU під основним текстом у CPU-картці (темп/тротл)
        cpuDetails.addClassName("cp-subvalue");
        cpuCard.add(cpuDetails);

        grid.add(ramCard, diskCard, cpuCard);

        Div footer = new Div(new Span("Порада: тримай вкладку відкритою — метрики оновлюються кожні 5 секунд."));
        footer.addClassName("cp-footer");

        shell.add(header, alertBar, grid, footer);

        Div stage = new Div(bg, shell);
        stage.addClassName("cp-stage");
        add(stage);

        ramBar.addClassName("cp-bar");
        diskBar.addClassName("cp-bar");
        cpuBar.addClassName("cp-bar");

        updateStats();
        startAutoRefresh();
    }

    private Div metricCard(String iconText, String label, String hint, ProgressBar bar, Span value, String extraClass) {
        Div card = new Div();
        card.addClassName("cp-card");
        card.addClassName(extraClass);

        Div top = new Div();
        top.addClassName("cp-card-top");

        Div left = new Div();
        left.addClassName("cp-card-left");

        Span icon = new Span(iconText);
        icon.addClassName("cp-icon");

        Span lbl = new Span(label);
        lbl.addClassName("cp-label");

        left.add(icon, lbl);

        Span h = new Span(hint);
        h.addClassName("cp-hint");

        top.add(left, h);

        Div body = new Div();
        body.addClassName("cp-card-body");

        value.addClassName("cp-value");

        body.add(bar, value);
        card.add(top, body);

        return card;
    }

    private void updateStats() {
        SystemStats s = statsService.read();

        uptime.setText("Аптайм " + formatDuration(Duration.ofSeconds(s.uptimeSeconds())));

        // RAM
        long ramUsed = s.ramTotalBytes() - s.ramAvailableBytes();
        double ramUsage = safeRatio(ramUsed, s.ramTotalBytes());
        ramBar.setValue(ramUsage);
        ramText.setText(" " + percent(ramUsage) + " (" + mb(ramUsed) + " / " + mb(s.ramTotalBytes()) + " MB)");
        applyThresholdClasses(ramCard, ramUsage, 0.80, 0.92);

        // Disk
        long diskUsed = s.diskTotalBytes() - s.diskUsableBytes();
        double diskUsage = safeRatio(diskUsed, s.diskTotalBytes());
        diskBar.setValue(diskUsage);
        diskText.setText(" " + percent(diskUsage) + " (" + gb(diskUsed) + " / " + gb(s.diskTotalBytes()) + " GB)");
        applyThresholdClasses(diskCard, diskUsage, 0.85, 0.95);

        // CPU
        double cpu = clamp01(s.cpuLoad01());
        cpuBar.setValue(cpu);
        cpuText.setText(" " + percent(cpu));
        applyThresholdClasses(cpuCard, cpu, 0.85, 0.95);

        // CPU details: температура + throttling (дублюємо тут)
        String tempPart = s.cpuTempC().isPresent()
                ? ("🌡 " + String.format("%.1f°C", s.cpuTempC().getAsDouble()))
                : "🌡 н/д";

        String powerPart = buildPowerStateText(s);

        cpuDetails.setText(tempPart + "  •  " + powerPart);

        // Тривоги: будуємо широку панель чіпів
        rebuildAlertBar(s, cpu);
    }

    private String buildPowerStateText(SystemStats s) {
        boolean crit = s.undervoltageNow() || s.throttledNow() || s.freqCappedNow();
        boolean warn = (!crit) && (s.undervoltageOccurred() || s.throttledOccurred() || s.freqCappedOccurred());

        if (crit) return "⚠️ живлення/тротл: КРИТИЧНО";
        if (warn) return "⚠️ живлення/тротл: БУЛО";
        return "✅ живлення: стабільно";
    }

    private void rebuildAlertBar(SystemStats s, double cpu01) {
        alertBar.removeAll();

        boolean hasCrit = !s.alertsCrit().isEmpty();
        boolean hasWarn = !s.alertsWarn().isEmpty();

        // Додатково: CPU temp пороги (навіть якщо alerts списки пусті на ПК)
        double temp = s.cpuTempC().orElse(-1);
        boolean tempCrit = temp >= 80.0;
        boolean tempWarn = temp >= 70.0 && temp < 80.0;

        // 1) критичні
        for (String msg : s.alertsCrit()) {
            alertBar.add(alertChip("🔴", msg, true));
        }
        if (tempCrit) {
            alertBar.add(alertChip("🔴", "Температура CPU критична", true));
        }

        // 2) попередження
        for (String msg : s.alertsWarn()) {
            alertBar.add(alertChip("🟡", msg, false));
        }
        if (tempWarn) {
            alertBar.add(alertChip("🟡", "Температура CPU висока", false));
        }

        // 3) якщо немає нічого — зелений “все добре”
        if (!hasCrit && !hasWarn && !tempCrit && !tempWarn) {
            alertBar.add(alertChip("🟢", "Система стабільна. Тривог немає.", false));
            alertBar.addClassName("is-ok");
            alertBar.removeClassName("is-warn");
            alertBar.removeClassName("is-crit");
        } else {
            alertBar.removeClassName("is-ok");
            if (hasCrit || tempCrit) {
                alertBar.addClassName("is-crit");
                alertBar.removeClassName("is-warn");
            } else {
                alertBar.addClassName("is-warn");
                alertBar.removeClassName("is-crit");
            }
        }

        // Пороги для CPU картки за температурою (додаємо поверх % CPU)
        applyTempThresholdToCpuCard(s);
    }

    private void applyTempThresholdToCpuCard(SystemStats s) {
        if (s.cpuTempC().isEmpty()) return;

        double t = s.cpuTempC().getAsDouble();

        // Не ламаємо твою логику CPU% — просто додаємо клас на карту
        if (t >= 80.0) {
            cpuCard.removeClassName("is-warn");
            cpuCard.addClassName("is-crit");
        } else if (t >= 70.0) {
            if (!cpuCard.hasClassName("is-crit")) {
                cpuCard.addClassName("is-warn");
            }
        }
    }

    private Div alertChip(String dot, String text, boolean crit) {
        Div chip = new Div();
        chip.addClassName("cp-alert-chip");
        if (crit) chip.addClassName("is-crit");

        Span d = new Span(dot);
        d.addClassName("cp-alert-dot");

        Span t = new Span(text);
        t.addClassName("cp-alert-text");

        chip.add(d, t);
        return chip;
    }

    private void applyThresholdClasses(Div card, double value01, double warn, double crit) {
        card.removeClassName("is-warn");
        card.removeClassName("is-crit");
        if (value01 >= crit) card.addClassName("is-crit");
        else if (value01 >= warn) card.addClassName("is-warn");
    }

    private void startAutoRefresh() {
        UI ui = UI.getCurrent();
        timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                ui.access(() -> updateStats());
            }
        }, 2000, 5000);
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private static double safeRatio(long num, long den) {
        if (den <= 0) return 0;
        return clamp01((double) num / (double) den);
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    private static String percent(double v01) {
        return String.format("%.0f%%", v01 * 100.0);
    }

    private static long mb(long bytes) {
        return bytes / 1024 / 1024;
    }

    private static long gb(long bytes) {
        return bytes / 1024 / 1024 / 1024;
    }

    private static String formatDuration(Duration d) {
        long hours = d.toHours();
        long minutes = d.minusHours(hours).toMinutes();
        return hours + "h " + minutes + "m";
    }
}
