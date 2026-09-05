package osproject.simulator;

import osproject.core.TraceLogger;

/**
 * Worker thread that increments the shared counter <em>without</em> any
 * synchronisation, deliberately triggering lost-update race conditions.
 *
 * <p>Faithfully mirrors the C++ {@code thread_unsafe(int tid)} function:</p>
 * <pre>
 *   void thread_unsafe(int tid) {
 *       for (int i = 0; i &lt; ITERATIONS; ++i) {
 *           log_event(tid, "READ", shared_counter, shared_counter, "reading counter");
 *           sleep(SPEED_MS);
 *
 *           int local = shared_counter;
 *           sleep(SPEED_MS/2);
 *           local++;
 *           sleep(SPEED_MS/2);
 *
 *           int before = shared_counter;
 *           shared_counter = local;
 *           log_event(tid, "WRITE", before, shared_counter,
 *               before != local - 1 ? "RACE DETECTED" : "ok");
 *           sleep(SPEED_MS);
 *       }
 *       log_event(tid, "DONE", ...);
 *   }
 * </pre>
 *
 * <p>The deliberate sleep <em>between</em> read and write maximises the
 * probability that another thread preempts and overwrites the counter,
 * causing the "RACE DETECTED" note exactly as in the C++ version.</p>
 */
public final class UnsafeWorker implements Runnable {

    private final int         tid;
    private final SharedState state;
    private final TraceLogger logger;
    private final int         iterations;
    private final int         speedMs;

    /**
     * @param tid        thread identifier (0-based)
     * @param state      shared mutable state
     * @param logger     JSON event logger
     * @param iterations number of increment cycles per thread
     * @param speedMs    base sleep duration in milliseconds
     */
    public UnsafeWorker(int tid, SharedState state, TraceLogger logger,
                        int iterations, int speedMs) {
        this.tid        = tid;
        this.state      = state;
        this.logger     = logger;
        this.iterations = iterations;
        this.speedMs    = speedMs;
    }

    @Override
    public void run() {
        for (int i = 0; i < iterations; i++) {
            // ── READ phase ────────────────────────────────────────────────
            logger.log(tid, "READ", state.counter, state.counter, "reading counter");
            sleep(speedMs);

            // ── Simulate the gap that lets races happen ────────────────────
            int local = state.counter;      // C++: int local = shared_counter;
            sleep(speedMs / 2);
            local++;                        // increment local copy
            sleep(speedMs / 2);

            // ── WRITE phase ───────────────────────────────────────────────
            int before = state.counter;
            state.counter = local;          // C++: shared_counter = local;

            String note = (before != local - 1) ? "RACE DETECTED" : "ok";
            logger.log(tid, "WRITE", before, state.counter, note);

            sleep(speedMs);
        }

        logger.log(tid, "DONE", state.counter, state.counter, "thread finished");
    }

    // -----------------------------------------------------------------------

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
