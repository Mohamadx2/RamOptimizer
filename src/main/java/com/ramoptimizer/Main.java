package com.ramoptimizer;

import com.ramoptimizer.service.ProcessMonitor;
import com.ramoptimizer.ui.MainWindow;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

/**
 * Entry point for the RAM Optimizer.
 *
 * Primary platform: Windows (process termination & heuristics tuned for it).
 * Also runs on Linux/macOS for monitoring; termination behavior depends on OS permissions.
 *
 * Android is intentionally not included: modern Android restricts third-party apps
 * from enumerating or controlling other processes' memory without root / special privileges.
 */
public class Main extends Application {

    private ProcessMonitor monitor;

    @Override
    public void start(Stage primaryStage) {
        monitor = new ProcessMonitor();
        MainWindow window = new MainWindow(monitor, primaryStage);
        window.show();
    }

    @Override
    public void stop() {
        if (monitor != null) {
            monitor.stop();
        }
    }

    public static void main(String[] args) {
        // Ensure JavaFX toolkit starts cleanly
        Platform.setImplicitExit(true);
        launch(args);
    }
}
