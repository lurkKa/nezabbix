package com.example.agent;

import shared.ProcessInfo;
import shared.SystemMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class AgentMain {
    public static void main(String[] args) throws IOException {
        int listenPort = 8081;
        HttpServer server = HttpServer.create(new InetSocketAddress(listenPort), 0);
        server.createContext("/metrics", AgentMain::handleRequest);
        server.createContext("/control", AgentMain::handleControl);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("Agent started and listening on port " + listenPort);
    }

    private static void handleRequest(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        SystemMetrics metrics = collectMetrics();
        sendJson(exchange, 200, metrics);
    }

    private static void handleControl(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> command;

        try {
            command = mapper.readValue(exchange.getRequestBody(), Map.class);
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(exchange, 400, Map.of("error", "Invalid JSON format"));
            return;
        }

        String action = (String) command.get("action");
        Integer pidStr = (Integer) command.get("pid");

        if (action == null || pidStr == null) {
            sendJson(exchange, 400, Map.of("error", "Missing 'action' or 'pid' field"));
            return;
        }

        int pid;
        try {
            pid = pidStr;
        } catch (NumberFormatException e) {
            sendJson(exchange, 400, Map.of("error", "Invalid PID format"));
            return;
        }

        try {
            boolean success = runCommand(action, pid);
            sendJson(exchange, 200, Map.of("success", success));
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            sendJson(exchange, 400, Map.of("error", "Invalid action: " + action));
        } catch (RuntimeException | IOException e) {
            e.printStackTrace();
            sendJson(exchange, 500, Map.of("error", "Command execution failed", "details", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(exchange, 500, Map.of("error", "Internal Server Error", "details", e.getMessage()));
        }
    }

    private static boolean runCommand(String action, int pid) throws IOException, InterruptedException {
        String os = System.getProperty("os.name").toLowerCase();
        String command = null;

        if (os.contains("win")) {
            switch (action) {
                case "kill":
                    command = "taskkill /PID " + pid + " /F";
                    break;
                case "suspend":
                case "resume":
                    throw new IllegalArgumentException("Action not supported on Windows: " + action);
            }
        } else if (os.contains("nix") || os.contains("nux") || os.contains("mac")) {
            switch (action) {
                case "kill":
                    command = "kill -9 " + pid;
                    break;
                case "suspend":
                    command = "kill -STOP " + pid;
                    break;
                case "resume":
                    command = "kill -CONT " + pid;
                    break;
            }
        }

        if (command == null) {
            throw new IllegalArgumentException("Unknown action or OS: " + action);
        }

        Process proc = Runtime.getRuntime().exec(command);

        try (BufferedReader inputReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
             BufferedReader errorReader = new BufferedReader(new InputStreamReader(proc.getErrorStream()))) {

            while (inputReader.readLine() != null) {}
            StringBuilder errorOutput = new StringBuilder();
            String line;
            while ((line = errorReader.readLine()) != null) {
                errorOutput.append(line).append("\n");
            }

            int exitCode = proc.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Command failed: " + errorOutput.toString());
            }
        }

        return true;
    }

    private static void sendJson(HttpExchange exchange, int code, Object data) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(data);
        byte[] jsonBytes = json.getBytes();

        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, jsonBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(jsonBytes);
        }
    }

    private static SystemMetrics collectMetrics() {
        SystemInfo si = new SystemInfo();
        CentralProcessor cpu = si.getHardware().getProcessor();
        GlobalMemory memory = si.getHardware().getMemory();
        OperatingSystem os = si.getOperatingSystem();

        try {
            long[] prevTicks = cpu.getSystemCpuLoadTicks();
            Thread.sleep(1000);
            double cpuLoad = cpu.getSystemCpuLoadBetweenTicks(prevTicks) * 100;

            long totalMem = memory.getTotal();
            long freeMem = memory.getAvailable();

            // 🔧 Получаем все доступные процессы, без сортировки и ограничения
            List<OSProcess> osProcesses = os.getProcesses();
            List<ProcessInfo> processes = osProcesses.stream()
                    .map(p -> {
                        ProcessInfo info = new ProcessInfo();
                        info.setPid(p.getProcessID());
                        info.setName(p.getName());
                        info.setCpu(p.getProcessCpuLoadCumulative() * 100);
                        info.setMem(((double) p.getResidentSetSize() / totalMem) * 100);
                        return info;
                    })
                    .collect(Collectors.toList());

            return new SystemMetrics(cpuLoad, totalMem, freeMem, processes);
        } catch (InterruptedException e) {
            e.printStackTrace();
            return new SystemMetrics();
        }
    }
}
