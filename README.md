# Java Operating System Simulator

A complete, **pure-Java port** of a C++ Race Condition Visualizer — demonstrating
OS concurrency concepts (unsafe threads, mutex, semaphore) with zero external
runtime dependencies.

## Overview

An OS concepts project built in Java that simulates race conditions in
multithreaded programs and compares unsynchronized, mutex-based, and
semaphore-based execution strategies. It includes a thread-safe JSON trace
logger, an ASCII timeline generator for spotting race conditions at a glance,
and a bundled HTML visualizer (`visualizer.html`) that renders captured
traces in the browser — making it useful as an educational demo for OS
coursework.

---

## Project Structure

```
race-condition-visualizer/
│
├── pom.xml                           Maven build file (JUnit 5 + fat-jar)
├── visualizer.html                   Browser dashboard (works with Java-generated logs)
│
└── src/
    ├── main/java/osproject/
    │   ├── OSProjectDemo.java         ← main()  entry point / CLI router
    │   │
    │   ├── core/
    │   │   ├── TraceEvent.java        record  – immutable event data holder
    │   │   ├── SyncMode.java          enum    – UNSAFE / MUTEX / SEMAPHORE
    │   │   └── TraceLogger.java       thread-safe JSON logger → stdout + file
    │   │
    │   ├── simulator/
    │   │   ├── SharedState.java       shared counter + ReentrantLock + Semaphore
    │   │   ├── UnsafeWorker.java      Runnable: no sync  (races intended)
    │   │   ├── MutexWorker.java       Runnable: ReentrantLock critical section
    │   │   ├── SemaphoreWorker.java   Runnable: Semaphore.acquire / release
    │   │   └── Simulator.java         Builder + orchestrator
    │   │
    │   ├── tracer/
    │   │   └── Tracer.java            log parser → ASCII timeline + race report
    │   │
    │   ├── detector/
    │   │   └── FileConflictDetector   FileChannel.tryLock() monitoring loop
    │   │
    │   └── educational/
    │       └── EducationalDemo.java   ANSI colour interactive walkthrough
    │
    └── test/java/osproject/
        └── SimulatorTest.java         JUnit 5 – 12 test cases
```

---

## C++ → Java Concept Map

| OS Concept | C++ | Java |
|---|---|---|
| Threads | `std::thread` | `java.lang.Thread` |
| Mutex | `std::mutex` + `lock()/unlock()` | `ReentrantLock.lock()/unlock()` |
| Semaphore | `sem_t`, `sem_wait/post` | `Semaphore.acquire()/release()` |
| File lock | `fcntl(F_SETLK, F_WRLCK)` | `FileChannel.tryLock()` |
| Shared state | global `int shared_counter` | `int` field in `SharedState` |
| JSON logging | hand-rolled `printf` | `String.format` + `PrintWriter` |
| ANSI colours | `#define RED "\033[1;31m"` | `static final String` constants |

---

## Build & Run

### Prerequisites
- Java 17+ JDK
- Maven 3.6+ (for tests + fat-jar)

### Build
```bash
mvn clean package          # compiles, runs tests, creates fat-jar
```

### Run all modes (non-interactive)
```bash
java -jar target/race-condition-visualizer-1.0.0.jar demo
```

### Individual commands

```bash
# UNSAFE mode — race conditions expected
java -jar target/*.jar simulate 4 300 0

# MUTEX mode — always correct
java -jar target/*.jar simulate 4 300 1

# SEMAPHORE mode — always correct
java -jar target/*.jar simulate 4 300 2

# Parse a trace log → ASCII timeline + report
java -jar target/*.jar trace trace.log

# Interactive educational demo (ANSI colour terminal)
java -jar target/*.jar edu

# File-lock conflict detector (runs until Ctrl-C)
java -jar target/*.jar detect shared_resource.txt
```

### Browser Visualiser
Open `visualizer.html` in any browser — it reads `trace.log` directly and
renders an interactive timeline. The Java simulator emits **100% identical JSON**
to the C++ version so no changes to the HTML are required.

---

## OS Concepts Demonstrated

### Race Condition (UNSAFE mode)
```
T0: reads counter=5
T1: reads counter=5    ← both read the same value
T0: writes 6
T1: writes 6           ← T0's increment is LOST
Expected: 7, Got: 6    ← data corruption
```

### Mutex (MUTEX mode)
```
T0: waits for lock (WAIT_LOCK)
T0: acquires lock      (LOCKED)
T0: reads 5, writes 6  (safe write)
T0: releases lock      (UNLOCKED)
T1: acquires lock  ←   only after T0 releases; guaranteed correct
```

### Semaphore (SEMAPHORE mode)
```
sem_wait / acquire()  →  P()  decrement; block if 0
  [critical section]
sem_post / release()  →  V()  increment; wake a waiter

Binary   (permits=1)  behaves like a mutex
Counting (permits=N)  allows N concurrent accesses — models a pool
```

### ASCII Timeline Legend
```
R = Read          W = Write          X = Race condition!
L = Lock acquired U = Lock released
S = Semaphore acquired               s = Semaphore released
D = Thread done   - = Idle
```

---

## Sample Output

### UNSAFE (4 threads × 5 iterations)
```
{"event":"START","mode":"UNSAFE","threads":4,"speed":300,"iterations":5}
{"ts":...,"tid":0,"state":"READ","before":0,"after":0,"note":"reading counter"}
{"ts":...,"tid":1,"state":"READ","before":0,"after":0,"note":"reading counter"}
{"ts":...,"tid":0,"state":"WRITE","before":1,"after":1,"note":"RACE DETECTED"}
...
{"event":"END","final_counter":8,"expected":20,"races":true}
```

### ASCII Timeline (UNSAFE)
```
=== ASCII TIMELINE ===
      T0 | R------X---R-------X---R-------W---R-------X---R-------W--D
      T1 | R------X---R-------X---R-------X---R-------W---R-------X---D
      T2 | R------X---R-------W---R-------X---R-------X---R-------X--D
      T3 | R------X---R-------X---R-------X---R-------X---R-------X--D

=== REPORT ===
Threads seen  : 4    Total events : 44
Total writes  : 20   Total reads  : 20
Race events   : 15   Final counter: 8

[!] UNSAFE: Race conditions detected!
```

### ASCII Timeline (MUTEX)
```
      T0 | .--------LWU-.------L-WU.------L-WU-.-----L-WU-.------L-WU-D
      T1 | .-----L-WU.------L-WU-.-----L-WU-.------LWU-.------L-WU-D
      T2 | .--L-WU-.-----L-WU-.-----L-WU-.------L-WU.------L-WU-D
      T3 | L-WU-.-----L-WU-.------LWU-.------L-WU-.-----L-WU-D

[+] SAFE: No race conditions detected.
```

---

## Running Tests

```bash
mvn test
```

Tests cover:
- `TraceEvent` record semantics & JSON round-trip
- `SyncMode` enum lookup & error handling
- `Tracer.parseLine()` with valid + race-annotated lines
- `SharedState` initial state
- `Simulator` — MUTEX and SEMAPHORE always produce correct counts
- `Simulator` — UNSAFE mode produces parseable RACE events
- `Simulator.Builder` parameter clamping
- `TraceLogger` writes valid JSON lines
