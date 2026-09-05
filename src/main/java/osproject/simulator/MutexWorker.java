package osproject.simulator;

import osproject.core.TraceLogger;

/**
 * Worker thread that guards the critical section with a
 * {@link java.util.concurrent.locks.ReentrantLock} (Java's equivalent of
 * C++ {@code std::mutex}).
 *
 * <p>Mirrors C++ {@code thread_mutex(int tid)}:</p>
 * <pre>
 *   void thread_mutex(int tid) {
 *       for (int i = 0; i &lt; ITERATIONS; ++i) {
 *           log_event(tid, "WAIT_LOCK", ...);
 *           sleep(SPEED_MS/4);
 *
 *           mtx.lock();
 *           log_event(tid, "LOCKED", ...);
 *           sleep(SPEED_MS);
 *
 *           int before = shared_counter++;
 *           log_event(tid, "WRITE", before, shared_counter, "safe write");
 *           sleep(SPEED_MS/2);
 *
 *           mtx.unlock();
 *           log_event(tid, "UNLOCKED", ...);
 *           sleep(SPEED_MS);
 *       }
 *   }
 * </pre>
 *
 * <p>C++ → Java mapping:</p>
 * <table border="1">
 *   <tr><th>C++</th><th>Java</th></tr>
 *   <tr><td>{@code mtx.lock()}</td>  <td>{@code state.mutex.lock()}</td></tr>
 *   <tr><td>{@code mtx.unlock()}</td><td>{@code state.mutex.unlock()}</td></tr>
 * </table>
 */
public final class MutexWorker implements Runnable {

    private final int         tid;
    private final SharedState state;
    private final TraceLogger logger;
    private final int         iterations;
    private final int         speedMs;

    public MutexWorker(int tid, SharedState state, TraceLogger logger,
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

            // ── Announce intent to acquire the lock ───────────────────────
            logger.log(tid, "WAIT_LOCK", state.counter, state.counter, "waiting for mutex");
            sleep(speedMs / 4);

            // ── Acquire (blocks until the mutex is free) ──────────────────
            state.mutex.lock();                       // C++: mtx.lock();
            try {
                logger.log(tid, "LOCKED", state.counter, state.counter, "acquired mutex");
                sleep(speedMs);

                int before = state.counter;
                state.counter++;                      // C++: shared_counter++;
                logger.log(tid, "WRITE", before, state.counter, "safe write");
                sleep(speedMs / 2);

            } finally {
                state.mutex.unlock();                 // C++: mtx.unlock();
                logger.log(tid, "UNLOCKED", state.counter, state.counter, "released mutex");
            }

            sleep(speedMs);
        }

        logger.log(tid, "DONE", state.counter, state.counter, "thread finished");
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
