package osproject;

import osproject.core.SyncMode;
import osproject.detector.FileConflictDetector;
import osproject.educational.EducationalDemo;
import osproject.simulator.Simulator;
import osproject.tracer.Tracer;

import java.io.IOException;
import java.util.List;

/**
 * Main entry point for the Java Operating System Simulator.
 *
 * <p>Routes to the four sub-commands that map to the four C++ binaries:</p>
 *
 * <table border="1">
 *   <tr><th>C++ binary</th>    <th>Java sub-command</th></tr>
 *   <tr><td>simulator</td>     <td>{@code simulate [threads] [speed_ms] [mode]}</td></tr>
 *   <tr><td>tracer</td>        <td>{@code trace    [trace.log]}</td></tr>
 *   <tr><td>detector</td>      <td>{@code detect   [shared_resource.txt]}</td></tr>
 *   <tr><td>educational</td>   <td>{@code edu}</td></tr>
 * </table>
 *
 * <h2>Quick-start examples (matches README.md)</h2>
 * <pre>
 *   # Unsafe mode (equivalent to ./simulator 4 300 0)
 *   java -jar race-condition-visualizer-1.0.0.jar simulate 4 300 0
 *
 *   # Mutex mode
 *   java -jar race-condition-visualizer-1.0.0.jar simulate 4 300 1
 *
 *   # Semaphore mode
 *   java -jar race-condition-visualizer-1.0.0.jar simulate 4 300 2
 *
 *   # Parse trace log (equivalent to ./tracer trace.log)
 *   java -jar race-condition-visualizer-1.0.0.jar trace trace.log
 *
 *   # Educational demo (equivalent to ./educational)
 *   java -jar race-condition-visualizer-1.0.0.jar edu
 *
 *   # File-lock detector (equivalent to ./detector shared_resource.txt)
 *   java -jar race-condition-visualizer-1.0.0.jar detect shared_resource.txt
 *
 *   # Run ALL demos non-interactively (good for CI / graders)
 *   java -jar race-condition-visualizer-1.0.0.jar demo
 * </pre>
 */
public final class OSProjectDemo {

    // ── ANSI colour helpers ────────────────────────────────────────────────
    private static final String CYAN  = "\033[1;36m";
    private static final String WHITE = "\033[1;37m";
    private static final String RESET = "\033[0m";

    private OSProjectDemo() {}   // utility class – not instantiated

    // -----------------------------------------------------------------------

    /**
     * Application entry point.
     *
     * @param args sub-command followed by optional arguments
     */
    public static void main(String[] args) throws IOException, InterruptedException {

        String cmd = (args.length > 0) ? args[0].toLowerCase() : "demo";

        switch (cmd) {

            // ── simulate [threads] [speed_ms] [mode] ──────────────────────
            case "simulate", "sim" -> {
                String[] rest = tail(args);
                new Simulator.Builder().fromArgs(rest).build().run();
            }

            // ── trace [logfile] ────────────────────────────────────────────
            case "trace" -> {
                String logFile = args.length > 1 ? args[1] : "trace.log";
                var events = Tracer.parseFile(logFile);
                if (events.isEmpty()) { System.out.println("No events found in " + logFile); return; }
                Tracer.printAsciiTimeline(events);
                Tracer.printReport(events);
            }

            // ── detect [file] ──────────────────────────────────────────────
            case "detect" -> {
                String file = args.length > 1 ? args[1] : "shared_resource.txt";
                FileConflictDetector.main(new String[]{file});
            }

            // ── edu ─────────────────────────────────────────────────────────
            case "edu", "educational" -> EducationalDemo.run();

            // ── demo  (non-interactive CI/showcase run) ────────────────────
            case "demo" -> runFullDemo();

            // ── help ───────────────────────────────────────────────────────
            default -> printHelp();
        }
    }

    // -----------------------------------------------------------------------
    // Full non-interactive demo (used when no args supplied)
    // -----------------------------------------------------------------------

    /**
     * Runs all three simulation modes back-to-back and then traces the log.
     * Useful for graders / CI pipelines that cannot interact with stdin.
     */
    private static void runFullDemo() throws IOException, InterruptedException {
        banner("Java OS Simulator – Full Demo");

        for (SyncMode mode : SyncMode.values()) {
            System.out.println(CYAN + "\n── Mode: " + mode.label + " ──" + RESET);
            int finalCount = new Simulator.Builder()
                    .numThreads(3)
                    .speedMs(50)        // fast for demo
                    .iterations(3)
                    .mode(mode)
                    .logFile("trace.log")
                    .build()
                    .run();

            int expected = 3 * 3;
            System.out.printf("  Final counter: %d  (expected %d)  %s%n",
                    finalCount, expected,
                    finalCount == expected ? "✓ CORRECT" : "✗ RACE OCCURRED");
        }

        System.out.println(CYAN + "\n── Tracing last log ──" + RESET);
        try {
            var events = Tracer.parseFile("trace.log");
            Tracer.printAsciiTimeline(events);
            Tracer.printReport(events);
        } catch (IOException e) {
            System.out.println("(trace.log not found – run simulate first)");
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static void banner(String title) {
        System.out.println(WHITE +
                "\n╔══════════════════════════════════════════════╗" +
                "\n║  " + String.format("%-44s", title) + "║" +
                "\n╚══════════════════════════════════════════════╝" + RESET);
    }

    private static void printHelp() {
        System.out.println("""
                Usage:  java -jar race-condition-visualizer.jar <command> [options]

                Commands:
                  simulate [threads] [speed_ms] [mode]   Run thread simulation
                                                         mode: 0=UNSAFE 1=MUTEX 2=SEMAPHORE
                  trace    [logfile]                      Parse trace.log → ASCII timeline
                  detect   [file]                         Monitor file for lock conflicts
                  edu                                     Interactive educational demo
                  demo                                    Non-interactive showcase of all modes
                  help                                    Print this message

                Examples:
                  java -jar *.jar simulate 4 300 0        # unsafe (races expected)
                  java -jar *.jar simulate 4 300 1        # mutex
                  java -jar *.jar simulate 4 300 2        # semaphore
                  java -jar *.jar trace trace.log
                  java -jar *.jar edu
                """);
    }

    /** Returns all but the first element of an array (safe for empty arrays). */
    private static String[] tail(String[] args) {
        if (args.length <= 1) return new String[0];
        String[] t = new String[args.length - 1];
        System.arraycopy(args, 1, t, 0, t.length);
        return t;
    }
}
