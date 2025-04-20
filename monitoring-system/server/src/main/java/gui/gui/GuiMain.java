package gui.gui;

import com.example.server.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import gui.auth.LoginStage;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import shared.ProcessInfo;
import shared.SystemMetrics;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class GuiMain extends Application {
    private static final int SERVER_PORT = 8080;
    private static final String METRICS_PATH = "/metrics";
    private static final String SUBNET = "192.168.1.";

    private final Map<String, SystemMetrics> metricsByIp = new HashMap<>();
    private final ObservableList<ProcessInfo> processData = FXCollections.observableArrayList();

    private Label cpuLabel = new Label("CPU: ");
    private Label memLabel = new Label("Memory: ");
    private ComboBox<String> ipSelector = new ComboBox<>();
    private TableView<ProcessInfo> table = new TableView<>();

    private String serverIp = null;
    private Timer pollingTimer;
    private static String userRole;

    @Override
    public void start(Stage primaryStage) {
        new LoginStage(role -> {
            userRole = role;
            Platform.runLater(() -> showDashboard(primaryStage));
        }).show();
    }

    private void showDashboard(Stage primaryStage) {
        setupTable();

        // --- Новый поиск ---
        TextField searchField = new TextField();
        searchField.setPromptText("🔍 Введите имя процесса");
        searchField.setStyle("-fx-border-radius: 6; -fx-background-radius: 6;");

        Button searchButton = new Button("Найти");
        searchButton.setStyle("-fx-background-color: #4299e1; -fx-text-fill: white;");
        searchButton.setOnAction(e -> searchProcessByName(searchField.getText()));

        HBox searchBox = new HBox(10, searchField, searchButton);

        // --- Кнопки управления ---
        Button killButton = new Button("🛑 Kill");
        Button suspendButton = new Button("⏸ Suspend");
        Button resumeButton = new Button("▶ Resume");

        killButton.setStyle("-fx-background-color: #ff4d4d; -fx-text-fill: white;");
        suspendButton.setStyle("-fx-background-color: #ffcc00; -fx-text-fill: black;");
        resumeButton.setStyle("-fx-background-color: #66cc66; -fx-text-fill: white;");

        killButton.setTooltip(new Tooltip("Остановить выбранный процесс"));
        suspendButton.setTooltip(new Tooltip("Приостановить выбранный процесс"));
        resumeButton.setTooltip(new Tooltip("Возобновить выбранный процесс"));

        if (!"ADMIN".equals(userRole)) {
            killButton.setDisable(true);
            suspendButton.setDisable(true);
            resumeButton.setDisable(true);
        }

        killButton.setOnAction(e -> handleAction("kill"));
        suspendButton.setOnAction(e -> handleAction("suspend"));
        resumeButton.setOnAction(e -> handleAction("resume"));

        VBox agentBox = new VBox(5, new Label("🧠 Выбор агента:"), ipSelector);
        VBox metricsBox = new VBox(5, cpuLabel, memLabel);
        VBox controlsBox = new VBox(5, killButton, suspendButton, resumeButton);

        TitledPane agentPane = new TitledPane("Агент", agentBox);
        TitledPane metricsPane = new TitledPane("Системные метрики", metricsBox);
        TitledPane tablePane = new TitledPane("🔍 Процессы", table);
        TitledPane controlsPane = new TitledPane("⚙ Управление", controlsBox);

        // добавлен searchBox
        VBox root = new VBox(10, agentPane, metricsPane, searchBox, tablePane, controlsPane);
        applyStyle(root);

        primaryStage.setTitle("💻 Task Manager Dashboard [" + userRole + "]");
        primaryStage.setScene(new Scene(root, 700, 500));
        primaryStage.show();

        ipSelector.setOnAction(e -> updateDisplayForSelectedIp());

        if ("GUEST".equals(userRole)) {
            findOnlyLocalServer();
        } else {
            findServerInSubnet();
        }
    }

    private void setupTable() {
        TableColumn<ProcessInfo, Number> pidCol = new TableColumn<>("PID");
        pidCol.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getPid()));

        TableColumn<ProcessInfo, Number> cpuCol = new TableColumn<>("CPU (%)");
        cpuCol.setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getCpu()));

        TableColumn<ProcessInfo, Number> memCol = new TableColumn<>("MEM (%)");
        memCol.setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getMem()));

        TableColumn<ProcessInfo, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));

        table.getColumns().addAll(pidCol, cpuCol, memCol, nameCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setItems(processData);
    }

    private void findServerInSubnet() {
        for (int i = 1; i <= 254; i++) {
            String ip = SUBNET + i;
            new Thread(() -> {
                if (isServerAvailable(ip)) {
                    Platform.runLater(() -> {
                        if (!ipSelector.getItems().contains(ip)) {
                            ipSelector.getItems().add(ip);
                        }
                        if (ipSelector.getValue() == null) {
                            ipSelector.setValue(ip);
                        }
                        serverIp = ip;
                        startPollingServer();
                    });
                }
            }).start();
        }
    }

    private void findOnlyLocalServer() {
        new Thread(() -> {
            String ip = SUBNET + "100"; // например, только 192.168.1.100
            if (isServerAvailable(ip)) {
                serverIp = ip;
                Platform.runLater(() -> {
                    ipSelector.getItems().add(ip);
                    ipSelector.setValue(ip);
                    startPollingServer();
                });
            }
        }).start();
    }

    private boolean isServerAvailable(String ip) {
        try {
            URL url = new URL("http://" + ip + ":" + SERVER_PORT + METRICS_PATH);
            try (InputStream in = url.openStream()) {
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }
    private void searchProcessByName(String name) {
        if (name == null || name.isBlank()) return;

        for (ProcessInfo process : processData) {
            if (process.getName().toLowerCase().contains(name.toLowerCase())) {
                table.getSelectionModel().select(process);
                table.scrollTo(process);
                return;
            }
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Поиск процесса");
        alert.setHeaderText(null);
        alert.setContentText("Процесс с именем \"" + name + "\" не найден.");
        alert.showAndWait();
    }

    private void startPollingServer() {
        if (pollingTimer != null) pollingTimer.cancel();

        pollingTimer = new Timer(true);
        pollingTimer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                try {
                    URL url = new URL("http://" + serverIp + ":" + SERVER_PORT + METRICS_PATH);
                    try (InputStream in = url.openStream()) {
                        ObjectMapper mapper = new ObjectMapper();
                        Map<String, SystemMetrics> newMetricsMap = mapper.readValue(in,
                                new TypeReference<>() {
                                });

                        metricsByIp.clear();
                        metricsByIp.putAll(newMetricsMap);

                        Platform.runLater(() -> {
                            updateAgentSelector();
                            updateDisplayForSelectedIp();
                        });
                    }
                } catch (Exception e) {
                    System.out.println("Failed to poll server.");
                }
            }
        }, 0, 2000);
    }

    private void updateAgentSelector() {
        Set<String> currentIps = new HashSet<>(ipSelector.getItems());
        for (String ip : metricsByIp.keySet()) {
            if (!currentIps.contains(ip)) {
                ipSelector.getItems().add(ip);
            }
        }

        if (ipSelector.getValue() == null && !ipSelector.getItems().isEmpty()) {
            ipSelector.setValue(ipSelector.getItems().get(0));
        }
    }

    private void updateDisplayForSelectedIp() {
        String selectedIp = ipSelector.getValue();
        if (selectedIp == null) return;

        SystemMetrics metrics = metricsByIp.get(selectedIp);
        if (metrics == null) return;

        cpuLabel.setText(String.format("CPU: %.2f%%", metrics.getCpuUsage()));
        memLabel.setText(String.format("Memory: %d MB free / %d MB total",
                metrics.getFreeMemory() / 1024 / 1024,
                metrics.getTotalMemory() / 1024 / 1024));

        List<ProcessInfo> newProcesses = metrics.getProcesses();

        List<TableColumn<ProcessInfo, ?>> sortOrder = new ArrayList<>(table.getSortOrder());
        ProcessInfo selectedProcess = table.getSelectionModel().getSelectedItem();
        Integer selectedPid = selectedProcess != null ? selectedProcess.getPid() : null;

        processData.setAll(newProcesses != null ? newProcesses : List.of());

        table.getSortOrder().setAll(sortOrder);

        if (selectedPid != null) {
            for (ProcessInfo p : processData) {
                if (p.getPid() == selectedPid) {
                    table.getSelectionModel().select(p);
                    break;
                }
            }
        }
    }

    private void handleAction(String action) {
        ProcessInfo selected = table.getSelectionModel().getSelectedItem();
        if (selected != null) {
            sendActionToAgent(ipSelector.getValue(), selected.getPid(), action);
        }
    }

    private void sendActionToAgent(String ip, int pid, String action) {
        try {
            URL url = new URL("http://" + ip + ":8081/control");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            Map<String, Object> data = new HashMap<>();
            data.put("pid", pid);
            data.put("action", action);

            ObjectMapper mapper = new ObjectMapper();
            try (OutputStream os = conn.getOutputStream()) {
                os.write(mapper.writeValueAsBytes(data));
            }

            int responseCode = conn.getResponseCode();
            try (InputStream is = responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream()) {
                if (is != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            System.out.println(line);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void applyStyle(VBox root) {
        root.setStyle("""
        -fx-padding: 20;
        -fx-spacing: 15;
        -fx-background-color: linear-gradient(to bottom, #f7fafc, #ebf8ff);
    """);

        table.setStyle("""
        -fx-table-cell-border-color: transparent;
        -fx-control-inner-background: white;
        -fx-selection-bar: #90cdf4;
        -fx-selection-bar-non-focused: #bee3f8;
        -fx-table-header-background: #ebf8ff;
        -fx-font-size: 13px;
        -fx-text-fill: #2a4365;
        -fx-border-radius: 8;
        -fx-background-radius: 8;
    """);

        for (TitledPane pane : root.getChildren().filtered(node -> node instanceof TitledPane).toArray(TitledPane[]::new)) {
            pane.setAnimated(true);
            pane.setStyle("""
            -fx-text-fill: #2c5282;
            -fx-font-size: 15px;
            -fx-background-color: #ebf8ff;
            -fx-border-color: #cbd5e0;
            -fx-border-radius: 10;
            -fx-background-radius: 10;
            -fx-font-weight: bold;
        """);
        }

        cpuLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2b6cb0;");
        memLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2b6cb0;");
        ipSelector.setStyle("""
        -fx-background-color: white;
        -fx-border-color: #cbd5e0;
        -fx-border-radius: 6;
        -fx-background-radius: 6;
        -fx-padding: 4;
    """);
    }

    public static void main(String[] args) {
        launch(args);
    }
}