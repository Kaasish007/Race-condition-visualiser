package osproject.educational;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Interactive, colour-annotated terminal walkthrough of all three
 * synchronisation modes – a Java port of {@code educational.cpp}.
 *
 * <h2>C++ → Java mapping</h2>
 * <table border="1">
 *   <tr><th>C++</th>                <th>Java</th></tr>
 *   <tr><td>ANSI escape macros</td> <td>String constants (RESET, RED, …)</td></tr>
 *   <tr><td>{@code std::mutex}</td>  <td>{@link ReentrantLock}</td></tr>
 *   <tr><td>{@code sem_t}</td>       <td>{@link Semaphore}</td></tr>
 *   <tr><td>{@code sem_wait/post}</td><td>{@code acquire()/release()}</td></tr>
 *   <tr><td>{@code cin.get()}</td>   <td>{@code scanner.nextLine()}</td></tr>
 * </table>
 *
 * <p>Unlike the C++ version which uses raw {@code int} globals, this class
 * uses {@code int[]} single-element arrays as mutable holders so that inner
 * classes (lambdas / anonymous Runnables) can close over them – the standard
 * Java idiom for capturing mutable state in a closure.</p>
 */
public final class EducationalDemo {

    // ── ANSI colour codes (mirrors the C++ #define macros) ────────────────
    private static final String RESET   = "\033[0m";
    private static final String RED     = "\033[1;31m";
    private static final String GREEN   = "\033[1;32m";
    private static final String YELLOW  = "\033[1;33m";
    private static final String BLUE    = "\033[1;34m";
    private static final String MAGENTA = "\033[1;35m";
    private static final String CYAN    = "\033[1;36m";
    private static final String WHITE   = "\033[1;37m";
    private static final String DIM     = "\033[2m";

    private static final Scanner SCANNER = new Scanner(System.in);

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    /**
     * Runs the full interactive demo.
     * Mirrors {@code int main()} in {@code educational.cpp}.
     */
    public static void run() {
        System.out.println(WHITE +
                "\n+----------------------------------------------+" +
                "\n|   Race Condition Visualizer - Education     |" +
                "\n+----------------------------------------------+" + RESET);

        demoUnsafe();
        waitForEnter();

        demoMutex();
        waitForEnter();

        demoSemaphore();
        waitForEnter();

        demoCountingSemaphore();

        separator("Summary");
        System.out.println(
                YELLOW  + "  Unsafe    " + RESET + "-> Fast, but counter gets corrupted by races\n" +
                CYAN    + "  Mutex     " + RESET + "-> One thread at a time; simple, correct\n" +
                MAGENTA + "  Semaphore " + RESET + "-> Like mutex, but can allow N concurrent accesses\n");
    }

    // -----------------------------------------------------------------------
    // Demo 1: No synchronisation (race conditions)
    // -----------------------------------------------------------------------

