package com.ramoptimizer.model;

import java.util.Objects;

/**
 * Immutable snapshot of a process relevant for RAM monitoring.
 */
public final class ProcessInfo {
    private final int pid;
    private final String name;
    private final String path;
    private final String commandLine;
    private final long residentSetSize; // bytes (RSS / Working Set)
    private final Category category;
    private final double cpuPercent;    // approximate, may be 0 on some platforms

    public ProcessInfo(int pid, String name, String path, String commandLine,
                       long residentSetSize, Category category, double cpuPercent) {
        this.pid = pid;
        this.name = name != null ? name : "";
        this.path = path != null ? path : "";
        this.commandLine = commandLine != null ? commandLine : "";
        this.residentSetSize = Math.max(0, residentSetSize);
        this.category = category != null ? category : Category.UNKNOWN;
        this.cpuPercent = cpuPercent;
    }

    public int getPid() {
        return pid;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public String getCommandLine() {
        return commandLine;
    }

    public long getResidentSetSize() {
        return residentSetSize;
    }

    public Category getCategory() {
        return category;
    }

    public double getCpuPercent() {
        return cpuPercent;
    }

    /** Human-readable memory (e.g. 1.23 GB). */
    public String getMemoryFormatted() {
        return formatBytes(residentSetSize);
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProcessInfo that)) return false;
        return pid == that.pid;
    }

    @Override
    public int hashCode() {
        return Objects.hash(pid);
    }

    @Override
    public String toString() {
        return name + " (PID " + pid + ") " + getMemoryFormatted() + " [" + category + "]";
    }
}
