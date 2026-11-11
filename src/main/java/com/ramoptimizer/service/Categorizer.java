package com.ramoptimizer.service;

import com.ramoptimizer.model.Category;

import java.util.Locale;
import java.util.Set;

/**
 * Heuristic process categorizer based on process name, path and command line.
 * Intentionally conservative for System processes.
 */
public final class Categorizer {

    private static final Set<String> BROWSER_NAMES = Set.of(
            "chrome", "chrome.exe", "msedge", "msedge.exe", "firefox", "firefox.exe",
            "brave", "brave.exe", "opera", "opera.exe", "vivaldi", "vivaldi.exe",
            "safari", "chromium", "chromium.exe", "iexplore.exe", "waterfox"
    );

    private static final Set<String> GAME_HINTS = Set.of(
            "steam", "steam.exe", "epicgameslauncher", "origin", "uplay", "battle.net",
            "riotclient", "league of legends", "valorant", "csgo", "cs2", "dota",
            "minecraft", "javaw", "unity", "unreal", "gameoverlay", "easyanticheat",
            "battleye", "fortnite", "gta", "rdr", "cyberpunk", "eldenring"
    );

    private static final Set<String> UTILITY_HINTS = Set.of(
            "explorer", "explorer.exe", "taskmgr", "taskmgr.exe", "cmd", "cmd.exe",
            "powershell", "powershell.exe", "conhost", "conhost.exe", "notepad",
            "notepad.exe", "code", "code.exe", "devenv", "devenv.exe", "idea64",
            "pycharm", "git", "git.exe", "docker", "docker.exe", "wsl", "wsl.exe",
            "terminal", "windowsterminal", "windowsterminal.exe", "searchhost",
            "runtimebroker", "sihost", "fontdrvhost", "dwm", "dwm.exe"
    );

    private static final Set<String> SYSTEM_NAMES = Set.of(
            "system", "idle", "smss", "smss.exe", "csrss", "csrss.exe",
            "wininit", "wininit.exe", "services", "services.exe", "lsass", "lsass.exe",
            "svchost", "svchost.exe", "winlogon", "winlogon.exe", "spoolsv", "spoolsv.exe",
            "ntoskrnl", "registry", "memory compression", "system interrupts",
            "secure system", "fontdrvhost.exe", "dwm.exe", "sihost.exe",
            "runtimebroker.exe", "searchindexer", "searchindexer.exe",
            "audiodg", "audiodg.exe", "wmiprvse", "wmiprvse.exe"
    );

    private Categorizer() {
    }

    public static Category categorize(String name, String path, String commandLine) {
        String n = safeLower(name);
        String p = safeLower(path);
        String c = safeLower(commandLine);

        // 1. Strong system signals
        if (SYSTEM_NAMES.contains(n) || n.startsWith("system") ||
                p.contains("\\windows\\system32\\") || p.contains("/system/") ||
                p.contains("\\windows\\syswow64\\")) {
            return Category.SYSTEM;
        }

        // 2. Browsers
        if (BROWSER_NAMES.contains(n) || n.contains("chrome") || n.contains("firefox") ||
                n.contains("edge") || n.contains("browser") ||
                c.contains("--type=renderer") || c.contains("chrome.exe") ||
                p.contains("google\\chrome") || p.contains("mozilla firefox") ||
                p.contains("microsoft\\edge")) {
            return Category.BROWSER;
        }

        // 3. Games
        for (String hint : GAME_HINTS) {
            if (n.contains(hint) || p.contains(hint) || c.contains(hint)) {
                return Category.GAMES;
            }
        }
        if (n.endsWith(".exe") && (p.contains("\\steam\\") || p.contains("\\epic games\\") ||
                p.contains("\\games\\") || p.contains("/games/"))) {
            return Category.GAMES;
        }

        // 4. Utilities / desktop tools
        for (String hint : UTILITY_HINTS) {
            if (n.contains(hint) || p.contains(hint)) {
                return Category.UTILITY;
            }
        }

        // 5. Fallback
        return Category.UNKNOWN;
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
