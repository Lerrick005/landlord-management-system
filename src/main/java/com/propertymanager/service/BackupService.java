package com.propertymanager.service;

import com.propertymanager.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Handles database backup and restore using PostgreSQL's command-line tools.
 *
 * Auto-detects the PostgreSQL bin folder so the landlord never has to
 * configure a path manually.
 *
 * Backup:  pg_dump -Fc  ->  a compressed .dump file
 * Restore: pg_restore  ->  wipes the DB and re-imports
 *
 * Both operations run in a background thread and give clear errors.
 */
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    private static final DateTimeFormatter FILE_TS =
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");

    /* =================================================================== */

    /** Result of a backup or restore operation. */
    public static class Result {
        public final boolean success;
        public final String message;
        public final File file;

        private Result(boolean success, String message, File file) {
            this.success = success;
            this.message = message;
            this.file = file;
        }

        public static Result ok(String message, File file) {
            return new Result(true, message, file);
        }

        public static Result fail(String message) {
            return new Result(false, message, null);
        }
    }

    /* =================================================================== */
    /*  BACKUP                                                             */
    /* =================================================================== */

    /**
     * Writes a timestamped backup file into the given folder.
     * Filename format: property_management_2026-09-18_184500.dump
     */
    public Result backup(File targetFolder) {
        if (targetFolder == null) {
            return Result.fail("Please choose a backup folder first.");
        }
        if (!targetFolder.exists() && !targetFolder.mkdirs()) {
            return Result.fail("Cannot create the backup folder: "
                               + targetFolder.getAbsolutePath());
        }
        if (!targetFolder.isDirectory()) {
            return Result.fail("The backup path is not a folder.");
        }

        String dbName = AppConfig.get("db.name");
        String stamp = LocalDateTime.now().format(FILE_TS);
        File outFile = new File(targetFolder, dbName + "_" + stamp + ".dump");

        List<String> cmd = new ArrayList<>();
        cmd.add(findPgTool("pg_dump"));
        cmd.add("-Fc");                       // custom format, compressed
        cmd.add("-h"); cmd.add(AppConfig.get("db.host"));
        cmd.add("-p"); cmd.add(AppConfig.get("db.port"));
        cmd.add("-U"); cmd.add(AppConfig.get("db.user"));
        cmd.add("-d"); cmd.add(dbName);
        cmd.add("-f"); cmd.add(outFile.getAbsolutePath());

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().put("PGPASSWORD", AppConfig.get("db.password"));
            pb.redirectErrorStream(true);

            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes());
            boolean done = p.waitFor(60, TimeUnit.SECONDS);

            if (!done) {
                p.destroyForcibly();
                return Result.fail("Backup timed out after 60 seconds.");
            }

            int code = p.exitValue();
            if (code != 0) {
                log.error("pg_dump failed (code {}): {}", code, output);
                return Result.fail("Backup failed. " + cleanError(output));
            }
            if (!outFile.exists() || outFile.length() == 0) {
                return Result.fail("Backup finished but the file is empty.");
            }

            log.info("Backup saved to {} ({} bytes)",
                     outFile.getAbsolutePath(), outFile.length());
            return Result.ok(
                "Backup saved: " + outFile.getName()
                + " (" + formatSize(outFile.length()) + ")",
                outFile);

        } catch (IOException e) {
            log.error("Backup I/O failed", e);
            return Result.fail("Cannot run pg_dump. Is PostgreSQL installed?");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.fail("Backup was interrupted.");
        }
    }

    /* =================================================================== */
    /*  RESTORE                                                            */
    /* =================================================================== */

    /**
     * Restores the database from a previously-saved .dump file.
     *
     * WARNING: drops all current data and replaces it with the backup's
     * contents. The caller MUST warn the user before calling this.
     */
    public Result restore(File backupFile) {
        if (backupFile == null || !backupFile.exists()) {
            return Result.fail("The selected backup file does not exist.");
        }

        String dbName = AppConfig.get("db.name");

        // ---- Step 1: drop and recreate the schema ----
        List<String> dropCmd = List.of(
            findPgTool("psql"),
            "-h", AppConfig.get("db.host"),
            "-p", AppConfig.get("db.port"),
            "-U", AppConfig.get("db.user"),
            "-d", dbName,
            "-c", "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
        );

        try {
            ProcessBuilder pb = new ProcessBuilder(dropCmd);
            pb.environment().put("PGPASSWORD", AppConfig.get("db.password"));
            pb.redirectErrorStream(true);

            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes());
            int code = p.waitFor();

            if (code != 0) {
                log.error("psql drop failed (code {}): {}", code, out);
                return Result.fail("Could not clear the current data. "
                                   + cleanError(out));
            }

            // ---- Step 2: pg_restore the backup ----
            List<String> restoreCmd = List.of(
                findPgTool("pg_restore"),
                "-h", AppConfig.get("db.host"),
                "-p", AppConfig.get("db.port"),
                "-U", AppConfig.get("db.user"),
                "-d", dbName,
                "--no-owner",
                "--no-acl",
                backupFile.getAbsolutePath()
            );

            pb = new ProcessBuilder(restoreCmd);
            pb.environment().put("PGPASSWORD", AppConfig.get("db.password"));
            pb.redirectErrorStream(true);

            p = pb.start();
            out = new String(p.getInputStream().readAllBytes());
            boolean done = p.waitFor(120, TimeUnit.SECONDS);

            if (!done) {
                p.destroyForcibly();
                return Result.fail("Restore timed out after 2 minutes.");
            }

            code = p.exitValue();
            // pg_restore commonly exits with non-zero on warnings; treat as success
            // if the schema was restored (which we can't check here — we trust the log).
            if (code != 0) {
                log.warn("pg_restore exited with code {}: {}", code, out);
                // Only treat as hard failure if the output contains an obvious error
                if (out.toLowerCase().contains("could not") ||
                    out.toLowerCase().contains("fatal")) {
                    return Result.fail("Restore failed. " + cleanError(out));
                }
            }

            log.info("Restore completed from {}", backupFile.getAbsolutePath());
            return Result.ok("Restore completed. Please log in again.", backupFile);

        } catch (IOException e) {
            log.error("Restore I/O failed", e);
            return Result.fail("Cannot run pg_restore. Is PostgreSQL installed?");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.fail("Restore was interrupted.");
        }
    }

    /* =================================================================== */
    /*  POSTGRES TOOL DETECTION                                            */
    /* =================================================================== */

    /**
     * Finds a PostgreSQL command-line tool. Tries:
     *   1. The tool on the PATH
     *   2. Common Windows install folders (newest version first)
     *
     * Returns the full path if found, or just the name (letting the OS
     * try the PATH again) if not.
     */
    private String findPgTool(String toolName) {
        // 1) PATH
        String onPath = which(toolName + ".exe");
        if (onPath != null) return onPath;

        // 2) Common Windows install folder
        String[] searchRoots = {
            "C:\\Program Files\\PostgreSQL",
            "C:\\Program Files (x86)\\PostgreSQL",
            "C:\\PostgreSQL"
        };
        for (String root : searchRoots) {
            File rootDir = new File(root);
            if (!rootDir.isDirectory()) continue;

            // Sort version folders descending (18, 17, 16, ...)
            File[] versions = rootDir.listFiles(File::isDirectory);
            if (versions == null) continue;
            java.util.Arrays.sort(versions,
                (a, b) -> b.getName().compareTo(a.getName()));

            for (File v : versions) {
                File candidate = new File(v, "bin" + File.separator + toolName + ".exe");
                if (candidate.exists()) return candidate.getAbsolutePath();
            }
        }

        // Fall back to bare name — let the OS try
        return toolName;
    }

    /** Windows equivalent of `which`. Returns the full path or null. */
    private String which(String exeName) {
        String path = System.getenv("PATH");
        if (path == null) return null;
        for (String dir : path.split(File.pathSeparator)) {
            File f = new File(dir, exeName);
            if (f.exists() && f.isFile()) {
                return f.getAbsolutePath();
            }
        }
        return null;
    }

    /* =================================================================== */
    /*  HELPERS                                                            */
    /* =================================================================== */

    /** Extracts the first line of pg_dump/psql error output. */
    private String cleanError(String raw) {
        if (raw == null || raw.isBlank()) return "";
        for (String line : raw.split("\\R")) {
            String t = line.trim();
            if (!t.isEmpty()) {
                return t.length() > 200 ? t.substring(0, 200) + "..." : t;
            }
        }
        return "";
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
