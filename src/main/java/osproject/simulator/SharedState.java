package osproject.simulator;

import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Holds all mutable shared state that worker threads compete over.
 *
 * <p>Mirrors the C++ globals:</p>
 * <pre>
 *   int        shared_counter = 0;
 *   mutex      mtx;
 *   sem_t      sem;
 * </pre>
 *
 * <p>The field {@code counter} is intentionally <em>not</em> {@code volatile}
 * or {@code AtomicInteger} so that the {@link UnsafeWorker} can demonstrate
 * real lost-update races, exactly as the C++ unsafe mode does.</p>
 */
public final class SharedState {

    // -----------------------------------------------------------------------
    // Shared counter – exposed as a plain int so UnsafeWorker can race on it
    // -----------------------------------------------------------------------

    /** The shared counter all threads increment. Deliberately non-atomic. */
    public int counter = 0;  // C++: int shared_counter = 0;

    // -----------------------------------------------------------------------
    // Mutex (std::mutex  →  ReentrantLock)
    // -----------------------------------------------------------------------

    /**
     * Mutex that {@link MutexWorker} uses to serialise access.
     * Java's {@link ReentrantLock} maps to C++ {@code std::mutex}:
     * <pre>
     *   lock()   → mtx.lock();
     *   unlock() → mtx.unlock();
     * </pre>
     */
    public final ReentrantLock mutex = new ReentrantLock();

    // -----------------------------------------------------------------------
    // Semaphore (sem_t  →  java.util.concurrent.Semaphore)
    // -----------------------------------------------------------------------

    /**
     * Binary semaphore (one permit) that {@link SemaphoreWorker} uses.
     * Initialised to 1, matching {@code sem_init(&sem, 0, 1)}.
     *
     * <p>C++ mapping:</p>
     * <pre>
     *   sem_wait(&sem)  →  semaphore.acquire();
     *   sem_post(&sem)  →  semaphore.release();
     * </pre>
     */
    public final Semaphore semaphore = new Semaphore(1, true); // fair = FIFO ordering

    // -----------------------------------------------------------------------
    // Factory helpers
    // -----------------------------------------------------------------------

    /** Returns a fresh {@code SharedState} with counter reset to 0. */
    public static SharedState fresh() {
        return new SharedState();
    }
}
