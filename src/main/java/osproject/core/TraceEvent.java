package osproject.core;

/**
 * Immutable data holder for a single simulation event parsed from a JSON
 * trace line.  Mirrors the C++ {@code Event} struct in {@code tracer.cpp}.
 *
 * <p>Uses a Java 17 {@code record} so all boilerplate (constructor,
 * accessors, equals/hashCode/toString) is generated automatically.</p>
 *
 * <p>JSON shape (one object per line):
 * <pre>
 * {"ts":1773664026842321415,"tid":0,"state":"READ","before":0,"after":0,"note":"reading counter"}
 * </pre>
 * </p>
 */
public record TraceEvent(
        long   ts,      // nanosecond wall-clock timestamp
        int    tid,     // thread id  (0-based)
        String state,   // READ | WRITE | LOCKED | UNLOCKED | WAIT_LOCK |
                        // WAIT_SEM | SEM_ACQUIRED | SEM_RELEASED | DONE
        int    before,  // shared-counter value before the operation
        int    after,   // shared-counter value after  the operation
        String note     // free-text annotation (e.g. "RACE DETECTED")
) {

    /**
     * Returns {@code true} when the event's {@code note} field contains the
     * string {@code "RACE"}, matching the C++ simulator's convention.
     */
    public boolean isRace() {
        return note != null && note.contains("RACE");
    }

    /**
     * Serialises this event back to the same single-line JSON format emitted
     * by the C++ simulator so that {@link osproject.tracer.Tracer} and the
     * HTML visualiser can consume Java-generated log files without change.
     */
    public String toJson() {
        return String.format(
                "{\"ts\":%d,\"tid\":%d,\"state\":\"%s\",\"before\":%d,\"after\":%d,\"note\":\"%s\"}",
                ts, tid, state, before, after, note == null ? "" : note);
    }
}
