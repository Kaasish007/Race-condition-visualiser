package osproject.core;

/**
 * Synchronisation strategy for the {@link osproject.simulator.Simulator}.
 *
 * <p>Maps directly to the C++ {@code MODE} integer argument:
 * <pre>
 *   0  →  UNSAFE    (no synchronisation – race conditions expected)
 *   1  →  MUTEX     (ReentrantLock – one thread at a time)
 *   2  →  SEMAPHORE (java.util.concurrent.Semaphore, permits configurable)
 * </pre>
 * </p>
 */
public enum SyncMode {

    /** No synchronisation – race conditions are intentionally demonstrated. */
    UNSAFE(0, "UNSAFE"),

    /**
     * {@link java.util.concurrent.locks.ReentrantLock} guards the critical
     * section.  Maps to C++ {@code std::mutex}.
     */
    MUTEX(1, "MUTEX"),

    /**
     * {@link java.util.concurrent.Semaphore} with an initial permit count of 1
     * (binary semaphore) guards the critical section.  Maps to POSIX
     * {@code sem_t} with {@code sem_init(&sem, 0, 1)}.
     */
    SEMAPHORE(2, "SEMAPHORE");

    /** Numeric code used on the command line ({@code 0 | 1 | 2}). */
    public final int    code;
    /** Display / JSON label, matching the C++ output. */
    public final String label;

    SyncMode(int code, String label) {
        this.code  = code;
        this.label = label;
    }

    /**
     * Looks up a {@code SyncMode} by its numeric {@code code}.
     *
     * @param code integer 0, 1, or 2
     * @return the matching enum constant
     * @throws IllegalArgumentException if the code is out of range
     */
    public static SyncMode fromCode(int code) {
        for (SyncMode m : values()) {
            if (m.code == code) return m;
        }
        throw new IllegalArgumentException("Unknown mode code: " + code + ". Use 0=UNSAFE 1=MUTEX 2=SEMAPHORE");
    }
}
