# MCP Tool Quick Reference

**For:** Causa Engine Developers  
**Purpose:** Quick lookup of MCP tool names, inputs, outputs, and example calls

---

## Tools at a Glance

| # | Tool | Input | Output | When | Frequency |
|-|-|-|-|-|-|
| 1 | `list_profiled_pods` | `namespace?` | `List<Pod>` | Discovery | Once/alert |
| 2 | `get_pod_jvm_status` | `pod, namespace?` | `Status` | Pre-check | Once |
| 3 | `get_jvm_statistics` | `pod` | `Stats` | Quick snapshot | High-freq |
| 4 | `start_recording` | `pod, duration?, type?` | `RecordingId` | Alert | Once |
| 5 | `stop_recording` | `recording_id` | `Status` | Alert resolves | Once |
| 6 | `get_recording` | `recording_id` | `Status` | Poll | Every 10-15s |
| 7 | `get_recording_report` | `recording_id` | `Report` | After READY | Once |
| 8 | `get_flame_graph` | `recording_id, format?` | `SVG|JSON` | Analysis | Once or twice |

---

## Quick Workflow

```
Prometheus Alert (heap > 75%)
  ↓
1. list_profiled_pods() → find target pods
  ↓
2. get_pod_jvm_status(pod) → verify READY
  ↓
3. start_recording(pod) → get recording_id
  ↓
4. POLL get_recording(recording_id) every 10-15s until status=READY
  ↓
5. get_recording_report(recording_id) → parse findings
  ↓
(optional) get_flame_graph(recording_id, format='json') → analyze hotspots
  ↓
Correlate with metrics → Root Cause Analysis
```

---

## Tool Details

### 1️⃣ list_profiled_pods

Discover pods with profiler sidecar.

**Input:**
```json
{
  "namespace": "production"  // optional
}
```

**Output:**
```json
[
  {
    "podName": "myapp-0",
    "namespace": "production",
    "profilerStatus": "READY",
    "jvmVersion": "21.0.2"
  }
]
```

**Error Codes:** `CLUSTER_UNREACHABLE`, `RBAC_FORBIDDEN`, `INTERNAL_ERROR`

---

### 2️⃣ get_pod_jvm_status

Verify profiler readiness.

**Input:**
```json
{
  "pod_name": "myapp-0",
  "namespace": "production"  // optional
}
```

**Output:**
```json
{
  "podName": "myapp-0",
  "profilerStatus": "READY",
  "jvmHealth": {
    "isHealthy": true,
    "activeThreadCount": 42
  },
  "latestRecording": { /* RecordingMetadata or null */ }
}
```

**Error Codes:** `POD_NOT_FOUND`, `SIDECAR_NOT_READY`, `JVM_UNREACHABLE`, `UNAUTHORIZED`

---

### 3️⃣ get_jvm_statistics

Get live JVM metrics (no recording needed).

**Input:**
```json
{
  "pod_name": "myapp-0"
}
```

**Output:**
```json
{
  "podName": "myapp-0",
  "capturedAt": "2025-01-15T14:32:15Z",
  "heapUsedBytes": 1610612736,
  "heapMaxBytes": 2147483648,
  "heapUsagePercent": 75.0,
  "threadCount": 42,
  "gcCollectionCount": 127
}
```

**Error Codes:** `POD_NOT_FOUND`, `JVM_UNREACHABLE`, `TIMEOUT`, `INTERNAL_ERROR`

---

### 4️⃣ start_recording

Trigger recording (default 5 min).

**Input:**
```json
{
  "pod_name": "myapp-0",
  "duration_seconds": 300,      // optional, default 300
  "profile_type": "CPU"         // optional, default "CPU"
}
```

**Supported profile_types:** `CPU`, `MEMORY`, `LOCK`, `ALLOCATION`, `ALL`

**Output:**
```json
{
  "recordingId": "rec-2025-01-15-143200-abc123",
  "podName": "myapp-0",
  "status": "QUEUED",
  "createdAt": "2025-01-15T14:32:00Z",
  "profileType": "CPU"
}
```

**Error Codes:** `POD_NOT_FOUND`, `SIDECAR_NOT_READY`, `RECORDING_IN_PROGRESS`, `STORAGE_FULL`

---

### 5️⃣ stop_recording

Stop active recording.

**Input:**
```json
{
  "recording_id": "rec-2025-01-15-143200-abc123"
}
```

**Output:**
```json
{
  "recordingId": "rec-2025-01-15-143200-abc123",
  "status": "DOWNLOADING",
  "terminationReason": "USER_REQUESTED",
  "sizeBytes": 15728640
}
```

**Error Codes:** `RECORDING_NOT_FOUND`, `RECORDING_ALREADY_STOPPED`, `INTERNAL_ERROR`

---

### 6️⃣ get_recording

Poll recording status.

**Input:**
```json
{
  "recording_id": "rec-2025-01-15-143200-abc123"
}
```

**Output:**
```json
{
  "recordingId": "rec-2025-01-15-143200-abc123",
  "status": "READY",                          // QUEUED → RECORDING → DOWNLOADING → ANALYSING → READY → DELIVERED
  "terminationReason": "DURATION_EXPIRED",
  "sizeBytes": 15728640
}
```

**Status Lifecycle:**
```
QUEUED → RECORDING → DOWNLOADING → ANALYSING → READY → DELIVERED
                                              ↓
                                        Call get_recording_report here
```

