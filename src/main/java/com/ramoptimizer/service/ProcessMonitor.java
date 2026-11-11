package com.ramoptimizer.service;

import com.ramoptimizer.model.Category;
import com.ramoptimizer.model.ProcessInfo;
import oshi.SystemInfo;
import oshi.hardware.GlobalMemory;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Periodically samples running processes via OSHI and notifies listeners.
 * Designed to be lightweight for background operation.
 */
public class ProcessMonitor implements Runnable {

    private final SystemInfo systemInfo = new SystemInfo();
    private final OperatingSystem os = systemInfo.getOperatingSystem();
    private final GlobalMemory memory = systemInfo.getHardware().getMemory();

    private final List<Consumer<List<ProcessInfo>>> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean running = false;
    private volatile long refreshIntervalMs = 2000;

    // Soft limits per category (bytes). 0 = no limit.
    private final Map<Category, Long> categoryLimits = new EnumMap<>(Category.class);

    public ProcessMonitor() {
        // Sensible defaults (can be changed from UI)
        categoryLimits.put(Category.BROWSER, 4L * 1024 * 1024 * 1024); // 4 GB
        categoryLimits.put(Category.GAMES,   8L * 1024 * 1024 * 1024); // 8 GB
        categoryLimits.put(Category.UTILITY, 2L * 1024 * 1024 * 1024); // 2 GB
        categoryLimits.put(Category.UNKNOWN, 3L * 1024 * 1024 * 1024); // 3 GB
        categoryLimits.put(Category.SYSTEM,  0L); // never auto-limit system
    }

    public void addListener(Consumer<List<ProcessInfo>> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<List<ProcessInfo>> listener) {
        listeners.remove(listener);
    }

    public void setRefreshIntervalMs(long ms) {
        this.refreshIntervalMs = Math.max(500, ms);
    }

    public long getRefreshIntervalMs() {
        return refreshIntervalMs;
    }

    public void setCategoryLimit(Category category, long bytes) {
        categoryLimits.put(category, Math.max(0, bytes));
    }

    public long getCategoryLimit(Category category) {
        return categoryLimits.getOrDefault(category, 0L);
    }

    public Map<Category, Long> getAllCategoryLimits() {
        return Collections.unmodifiableMap(categoryLimits);
    }

    public long getTotalMemory() {
        return memory.getTotal();
    }

    public long getAvailableMemory() {
        return memory.getAvailable();
    }

    public long getUsedMemory() {
        return memory.getTotal() - memory.getAvailable();
    }

    public void start() {
        if (running) return;
        running = true;
        Thread t = new Thread(this, "ProcessMonitor");
        t.setDaemon(true);
        t.start();
    }

    public void stop() {
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public void run() {
        while (running) {
            try {
                List<ProcessInfo> snapshot = sample();
                for (Consumer<List<ProcessInfo>> listener : listeners) {
                    try {
                        listener.accept(snapshot);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                Thread.sleep(refreshIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                e.printStackTrace();
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            }
        }
    }

    public List<ProcessInfo> sample() {
        List<OSProcess> processes = os.getProcesses(
                null,
                OperatingSystem.ProcessSorting.RSS_DESC,
                0
        );

        List<ProcessInfo> result = new ArrayList<>(processes.size());
        for (OSProcess p : processes) {
            if (p.getProcessID() <= 0) continue;
            String name = p.getName();
            String path = p.getPath();
            String cmd = p.getCommandLine();
            long rss = p.getResidentMemory();
            Category cat = Categorizer.categorize(name, path, cmd);
            double cpu = p.getProcessCpuLoadCumulative();

            result.add(new ProcessInfo(
                    p.getProcessID(),
                    name,
                    path,
                    cmd,
                    rss,
                    cat,
                    cpu
            ));
        }
        return result;
    }

    /**
     * Attempts to terminate a process by PID using ProcessHandle.
     */
    public boolean terminateProcess(int pid) {
        try {
            return ProcessHandle.of(pid)
                    .map(ph -> ph.destroy() || ph.destroyForcibly())
                    .orElse(false);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Returns processes that currently exceed their category soft limit.
     */
    public List<ProcessInfo> findOverLimit(List<ProcessInfo> processes) {
        return processes.stream()
                .filter(p -> {
                    long limit = categoryLimits.getOrDefault(p.getCategory(), 0L);
                    return limit > 0 && p.getResidentSetSize() > limit;
                })
                .collect(Collectors.toList());
    }
}
