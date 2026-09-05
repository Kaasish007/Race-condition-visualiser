package osproject.simulator;

import osproject.core.TraceLogger;

/**
 * Worker thread that guards the critical section with a
 * {@link java.util.concurrent.Semaphore} (Java's equivalent of POSIX
 * {@code sem_t}).
 *
 * <p>Mirrors C++ {@code thread_semaphore(int tid)}:</p>
 * <pre>
 *   void thread_semaphore(int tid) {
 *       for (int i = 0; i &lt; ITERATIONS; ++i) {
 *           log_event(tid, "WAIT_SEM", ...);
 *
 *           sem_wait(&amp;sem);                     // P()  – acquire permit
 *           log_event(tid, "SEM_ACQUIRED", ...);
 *           sleep(SPEED_MS);
 *
 *           int before = shared_counter++;
 *           log_event(tid, "WRITE", before, shared_counter, "safe write via semaphore");
 *           sleep(SPEED_MS/2);
 *
 *           sem_post(&amp;sem);                     // V()  – release permit
 *           log_event(tid, "SEM_RELEASED", ...);
 *           sleep(SPEED_MS);
 *       }
 *   }
 * </pre>
 *
 * <p>C++ → Java mapping:</p>
 * <table border="1">
 *   <tr><th>C++ (POSIX)</th>           <th>Java</th></tr>
 *   <tr><td>{@code sem_wait(&sem)}</td><td>{@code state.semaphore.acquire()}</td></tr>
 *   <tr><td>{@code sem_post(&sem)}</td><td>{@code state.semaphore.release()}</td></tr>
 * </table>
 *
 * <p>A binary semaphore (initial permits = 1) behaves identically to a mutex
 * for this use-case.  The {@link osproject.educational.EducationalDemo} shows
 * a counting semaphore (permits = 3) for the connection-pool example.</p>
 */
public final class SemaphoreWorker implements Runnable {

    private final int         tid;
    private final SharedState state;
    private final TraceLogger logger;
    private final int         iterations;
    private final int         speedMs;

    public SemaphoreWorker(int tid, SharedState state, TraceLogger logger,
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

            logger.log(tid, "WAIT_SEM", state.counter, state.counter, "waiting on semaphore");

            try {
                state.semaphore.acquire();                 // C++: sem_wait(&sem);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            try {
                logger.log(tid, "SEM_ACQUIRED", state.counter, state.counter, "semaphore acquired");
                sleep(speedMs);

                int before = state.counter;
                state.counter++;                           // C++: shared_counter++;
                logger.log(tid, "WRITE", before, state.counter, "safe write via semaphore");
                sleep(speedMs / 2);

            } finally {
                state.semaphore.release();                 // C++: sem_post(&sem);
                logger.log(tid, "SEM_RELEASED", state.counter, state.counter, "semaphore released");
            }

            sleep(speedMs);
        }

        logger.log(tid, "DONE", state.counter, state.counter, "thread finished");
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
