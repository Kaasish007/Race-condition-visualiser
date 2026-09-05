package osproject.tracer;

import osproject.core.TraceEvent;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a {@code trace.log} file and produces an ASCII timeline and a
 * race-condition report – a direct Java port of {@code tracer.cpp}.
 *
 * <h2>Design notes</h2>
 * <ul>
 *   <li>C++ used hand-rolled {@code extract_field()} string scanning;
 *       this class uses {@link Pattern} / {@link Matcher} instead.</li>
 *   <li>All I/O is done with {@code java.io} / {@code java.nio}; no
 *       external dependencies.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * List<TraceEvent> events = Tracer.parseFile("trace.log");
 * Tracer.printAsciiTimeline(events);
 * Tracer.printReport(events);
 * }</pre>
 */
public final class Tracer {

    /** Width (columns) of the ASCII timeline bar – matches C++ {@code WIDTH = 60}. */
    private static final int TIMELINE_WIDTH = 60;

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Reads every JSON line from {@code logFilePath} and returns a list of
     * parsed {@link TraceEvent} objects in file order.
     *
     * @param logFilePath path to the trace log
     * @return ordered list of events (never null; may be empty)
     * @throws IOException if the file cannot be read
     */
    public static List<TraceEvent> parseFile(String logFilePath) throws IOException {
        List<TraceEvent> events = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(logFilePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.charAt(0) != '{') continue;
                try {
                    events.add(parseLine(line));
                } catch (Exception ignored) {
                    // skip malformed lines exactly as the C++ try{} catch{} does
                }
            }
        }
        return events;
    }

    /**
     * Prints an ASCII timeline to {@link System#out}, one row per thread.
     *
     * <p>Mirrors C++ {@code print_ascii_timeline()}.</p>
     *
     * @param events    ordered list of events (from {@link #parseFile})
     * @param maxThreads maximum thread id + 1; auto-detected when ≤ 0
     */
    public static void printAsciiTimeline(List<TraceEvent> events, int maxThreads) {
        if (events.isEmpty()) {
            System.out.println("No events to display.");
            return;
        }

        if (maxThreads <= 0) {
            maxThreads = events.stream().mapToInt(TraceEvent::tid).max().orElse(0) + 1;
        }

        long t0   = events.get(0).ts();
        long tmax = events.get(events.size() - 1).ts() - t0;
        if (tmax == 0) tmax = 1;

        System.out.println("\n=== ASCII TIMELINE ===");
        System.out.printf("%-10s | %-" + TIMELINE_WIDTH + "s | Events%n", "Thread", "Timeline");
        System.out.println("-".repeat(10 + 3 + TIMELINE_WIDTH + 3 + 20));

        for (int tid = 0; tid < maxThreads; tid++) {
            char[] bar = new char[TIMELINE_WIDTH];
            java.util.Arrays.fill(bar, '-');

            int writes = 0, races = 0;

            for (TraceEvent e : events) {
                if (e.tid() != tid) continue;

                long rel = e.ts() - t0;
                int pos  = (int) ((double) rel / tmax * (TIMELINE_WIDTH - 1));
                pos = Math.max(0, Math.min(pos, TIMELINE_WIDTH - 1));

                char sym = symbolFor(e);
                bar[pos] = sym;

                if ("WRITE".equals(e.state())) writes++;
                if (e.isRace())                races++;
            }

            System.out.printf("%8s | %s | W:%d R:%d%n",
                    "T" + tid, new String(bar), writes, races);
        }

        System.out.println("\nLegend: R=Read W=Write X=Race L=Lock U=Unlock S=Sem_Acq s=Sem_Rel D=Done");
        System.out.println();
    }

    /**
     * Overload that auto-detects the thread count from the event list.
     *
     * @param events ordered list of events
     */
    public static void printAsciiTimeline(List<TraceEvent> events) {
        printAsciiTimeline(events, -1);
    }

    /**
     * Prints a plain-text summary report to {@link System#out}.
     *
     * <p>Mirrors C++ {@code print_report()}.</p>
     *
     * @param events ordered list of events
     */
    public static void printReport(List<TraceEvent> events) {
        int races = 0, writes = 0, reads = 0;
        Set<Integer> tids = new TreeSet<>();

        for (TraceEvent e : events) {
            tids.add(e.tid());
            if ("WRITE".equals(e.state())) writes++;
            if ("READ" .equals(e.state())) reads++;
            if (e.isRace())                races++;
        }

        System.out.println("=== REPORT ===");
        System.out.println("Threads seen  : " + tids.size());
        System.out.println("Total events  : " + events.size());
        System.out.println("Total writes  : " + writes);
        System.out.println("Total reads   : " + reads);
        System.out.println("Race events   : " + races);

        if (!events.isEmpty()) {
            System.out.println("Final counter : " + events.get(events.size() - 1).after());
        }

        if (races > 0) {
            System.out.println("\n[!] UNSAFE: Race conditions detected!");
        } else {
            System.out.println("\n[+] SAFE: No race conditions detected.");
        }
    }

    // -----------------------------------------------------------------------
    // Entry point (mirrors tracer.cpp main())
    // -----------------------------------------------------------------------

    /**
     * Stand-alone entry point.
     * Usage: {@code java osproject.tracer.Tracer [trace.log]}
     *
     * @param args optional path to the trace log (default: {@code trace.log})
     */
    public static void main(String[] args) {
        String logFile = (args.length > 0) ? args[0] : "trace.log";
        try {
            List<TraceEvent> events = parseFile(logFile);
            if (events.isEmpty()) {
                System.out.println("No events found in " + logFile);
                return;
            }
            printAsciiTimeline(events);
            printReport(events);
        } catch (IOException e) {
            System.err.println("Cannot open " + logFile + ": " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Maps a {@link TraceEvent#state()} string to the single ASCII character
     * used in the timeline bar, exactly matching the C++ symbol table.
     */
    private static char symbolFor(TraceEvent e) {
        return switch (e.state()) {
            case "WRITE"        -> e.isRace() ? 'X' : 'W';
            case "READ"         -> 'R';
            case "LOCKED"       -> 'L';
            case "UNLOCKED"     -> 'U';
            case "SEM_ACQUIRED" -> 'S';
            case "SEM_RELEASED" -> 's';
            case "DONE"         -> 'D';
            default             -> '.';
        };
    }

    // -----------------------------------------------------------------------
    // JSON parsing (replaces C++ extract_field() + parse_event())
    // -----------------------------------------------------------------------

    // Pre-compiled patterns for each field (avoids regex recompilation per line)
    private static final Pattern TS_PAT     = Pattern.compile("\"ts\":(\\d+)");
    private static final Pattern TID_PAT    = Pattern.compile("\"tid\":(\\d+)");
    private static final Pattern STATE_PAT  = Pattern.compile("\"state\":\"([^\"]+)\"");
    private static final Pattern BEFORE_PAT = Pattern.compile("\"before\":(-?\\d+)");
    private static final Pattern AFTER_PAT  = Pattern.compile("\"after\":(-?\\d+)");
    private static final Pattern NOTE_PAT   = Pattern.compile("\"note\":\"([^\"]*)\"");

    /**
     * Parses one JSON event line into a {@link TraceEvent}.
     *
     * <p>C++ used a hand-rolled {@code extract_field()} scanner; here we use
     * pre-compiled {@link Pattern} objects for clarity and correctness.</p>
     *
     * @param line a single JSON object line from the trace log
     * @return the parsed event
     * @throws IllegalArgumentException if required fields are missing
     */
    public static TraceEvent parseLine(String line) {
        long   ts     = extractLong  (TS_PAT,     line, "ts");
        int    tid    = (int) extractLong(TID_PAT, line, "tid");
        String state  = extractString(STATE_PAT,  line, "state");
        int    before = (int) extractLong(BEFORE_PAT, line, "before");
        int    after  = (int) extractLong(AFTER_PAT,  line, "after");
        String note   = extractOptionalString(NOTE_PAT, line);
        return new TraceEvent(ts, tid, state, before, after, note);
    }

    private static long extractLong(Pattern p, String line, String fieldName) {
        Matcher m = p.matcher(line);
        if (!m.find()) throw new IllegalArgumentException("Missing field: " + fieldName);
        return Long.parseLong(m.group(1));
    }

    private static String extractString(Pattern p, String line, String fieldName) {
        Matcher m = p.matcher(line);
        if (!m.find()) throw new IllegalArgumentException("Missing field: " + fieldName);
        return m.group(1);
    }

    private static String extractOptionalString(Pattern p, String line) {
        Matcher m = p.matcher(line);
        return m.find() ? m.group(1) : "";
    }
}
