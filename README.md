# RAM Optimizer (Java / JavaFX)

A lightweight desktop RAM monitoring and soft-limiting tool written in Java.

**Primary target: Windows.**  
It also runs on Linux and macOS for monitoring purposes.  
A full Android version is **not practical** on modern Android without root or privileged APIs (see notes below).

## Features

- Live process list sorted by memory usage (Resident Set Size / Working Set)
- Automatic categorization:
  - **System** – critical OS processes (protected by default)
  - **Browser** – Chrome, Edge, Firefox, etc.
  - **Games** – Steam, common game launchers and titles
  - **Utility** – Explorer, terminals, IDEs, etc.
  - **Unknown** – everything else
- Soft memory limits per category (configurable in MB)
- Manual “End process” and “End all over-limit”
- Optional auto-end of processes that exceed their category limit
- Minimal always-on-top window suitable for background use
- Cross-platform system information via [OSHI](https://github.com/oshi/oshi)

## Requirements

- JDK 17 or newer
- Windows recommended for process termination and accurate heuristics

## Build & Run

```bash
cd RamOptimizer
./gradlew run          # or  gradlew.bat run  on Windows
```

Create a runnable fat JAR:

```bash
./gradlew fatJar
java --enable-native-access=ALL-UNNAMED -jar build/libs/RamOptimizer-1.0.0-all.jar
```

(The `--enable-native-access` flag is required on recent JDKs because OSHI uses JNA.)

## How the soft limits work

Limits are **soft**. When a process exceeds its category limit:

- It is highlighted in the status bar
- You can manually terminate it
- If “Auto-end over limit” is checked, non-System processes are terminated automatically

Hard memory capping (Windows Job Objects) is possible with native code but is intentionally omitted for safety and simplicity. Terminating high-memory processes is the practical approach used by most user-facing optimizers.

## Android limitations

Modern Android (7.0+) blocks third-party apps from freely reading `/proc` and from obtaining detailed memory information about other apps’ processes.  
`ActivityManager.getProcessMemoryInfo()` only returns data for processes belonging to the same UID.  

A true system-wide RAM optimizer that can limit or kill arbitrary processes requires root or special privileges (Shizuku / ADB).  
Therefore this project focuses on the desktop (Windows) use-case where such control is still feasible under normal user / elevated permissions.

## Safety notes

- Never auto-kill System processes.
- Ending critical processes can cause instability or data loss.
- Run as a normal user first; elevation may be required for some processes.
- This tool is provided as a learning / utility skeleton. Use at your own risk.

## Project structure

```
src/main/java/com/ramoptimizer/
├── Main.java
├── model/
│   ├── Category.java
│   └── ProcessInfo.java
├── service/
│   ├── Categorizer.java
│   └── ProcessMonitor.java
└── ui/
    └── MainWindow.java
```

## License

MIT (feel free to adapt).
