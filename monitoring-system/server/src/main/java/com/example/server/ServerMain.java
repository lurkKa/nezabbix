package com.example.server;

import static spark.Spark.*;

import shared.SystemMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Map;

public class ServerMain {
    private static final Map<String, SystemMetrics> metricsByIp = new ConcurrentHashMap<>();
    private static final String SUBNET = "192.168.1.";
    private static final int AGENT_PORT = 8081;

    public static void main(String[] args) {
        ipAddress("0.0.0.0");
        port(8080);

        System.out.println("Server started on port 8080");

        startAgentPolling();

        get("/metrics", (req, res) -> {
            String ip = req.queryParams("ip");
            res.type("application/json");

            ObjectMapper mapper = new ObjectMapper();
            if (ip != null) {
                SystemMetrics metrics = metricsByIp.get(ip);
                return mapper.writeValueAsString(metrics != null ? metrics : new SystemMetrics());
            } else {
                return mapper.writeValueAsString(metricsByIp);
            }
        });
    }

    private static void startAgentPolling() {
        java.util.concurrent.ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            for (int i = 1; i <= 254; i++) {
                String ip = SUBNET + i;
                new Thread(() -> pollAgent(ip)).start();
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    private static void pollAgent(String ip) {
        try {
            URL url = new URL("http://" + ip + ":" + AGENT_PORT + "/metrics");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() == 200) {
                ObjectMapper mapper = new ObjectMapper();
                SystemMetrics metrics = mapper.readValue(conn.getInputStream(), SystemMetrics.class);
                metricsByIp.put(ip, metrics);
                System.out.println("Updated metrics from " + ip);
            }
        } catch (Exception e) {
            System.out.println("Agent not responding at " + ip);
        }
    }
}
