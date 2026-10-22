# LedgerMate Debug Diagnostics & Performance Profiling Guide

This diagnostic guide outlines the performance tooling enabled in **LedgerMate** debug builds (`BuildConfig.DEBUG = true`) to detect main-thread disk I/O, frame drops, recomposition hotspots, and rendering bottlenecks.

---

## 1. StrictMode (Main-Thread Violations)

In `LedgerApplication.kt`, StrictMode is configured on app startup in Debug mode (`BuildConfig.DEBUG = true`):

```kotlin
if (BuildConfig.DEBUG) {
    StrictMode.setThreadPolicy(
        StrictMode.ThreadPolicy.Builder()
            .detectDiskReads()
            .detectDiskWrites()
            .detectNetwork()
            .detectCustomSlowCalls()
            .penaltyLog()
            .build()
    )
    StrictMode.setVmPolicy(
        StrictMode.VmPolicy.Builder()
            .detectLeakedSqlLiteObjects()
            .detectLeakedClosableObjects()
            .penaltyLog()
            .build()
    )
}
```

### How to Check Logcat:
1. Connect a physical Android device or run a debug build.
2. Open **Logcat** in Android Studio.
3. Filter by tag: `StrictMode`.
4. Perform list scrolling (Item List, Customer List) and rapid tab switches.
5. Every main-thread disk read/write or SQLite/closeable leak will print a full stack trace identifying the exact file and line number.

---

## 2. On-Device GPU Profiling Bars

To visually inspect rendering latency and frame drops directly on a physical device without a connected debugger:

1. Go to **Settings** → **Developer Options** on your Android test device.
2. Scroll to **Profile GPU Rendering** (or *HWUI rendering*).
3. Select **On screen as bars**.
4. Open LedgerMate and scroll through lists or swipe bottom nav tabs.

### Interpreting the Overlay Bars:
- **Green Horizontal Line**: Represents the 16.6ms frame budget (60 FPS). Any vertical bar spiking above this line indicates a dropped frame (jank).
- **Blue Segment (Process / Draw)**: Time spent creating and updating Compose display lists. Tall blue bars indicate complex layout passes, expensive recompositions, or unmemoized string formatting.
- **Red Segment (Execute)**: Time spent issuing draw commands to the GPU.
- **Orange Segment (Swap Buffers)**: Time spent waiting for GPU to finish rendering.

---

## 3. Compose Recomposition Counts Inspection

To identify unstable data classes or Composables that recompose unnecessarily during scroll:

1. Run the debug build on device or emulator.
2. In Android Studio, open **Layout Inspector** (`View` → `Tool Windows` → `Layout Inspector`).
3. Select the running `com.aistudio.ledgermate.app` process.
4. Enable the **Recomposition Counts** column.
5. Scroll lists or toggle bottom nav tabs while observing counts:
   - A list item row that increments count by 20–30+ during a single scroll gesture indicates missing `@Immutable` / `@Stable` annotations or unstable key definitions.

---

## 4. Custom Trace Markers & Perfetto System Tracing

Custom trace markers are embedded around suspect JSON parsing, variant serialization, and price history calculations using `android.os.Trace.beginSection()` and `android.os.Trace.endSection()`:

- `"RateItemEntity.getVariants"`
- `"RateItemEntity.encodeVariants"`
- `"parsePriceHistory"`
- `"encodePriceHistory"`

### Capturing a Perfetto System Trace:
1. Open **Android Studio Profiler** (`View` → `Tool Windows` → `Profiler`).
2. Start a CPU profiling session and select **System Trace** (or execute `adb shell perfetto`).
3. Scroll through Item/Customer lists or switch tabs.
4. Stop recording and locate the custom named blocks in the main-thread timeline to measure exact duration in milliseconds.

---

## 5. Reporting Diagnostic Findings

When reporting performance data from a test device:
- Copy full stack traces from Logcat for `StrictMode` policy violations.
- Note which specific actions (e.g. scrolling Item List, swiping tabs) cause GPU bars to exceed the green 16ms line.
- Report any Composable with unusually high recomposition counts in Layout Inspector.