    /**
     * Demonstrates lost-update race conditions when no synchronisation is used.
     * Mirrors C++ {@code demo_unsafe()}.
     */
    private static void demoUnsafe() {
        separator("DEMO 1: No Synchronization (Race Conditions)");
        System.out.println(YELLOW +
                "  What happens when 4 threads increment a counter 5 times each\n" +
                "  with NO synchronization?\n\n" +
                "  Expected final value: 4 x 5 = 20\n" + RESET);
        pause(500);

        // C++: int unsafe_counter = 0;  → mutable int captured by reference via int[]
        int[] counter = {0};
        int N = 4, iters = 5, delayMs = 50;

        List<Thread> threads = new ArrayList<>();
        System.out.println("\n  " + DIM + "Starting 4 threads..." + RESET);

        for (int i = 0; i < N; i++) {
            final int tid = i;
            threads.add(new Thread(() -> {
                for (int j = 0; j < iters; j++) {
                    int local = counter[0];          // READ  (no lock)
                    pause(delayMs + tid * 5);         // simulate preemption window
                    local++;
                    counter[0] = local;              // WRITE (no lock)
                }
            }, "unsafe-" + i));
        }

        threads.forEach(Thread::start);
        threads.forEach(t -> { try { t.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });

        System.out.println();
        printShared(N * iters, counter[0]);

        if (counter[0] != N * iters) {
            System.out.println(RED +
                    "\n  [!] Race condition occurred!\n" +
                    "      The READ-INCREMENT-WRITE sequence was interrupted.\n" +
                    "      When two threads read the same value and both write back +1,\n" +
                    "      one increment is lost. This is a LOST UPDATE." + RESET);
        }
    }

    // -----------------------------------------------------------------------
    // Demo 2: Mutex (ReentrantLock)
    // -----------------------------------------------------------------------

    /**
     * Demonstrates safe counter increments guarded by a {@link ReentrantLock}.
     * Mirrors C++ {@code demo_mutex()}.
     */
    private static void demoMutex() {
        separator("DEMO 2: Mutex (Mutual Exclusion)");
        System.out.println(CYAN +
                "  ReentrantLock ensures only ONE thread is in the critical section at a time.\n" +
                "  Other threads BLOCK at lock() and wait their turn.\n\n" +
                "  Expected final value: 4 x 5 = 20\n" + RESET);
        pause(500);

        int[]         counter = {0};
        ReentrantLock mtx     = new ReentrantLock();  // C++: std::mutex mtx_demo;
        int N = 4, iters = 5, delayMs = 50;

        List<Thread> threads = new ArrayList<>();
        System.out.println("\n  " + DIM + "Starting 4 threads with mutex protection..." + RESET);

        for (int i = 0; i < N; i++) {
            threads.add(new Thread(() -> {
                for (int j = 0; j < iters; j++) {
                    mtx.lock();                       // C++: mtx_demo.lock();
                    try {
                        int local = counter[0];
                        pause(delayMs);
                        local++;
                        counter[0] = local;
                    } finally {
                        mtx.unlock();                 // C++: mtx_demo.unlock();
                    }
                }
            }));
        }

        threads.forEach(Thread::start);
        threads.forEach(t -> { try { t.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });

        System.out.println();
        printShared(N * iters, counter[0]);
        System.out.println(GREEN + "  [+] No race conditions. Mutex serialized all writes." + RESET);
    }

    // -----------------------------------------------------------------------
    // Demo 3: Binary semaphore
    // -----------------------------------------------------------------------

    /**
     * Demonstrates safe counter increments guarded by a binary
     * {@link Semaphore} (permits = 1).
     * Mirrors C++ {@code demo_semaphore()}.
     */
    private static void demoSemaphore() {
        separator("DEMO 3: Semaphore");
        System.out.println(MAGENTA +
                "  java.util.concurrent.Semaphore (permits=1) works like a mutex here.\n" +
                "  acquire() = P() = decrement and possibly block\n" +
                "  release() = V() = increment and possibly wake a thread\n\n" +
                "  Counting semaphores (permits=N) allow N concurrent accesses.\n\n" +
                "  Expected final value: 4 x 5 = 20\n" + RESET);
        pause(500);

        int[]     counter = {0};
        Semaphore sem     = new Semaphore(1, true);   // C++: sem_init(&sem_demo, 0, 1);
        int N = 4, iters = 5, delayMs = 50;

        List<Thread> threads = new ArrayList<>();
        System.out.println("\n  " + DIM + "Starting 4 threads with semaphore protection..." + RESET);

        for (int i = 0; i < N; i++) {
            threads.add(new Thread(() -> {
                for (int j = 0; j < iters; j++) {
                    try {
                        sem.acquire();                // C++: sem_wait(&sem_demo);
                    } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                    try {
                        int local = counter[0];
                        pause(delayMs);
                        local++;
                        counter[0] = local;
                    } finally {
                        sem.release();                // C++: sem_post(&sem_demo);
                    }
                }
            }));
        }

        threads.forEach(Thread::start);
        threads.forEach(t -> { try { t.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });

        System.out.println();
        printShared(N * iters, counter[0]);
        System.out.println(GREEN + "  [+] No race conditions. Semaphore serialized all writes." + RESET);
    }

    // -----------------------------------------------------------------------
    // Demo 4: Counting semaphore (connection pool)
    // -----------------------------------------------------------------------

    /**
     * Demonstrates a counting semaphore (permits = 3) that models a resource
     * pool – e.g., 3 database connections shared by 8 threads.
     * Mirrors C++ {@code demo_counting_semaphore()}.
     */
    private static void demoCountingSemaphore() {
        separator("DEMO 4: Counting Semaphore (N=3 concurrent)");
        System.out.println(BLUE +
                "  A counting semaphore with permits=3 allows 3 threads simultaneously.\n" +
                "  Models a resource pool — e.g., 3 database connections.\n" + RESET);
        pause(300);

        Semaphore       poolSem      = new Semaphore(3, true);       // C++: sem_init(&pool_sem, 0, 3);
        AtomicInteger   concurrent   = new AtomicInteger(0);
        ReentrantLock   logLock      = new ReentrantLock();

        int N = 8;
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < N; i++) {
            final int tid = i;
            threads.add(new Thread(() -> {
                try { poolSem.acquire(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                try {
                    int c = concurrent.incrementAndGet();
                    logLock.lock();
                    try { System.out.printf("  T%d entered   (concurrent: %d)%n", tid, c); }
                    finally { logLock.unlock(); }

                    pause(80);

                    c = concurrent.decrementAndGet();
                    logLock.lock();
                    try { System.out.printf("  T%d exited    (concurrent: %d)%n", tid, c); }
                    finally { logLock.unlock(); }

                } finally {
                    poolSem.release();                                // C++: sem_post(&pool_sem);
                }
            }, "pool-" + i));
        }

        threads.forEach(Thread::start);
        threads.forEach(t -> { try { t.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });

        System.out.println(GREEN + "\n  [+] Never more than 3 threads accessed simultaneously." + RESET);
    }

    // -----------------------------------------------------------------------
    // Helpers (mirror C++ separator(), pause_for(), print_shared(), cin.get())
    // -----------------------------------------------------------------------

    private static void separator(String title) {
        System.out.println("\n" + DIM + "-".repeat(60) + RESET);
        if (!title.isEmpty()) {
            System.out.println(WHITE + "  " + title + RESET);
            System.out.println(DIM + "-".repeat(60) + RESET);
        }
    }

    private static void pause(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static void printShared(int expected, int actual) {
        boolean ok = actual == expected;
        System.out.printf("  Shared counter : %s%4d%s  (expected %d)%s%n",
                ok ? GREEN : RED,
                actual,
                RESET,
                expected,
                ok ? "" : RED + "  <- CORRUPTED!" + RESET);
    }

    private static void waitForEnter() {
        System.out.print("\n  Press ENTER to continue...");
        SCANNER.nextLine();
    }
}
