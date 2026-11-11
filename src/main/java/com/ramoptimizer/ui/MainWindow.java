package com.ramoptimizer.ui;

import com.ramoptimizer.model.Category;
import com.ramoptimizer.model.ProcessInfo;
import com.ramoptimizer.service.ProcessMonitor;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import java.util.EnumMap;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Minimal always-on-top style window for the RAM optimizer.
 * Designed to stay small and usable in the background.
 */
public class MainWindow {

    private final ProcessMonitor monitor;
    private final Stage stage;
    private final ObservableList<ProcessInfo> processList = FXCollections.observableArrayList();
    private final FilteredList<ProcessInfo> filteredList = new FilteredList<>(processList, p -> true);
    private final Label memSummaryLabel = new Label();
    private final Label statusLabel = new Label("Ready");
    private final ComboBox<Category> categoryFilter = new ComboBox<>();
    private final AtomicBoolean autoKillEnabled = new AtomicBoolean(false);
    private TableView<ProcessInfo> table;

    public MainWindow(ProcessMonitor monitor, Stage stage) {
        this.monitor = monitor;
        this.stage = stage;
        buildUi();
        monitor.addListener(this::onNewSnapshot);
        monitor.start();
    }

    private void buildUi() {
        stage.setTitle("RAM Optimizer");
        stage.setAlwaysOnTop(true);
        stage.initStyle(StageStyle.DECORATED);
        stage.setMinWidth(520);
        stage.setMinHeight(360);

        // Top: memory summary + controls
        HBox topBar = new HBox(10);
        topBar.setPadding(new Insets(8, 10, 6, 10));
        topBar.setAlignment(Pos.CENTER_LEFT);

        memSummaryLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        updateMemorySummary();

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setOnAction(e -> forceRefresh());

        CheckBox autoKillCb = new CheckBox("Auto-end over limit");
        autoKillCb.setTooltip(new Tooltip("When enabled, processes exceeding category soft limits are terminated"));
        autoKillCb.selectedProperty().addListener((obs, o, n) -> autoKillEnabled.set(n));

        Button limitsBtn = new Button("Category Limits...");
        limitsBtn.setOnAction(e -> showLimitsDialog());

        topBar.getChildren().addAll(memSummaryLabel, new Separator(), refreshBtn, autoKillCb, limitsBtn);

        // Filter bar
        HBox filterBar = new HBox(8);
        filterBar.setPadding(new Insets(0, 10, 6, 10));
        filterBar.setAlignment(Pos.CENTER_LEFT);

        categoryFilter.getItems().add(null); // "All"
        categoryFilter.getItems().addAll(Category.values());
        categoryFilter.setPromptText("All categories");

        TextField searchField = new TextField();
        searchField.setPromptText("Filter by name...");
        searchField.setPrefWidth(180);
        searchField.textProperty().addListener((obs, o, n) -> applyFilter(searchField.getText(), categoryFilter.getValue()));

        categoryFilter.setOnAction(e -> applyFilter(searchField.getText(), categoryFilter.getValue()));

        filterBar.getChildren().addAll(new Label("Category:"), categoryFilter,
                new Label("Search:"), searchField);

        // Table
        table = createTable();
        SortedList<ProcessInfo> sorted = new SortedList<>(filteredList);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);

        // Bottom status + actions
        HBox bottomBar = new HBox(10);
        bottomBar.setPadding(new Insets(6, 10, 8, 10));
        bottomBar.setAlignment(Pos.CENTER_LEFT);

        Button endBtn = new Button("End selected process");
        endBtn.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white;");
        endBtn.setOnAction(e -> endSelected());

        Button endOverBtn = new Button("End all over-limit");
        endOverBtn.setOnAction(e -> endOverLimit());

