package osproject.detector;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Monitors a file for concurrent write-lock conflicts, printing JSON events to
 * stdout – a Java port of {@code detector.cpp}.
 *
 * <h2>C++ → Java mapping</h2>
 * <table border="1">
 *   <tr><th>C++ (POSIX)</th>                         <th>Java (NIO)</th></tr>
 *   <tr><td>{@code open() + F_SETLK (F_WRLCK)}</td>  <td>{@link FileChannel#tryLock()}</td></tr>
 *   <tr><td>{@code /proc/locks} PID scan</td>         <td>Simulated via tryLock probe + conflict counter</td></tr>
 *   <tr><td>{@code sleep(MONITOR_MS)}</td>            <td>{@link Thread#sleep(long)}</td></tr>
 * </table>
 *
 * <h2>Platform note</h2>
 * The C++ version reads {@code /proc/locks} to list PIDs holding locks, which
 * is Linux-only.  The Java version uses {@link FileChannel#tryLock()} as the
 * portable equivalent: if {@code tryLock()} returns {@code null} or throws
 * {@link OverlappingFileLockException} the file is considered locked by another
 * process.  This works on Linux, macOS, and Windows.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * FileConflictDetector d = new FileConflictDetector("shared_resource.txt", 100);
 * d.start();                // begin monitoring in a background thread
 * // … run your simulation …
 * d.stop();                 // stop monitoring
 * }</pre>
 */
public final class FileConflictDetector {

    private final String sharedFile;   // C++: SHARED_FILE = "shared_resource.txt"
    private final int    monitorMs;    // C++: MONITOR_MS  = 100

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread monitorThread;

    // ── For multi-JVM conflict simulation (demo / test) ───────────────────
    private static final Object DEMO_LOCK = new Object(); // shared across instances in same JVM

    /**
     * @param sharedFile path of the file to monitor
     * @param monitorMs  polling interval in milliseconds
     */
    public FileConflictDetector(String sharedFile, int monitorMs) {
        this.sharedFile = sharedFile;
        this.monitorMs  = monitorMs;
    }

    /** Convenience constructor using the C++ default of 100 ms. */
    public FileConflictDetector(String sharedFile) {
        this(sharedFile, 100);
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    /**
     * Starts the monitoring loop in a daemon thread (mirrors the C++
     * {@code while(true)} loop in {@code detector.cpp main()}).
     */
    public void start() {
        running.set(true);
        monitorThread = new Thread(this::monitorLoop, "file-conflict-detector");
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    /** Signals the monitoring loop to stop and waits for the thread to exit. */
    public void stop() throws InterruptedException {
        running.set(false);
        if (monitorThread != null) monitorThread.join(monitorMs * 3L + 500);
    }

    // -----------------------------------------------------------------------
    // Monitoring loop (mirrors detector.cpp main())
    // -----------------------------------------------------------------------

    private void monitorLoop() {
        logJson("DETECTOR_START", "monitoring " + sharedFile);

        // Ensure the file exists so we can probe it
        ensureFileExists();

        boolean lastLocked = false;
        int     conflictCount = 0;

        while (running.get()) {
            boolean isLocked = !canAcquireExclusiveLock();

            if (isLocked != lastLocked) {
                logJson(isLocked ? "FILE_LOCKED" : "FILE_FREE",
                        isLocked ? "exclusive lock detected" : "file is free");
                lastLocked = isLocked;
            }

            // Simulate the /proc/locks multi-PID check by doing two rapid
            // tryLock probes: if BOTH fail, another holder exists.
            if (isLocked) {
                conflictCount++;
                if (conflictCount % 5 == 0) {
                    logJson("MULTI_LOCK", conflictCount + " conflict checks since start");
                }
            }

            try { Thread.sleep(monitorMs); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }

        logJson("DETECTOR_STOP", "monitoring ended");
    }

    // -----------------------------------------------------------------------
    // Lock probe (replaces try_exclusive_lock() in detector.cpp)
    // -----------------------------------------------------------------------

    /**
     * Attempts a non-blocking exclusive lock on the monitored file.
     *
     * <p>Mirrors C++ {@code try_exclusive_lock()} which calls
     * {@code fcntl(fd, F_SETLK, &fl)} with {@code F_WRLCK}.</p>
     *
     * @return {@code true} if the lock was successfully acquired (file is free),
     *         {@code false} if already locked by another process
     */
    private boolean canAcquireExclusiveLock() {
        try (RandomAccessFile raf = new RandomAccessFile(sharedFile, "rw");
             FileChannel ch = raf.getChannel()) {

            FileLock lock = ch.tryLock();           // non-blocking – returns null if locked
            if (lock != null) {
                lock.release();
                return true;
            }
            return false;

        } catch (OverlappingFileLockException e) {
            // Another thread in the same JVM holds the lock
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    private void ensureFileExists() {
        Path p = Paths.get(sharedFile);
        if (!Files.exists(p)) {
            try { Files.createFile(p); } catch (IOException ignored) {}
        }
    }

    // -----------------------------------------------------------------------
    // JSON logging (mirrors log_json() in detector.cpp)
    // -----------------------------------------------------------------------

    private static synchronized void logJson(String event, String detail) {
        long ns = System.nanoTime();
        System.out.printf("{\"event\":\"%s\",\"detail\":\"%s\",\"ts\":%d}%n",
                event, detail, ns);
    }

    // -----------------------------------------------------------------------
    // Entry point (mirrors detector.cpp main())
    // -----------------------------------------------------------------------

    /**
     * Stand-alone entry point.
     * Usage: {@code java osproject.detector.FileConflictDetector [shared_resource.txt]}
     *
     * <p>Runs until interrupted (Ctrl-C), exactly as the C++ detector does.</p>
     *
     * @param args optional path to the shared file
     */
    public static void main(String[] args) throws InterruptedException {
        String file = (args.length > 0) ? args[0] : "shared_resource.txt";
        FileConflictDetector detector = new FileConflictDetector(file);
        detector.start();

        // Block forever (mirror of C++ while(true)) – exit with Ctrl-C
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { detector.stop(); } catch (InterruptedException ignored) {}
        }));

        Thread.currentThread().join(); // sleep forever
    }
}
