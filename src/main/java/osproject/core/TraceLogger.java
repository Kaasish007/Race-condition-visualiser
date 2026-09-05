package osproject.core;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Thread-safe JSON event logger.
 *
 * <p>Mirrors the C++ {@code log_event()} function and the global
 * {@code log_mutex} / {@code log_file} pair in {@code simulator.cpp}.
 * Every call atomically writes a single-line JSON object both to
 * {@link System#out} and to an optional log file.</p>
 *
 * <p>Usage pattern (try-with-resources):</p>
 * <pre>{@code
 * try (TraceLogger logger = new TraceLogger("trace.log")) {
 *     logger.log(0, "READ", 0, 0, "reading counter");
 * }
 * }</pre>
 */
public final class TraceLogger implements Closeable {

    private final PrintWriter fileWriter;  // may be null if no file requested
    private final Object      lock = new Object();

    /**
     * Opens a logger that appends to {@code logFilePath}.
     * Pass {@code null} to disable file logging.
     *
     * @param logFilePath path to the trace log, or {@code null}
     * @throws IOException if the file cannot be opened for writing
     */
    public TraceLogger(String logFilePath) throws IOException {
        if (logFilePath != null) {
            fileWriter = new PrintWriter(new BufferedWriter(new FileWriter(logFilePath, true)));
        } else {
            fileWriter = null;
        }
    }

    /**
     * Logs a single event, matching the JSON schema of the C++ simulator.
     *
     * @param tid           thread id
     * @param state         state label (READ, WRITE, LOCKED, …)
     * @param counterBefore shared-counter value before the operation
     * @param counterAfter  shared-counter value after  the operation
     * @param note          free-text annotation (empty string for none)
     */
    public void log(int tid, String state, int counterBefore, int counterAfter, String note) {
        long ts = System.nanoTime();
        String json = String.format(
                "{\"ts\":%d,\"tid\":%d,\"state\":\"%s\",\"before\":%d,\"after\":%d,\"note\":\"%s\"}",
                ts, tid, state, counterBefore, counterAfter, note);

        synchronized (lock) {
            System.out.println(json);
            if (fileWriter != null) {
                fileWriter.println(json);
                fileWriter.flush();
            }
        }
    }

    /** Convenience overload with no annotation. */
    public void log(int tid, String state, int counterBefore, int counterAfter) {
        log(tid, state, counterBefore, counterAfter, "");
    }

    /** Flushes and closes the underlying file writer if open. */
    @Override
    public void close() {
        if (fileWriter != null) {
            fileWriter.flush();
            fileWriter.close();
        }
    }
}