        statusLabel.setStyle("-fx-text-fill: #555;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bottomBar.getChildren().addAll(endBtn, endOverBtn, spacer, statusLabel);

        VBox root = new VBox(topBar, filterBar, table, bottomBar);
        VBox.setVgrow(table, Priority.ALWAYS);

        Scene scene = new Scene(root, 720, 480);
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            monitor.stop();
            Platform.exit();
        });
    }

    private TableView<ProcessInfo> createTable() {
        TableView<ProcessInfo> tv = new TableView<>();
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        TableColumn<ProcessInfo, String> nameCol = new TableColumn<>("Process");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        nameCol.setPrefWidth(160);

        TableColumn<ProcessInfo, String> pidCol = new TableColumn<>("PID");
        pidCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getPid())));
        pidCol.setPrefWidth(60);

        TableColumn<ProcessInfo, String> memCol = new TableColumn<>("Memory");
        memCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getMemoryFormatted()));
        memCol.setPrefWidth(90);
        memCol.setComparator((a, b) -> {
            // sort by actual bytes if possible - fall back to string
            return a.compareTo(b);
        });

        TableColumn<ProcessInfo, String> catCol = new TableColumn<>("Category");
        catCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCategory().getDisplayName()));
        catCol.setPrefWidth(90);
        catCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    ProcessInfo row = getTableView().getItems().get(getIndex());
                    if (row != null) {
                        setStyle("-fx-text-fill: " + row.getCategory().getColorHex() + "; -fx-font-weight: bold;");
                    }
                }
            }
        });

        TableColumn<ProcessInfo, String> pathCol = new TableColumn<>("Path / Command");
        pathCol.setCellValueFactory(c -> {
            String p = c.getValue().getPath();
            if (p == null || p.isBlank()) p = c.getValue().getCommandLine();
            if (p != null && p.length() > 80) p = p.substring(0, 77) + "...";
            return new SimpleStringProperty(p);
        });

        tv.getColumns().addAll(nameCol, pidCol, memCol, catCol, pathCol);

        // Double-click to end
        tv.setRowFactory(tv2 -> {
            TableRow<ProcessInfo> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !row.isEmpty()) {
                    endProcess(row.getItem());
                }
            });
            return row;
        });

        return tv;
    }

    private void applyFilter(String searchText, Category selected) {
        final String q = searchText == null ? "" : searchText.trim().toLowerCase();
        filteredList.setPredicate(p -> {
            if (selected != null && p.getCategory() != selected) return false;
            if (q.isEmpty()) return true;
            return p.getName().toLowerCase().contains(q)
                    || String.valueOf(p.getPid()).contains(q)
                    || (p.getPath() != null && p.getPath().toLowerCase().contains(q));
        });
    }

    private void onNewSnapshot(List<ProcessInfo> snapshot) {
        Platform.runLater(() -> {
            processList.setAll(snapshot);
            updateMemorySummary();

            List<ProcessInfo> over = monitor.findOverLimit(snapshot);
            if (!over.isEmpty()) {
                statusLabel.setText(over.size() + " process(es) over soft limit");
                statusLabel.setTextFill(Color.ORANGERED);
                if (autoKillEnabled.get()) {
                    for (ProcessInfo p : over) {
                        if (p.getCategory() != Category.SYSTEM) {
                            monitor.terminateProcess(p.getPid());
                        }
                    }
                    statusLabel.setText("Auto-ended " + over.size() + " over-limit process(es)");
                }
            } else {
                statusLabel.setText("Monitoring... " + snapshot.size() + " processes");
                statusLabel.setTextFill(Color.GRAY);
            }
        });
    }

    private void updateMemorySummary() {
        long used = monitor.getUsedMemory();
        long total = monitor.getTotalMemory();
        double pct = total > 0 ? (used * 100.0 / total) : 0;
        memSummaryLabel.setText(String.format("RAM: %s / %s  (%.0f%%)",
                ProcessInfo.formatBytes(used),
                ProcessInfo.formatBytes(total),
                pct));
    }

    private void forceRefresh() {
        statusLabel.setText("Refreshing...");
        List<ProcessInfo> snap = monitor.sample();
        onNewSnapshot(snap);
    }

    private void endSelected() {
        ProcessInfo selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("No process selected");
            return;
        }
        endProcess(selected);
    }

    private void endProcess(ProcessInfo p) {
        if (p.getCategory() == Category.SYSTEM) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("System process");
            alert.setHeaderText("Terminate system process?");
            alert.setContentText(p.getName() + " (PID " + p.getPid() + ") is categorized as System.\n"
                    + "Ending it may destabilize Windows. Continue?");
            if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
        }
        boolean ok = monitor.terminateProcess(p.getPid());
        statusLabel.setText(ok ? "Terminate requested for PID " + p.getPid() : "Failed to terminate PID " + p.getPid());
    }

    private void endOverLimit() {
        List<ProcessInfo> over = monitor.findOverLimit(processList);
        int count = 0;
        for (ProcessInfo p : over) {
            if (p.getCategory() != Category.SYSTEM) {
                if (monitor.terminateProcess(p.getPid())) count++;
            }
        }
        statusLabel.setText("Requested end for " + count + " over-limit process(es)");
    }

    private void showLimitsDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Category Soft Limits");
        dialog.setHeaderText("Set soft memory limits (MB). 0 = disabled.\n"
                + "Processes exceeding the limit can be auto-ended or manually terminated.");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(15));

        Map<Category, TextField> fields = new EnumMap<>(Category.class);
        int row = 0;
        for (Category cat : Category.values()) {
            Label lbl = new Label(cat.getDisplayName() + ":");
            TextField tf = new TextField();
            long bytes = monitor.getCategoryLimit(cat);
            tf.setText(String.valueOf(bytes / (1024 * 1024)));
            tf.setPrefWidth(100);
            fields.put(cat, tf);
            grid.add(lbl, 0, row);
            grid.add(tf, 1, row);
            grid.add(new Label("MB"), 2, row);
            row++;
        }

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                for (Map.Entry<Category, TextField> e : fields.entrySet()) {
                    try {
                        long mb = Long.parseLong(e.getValue().getText().trim());
                        monitor.setCategoryLimit(e.getKey(), mb * 1024 * 1024);
                    } catch (NumberFormatException ignored) {
                    }
                }
                statusLabel.setText("Category limits updated");
            }
            return null;
        });

        dialog.showAndWait();
    }

    public void show() {
        stage.show();
    }
}
