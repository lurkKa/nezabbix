package shared;

public class ProcessInfo {
    private int pid;
    private double cpu;
    private double mem;
    private String name;

    public ProcessInfo() {
        // Пустой конструктор нужен для Jackson
    }

    public ProcessInfo(int pid, double cpu, double mem, String name) {
        this.pid = pid;
        this.cpu = cpu;
        this.mem = mem;
        this.name = name;
    }

    public int getPid() {
        return pid;
    }

    public void setPid(int pid) {
        this.pid = pid;
    }

    public double getCpu() {
        return cpu;
    }

    public void setCpu(double cpu) {
        this.cpu = cpu;
    }

    public double getMem() {
        return mem;
    }

    public void setMem(double mem) {
        this.mem = mem;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    // Необязательно, но удобно
    @Override
    public String toString() {
        return String.format("PID: %d, CPU: %.2f%%, MEM: %.2f%%, Name: %s", pid, cpu, mem, name);
    }
}
