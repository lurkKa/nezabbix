package shared;

import java.util.List;

public class SystemMetrics {
    private double cpuUsage;
    private long totalMemory;
    private long freeMemory;
    private List<ProcessInfo> processes;
    private String ip; // Добавлено

    public SystemMetrics() {}

    public SystemMetrics(double cpuUsage, long totalMemory, long freeMemory, List<ProcessInfo> processes) {
        this.cpuUsage = cpuUsage;
        this.totalMemory = totalMemory;
        this.freeMemory = freeMemory;
        this.processes = processes;
    }

    public double getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(double cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public long getTotalMemory() {
        return totalMemory;
    }

    public void setTotalMemory(long totalMemory) {
        this.totalMemory = totalMemory;
    }

    public long getFreeMemory() {
        return freeMemory;
    }

    public void setFreeMemory(long freeMemory) {
        this.freeMemory = freeMemory;
    }

    public List<ProcessInfo> getProcesses() {
        return processes;
    }

    public void setProcesses(List<ProcessInfo> processes) {
        this.processes = processes;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }
}