**Possible Early Termination:** `CONTAINER_OOM`, `POD_DELETED`, `ERROR`

---

### 7️⃣ get_recording_report

Get full analysis (only when status=READY).

**Input:**
```json
{
  "recording_id": "rec-2025-01-15-143200-abc123"
}
```

**Output:** `ProfilingReport` with:
- **summary** — overall findings + observations
- **cpu** — top-5 hotspot methods
- **memory** — top-5 allocation sites
- **gc** — GC pause summary + recent events
- **threads** — thread breakdown
- **locks** — lock contention
- **jvm** — JVM metadata

**Key fields in report.summary:**
```json
{
  "cpuUsagePercent": 42.5,
  "heapUsagePercent": 75.0,
  "gcEventCount": 3,
  "topHotspot": "com.myapp.processor.ProcessorService.process",
  "observations": [
    "CPU usage is moderate but concentrated in request processing",
    "Heap is at 75% — consider monitoring after deployment"
  ]
}
```

---

### 8️⃣ get_flame_graph

Get flame graph (SVG or JSON).

**Input:**
```json
{
  "recording_id": "rec-2025-01-15-143200-abc123",
  "format": "json"  // "svg" or "json", default "json"
}
```

**Output (JSON format):**
```json
{
  "recordingId": "rec-2025-01-15-143200-abc123",
  "format": "json",
  "frames": [
    {
      "signature": "java.lang.Thread.run",
      "samples": 11000,
      "percent": 100.0,
      "children": [ /* nested frames */ ]
    }
  ]
}
```

**Output (SVG format):** Self-contained HTML/SVG string ready for browser display.

---

## Error Handling

All errors follow this envelope:

```json
{
  "error": {
    "code": -32603,
    "message": "Internal error",
    "data": {
      "errorCode": "SPECIFIC_ERROR_CODE",
      "message": "Human-readable message",
      "details": "Additional context",
      "timestamp": "2025-01-15T14:30:00Z"
    }
  }
}
```

**Common Error Codes:**

| Code | HTTP | Meaning | Fix |
|------|------|---------|-----|
| `POD_NOT_FOUND` | 404 | Pod doesn't exist | Verify pod name |
| `SIDECAR_NOT_READY` | 503 | Sidecar not attached | Wait and retry |
| `RECORDING_IN_PROGRESS` | 409 | Pod already recording | Wait or call `stop_recording` |
| `RECORDING_NOT_READY` | 409 | Analysis still running | Retry after 10s |
| `JVM_UNREACHABLE` | 503 | Can't reach JVM | Check pod logs |
| `CONTAINER_OOM` | 500 | Pod ran out of memory | Check pod events |
| `INTERNAL_ERROR` | 500 | Server error | Check logs; retry |

---

## Polling Strategy

**Recommended polling intervals:**

| Operation | Interval | Total Timeout |
|-----------|----------|---------------|
| `get_recording_status` | 10–15 seconds | 10–15 minutes |
| `get_jvm_statistics` (monitoring) | 5–10 seconds | N/A (continuous) |
| `get_pod_jvm_status` (health check) | 30 seconds | N/A (background) |

---

## State Transitions

### Recording Status

```
       start_recording()
              ↓
          QUEUED
          /    \
     (waiting)  (profiler acquired slot)
        /            \
       /              ↓
                   RECORDING
                   /    \
      (duration    |      | (stop_recording)
       expired)   |       |
                  ↓       ↓
              DOWNLOADING
                  ↓
              ANALYSING
                  ↓
                 READY ←────← get_recording_report()
                  ↓
             DELIVERED
```

**Early termination:**
- `CONTAINER_OOM` — Container ran out of memory
- `POD_DELETED` — Pod was deleted/evicted
- `ERROR` — Recording failed

---

## Example Causa Workflow

### Full Alert Response

```python
def handle_heap_alert(pod_name):
    # 1. Verify pod is ready
    status = get_pod_jvm_status(pod_name)
    if status['profilerStatus'] != 'READY':
        return None
    
    # 2. Start recording
    result = start_recording(pod_name, duration_seconds=300)
    recording_id = result['recordingId']
    
    # 3. Poll for completion (max 10 minutes)
    for i in range(40):  # 40 * 15s = 10 min
        recording = get_recording(recording_id)
        if recording['status'] == 'READY':
            break
        elif recording['status'] == 'ERROR':
            return f"Recording failed: {recording['terminationReason']}"
        time.sleep(15)
    
    # 4. Get report
    report = get_recording_report(recording_id)
    
    # 5. Analyze
    findings = parse_report(report)
    correlate_with_metrics(findings)
    
    return findings
```

---

## Testing Checklist

- [ ] Can list profiled pods
- [ ] Can verify pod health before recording
- [ ] Can start recording and get recording_id
- [ ] Can poll status transitions (QUEUED → RECORDING → ... → READY)
- [ ] Can retrieve report after READY
- [ ] Can get flame graph in both JSON and SVG formats
- [ ] Can handle errors gracefully (POD_NOT_FOUND, TIMEOUT, etc.)
- [ ] Polling timeout is configured appropriately
- [ ] Report parsing handles all sections (cpu, memory, gc, threads, locks)

---

## Links

- **Full Specification:** `MCP_TOOL_CONTRACT_SPECIFICATION.md`
- **DTOs:** `Dtos.java`
- **Tools Implementation:** `ProfilingTools.java`
- **Architecture:** `README.md`
