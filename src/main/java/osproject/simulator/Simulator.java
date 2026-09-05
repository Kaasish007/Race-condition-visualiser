package osproject.simulator;

import osproject.core.SyncMode;
import osproject.core.TraceLogger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Core simulation engine.
 *
 * <p>Mirrors the {@code main()} function of {@code simulator.cpp}, including:
 * <ul>
 *   <li>Clamping input parameters to safe ranges.</li>
 *   <li>Emitting a START JSON banner.</li>
 *   <li>Spawning worker threads in the chosen mode.</li>
 *   <li>Joining all threads (C++ {@code t.join()}).</li>
 *   <li>Emitting an END JSON summary with race-detection flag.</li>
 * </ul>
 * </p>
 *
 * <p>C++ → Java thread mapping:</p>
 * <pre>
 *   vector&lt;thread&gt; threads;
 *   threads.emplace_back(thread_unsafe, i);   →  new Thread(new UnsafeWorker(...))
 *   for (auto&amp; t : threads) t.join();         →  thread.join()
 * </pre>
 */
public final class Simulator {

    // -----------------------------------------------------------------------
    // Configuration (defaults mirror the C++ global variables)
    // -----------------------------------------------------------------------

    private final int      numThreads;   // C++: NUM_THREADS = 4
    private final int      speedMs;      // C++: SPEED_MS    = 300
    private final int      iterations;   // C++: ITERATIONS  = 5
    private final SyncMode mode;         // C++: MODE        = 0
    private final String   logFile;      // C++: LOG_FILE    = "trace.log"

    private Simulator(Builder b) {
        this.numThreads = b.numThreads;
        this.speedMs    = b.speedMs;
        this.iterations = b.iterations;
        this.mode       = b.mode;
        this.logFile    = b.logFile;
    }

    // -----------------------------------------------------------------------
    // Public entry point
    // -----------------------------------------------------------------------

    /**
     * Runs the simulation to completion and returns the final counter value.
     *
     * @return the value of the shared counter after all threads have finished
     * @throws IOException          if the log file cannot be opened
     * @throws InterruptedException if the calling thread is interrupted while
     *                              joining worker threads
     */
    public int run() throws IOException, InterruptedException {
        SharedState sharedState = SharedState.fresh();

        try (TraceLogger logger = new TraceLogger(logFile)) {

            // ── START banner ──────────────────────────────────────────────
            System.out.printf("{\"event\":\"START\",\"mode\":\"%s\",\"threads\":%d,\"speed\":%d,\"iterations\":%d}%n",
                    mode.label, numThreads, speedMs, iterations);

            // ── Spawn threads ─────────────────────────────────────────────
            List<Thread> threads = new ArrayList<>(numThreads);

            for (int i = 0; i < numThreads; i++) {
                Runnable worker = switch (mode) {
                    case UNSAFE    -> new UnsafeWorker   (i, sharedState, logger, iterations, speedMs);
                    case MUTEX     -> new MutexWorker    (i, sharedState, logger, iterations, speedMs);
                    case SEMAPHORE -> new SemaphoreWorker(i, sharedState, logger, iterations, speedMs);
                };
                Thread t = new Thread(worker, "sim-thread-" + i);
                threads.add(t);
                t.start();
            }

            // ── Join (C++: for (auto& t : threads) t.join()) ─────────────
            for (Thread t : threads) t.join();

        } // logger closed here (flush + close the file)

        // ── END banner ────────────────────────────────────────────────────
        int expected = numThreads * iterations;
        boolean raced = sharedState.counter != expected;
        System.out.printf("{\"event\":\"END\",\"final_counter\":%d,\"expected\":%d,\"races\":%s}%n",
                sharedState.counter, expected, raced);

        return sharedState.counter;
    }

    // -----------------------------------------------------------------------
    // Builder (fluent API replacing C++ argc/argv parsing)
    // -----------------------------------------------------------------------

    /** Fluent builder for {@link Simulator}. */
    public static final class Builder {

        // defaults matching C++ globals
        private int      numThreads = 4;
        private int      speedMs    = 300;
        private int      iterations = 5;
        private SyncMode mode       = SyncMode.UNSAFE;
        private String   logFile    = "trace.log";

        /**
         * Parses a C++-style {@code argv} array:
         * {@code [threads] [speed_ms] [mode_code]}.
         */
        public Builder fromArgs(String[] args) {
            if (args.length > 0) numThreads = Integer.parseInt(args[0]);
            if (args.length > 1) speedMs    = Integer.parseInt(args[1]);
            if (args.length > 2) mode       = SyncMode.fromCode(Integer.parseInt(args[2]));
            return this;
        }

        public Builder numThreads(int v) { numThreads = v; return this; }
        public Builder speedMs(int v)    { speedMs    = v; return this; }
        public Builder iterations(int v) { iterations = v; return this; }
        public Builder mode(SyncMode m)  { mode       = m; return this; }
        public Builder logFile(String f) { logFile    = f; return this; }

        /** Clamps parameters to safe ranges, exactly as the C++ {@code main()} does. */
        public Simulator build() {
            numThreads = Math.max(1, Math.min(numThreads, 10));   // C++: max(1, min(n, 10))
            speedMs    = Math.max(10, Math.min(speedMs, 2000));    // C++: max(10, min(s, 2000))
            iterations = Math.max(1, iterations);
            return new Simulator(this);
        }
    }
}
