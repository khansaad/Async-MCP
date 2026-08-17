# Controller contract payloads

This document extracts the actual controller request and response payloads from the current MCP server contract, without the WireMock-style `request` / `response` wrapper used in files like [`04-start-recording.json`](04-start-recording.json).

Code references:
- [`ProfiledPodMetadata`](src/main/java/io/causa/profiler/dto/Dtos.java:62)
- [`PodJvmStatus`](src/main/java/io/causa/profiler/dto/Dtos.java:77)
- [`RecordingMetadata`](src/main/java/io/causa/profiler/dto/Dtos.java:101)
- [`StartRecordingRequest`](src/main/java/io/causa/profiler/dto/Dtos.java:121)
- [`ProfilingReport`](src/main/java/io/causa/profiler/dto/Dtos.java:134)
- [`JvmStatistics`](src/main/java/io/causa/profiler/dto/Dtos.java:273)
- [`ControllerApi`](src/main/java/io/causa/profiler/service/ProfilerServiceClient.java:109)

## 1. List profiled pods

Endpoint:
- `GET /pods`

Response body:

```json
[
  {
    "podName": "myapp-service-0",
    "namespace": "production",
    "profilerStatus": "READY",
    "jvmVersion": "21.0.2",
    "profilerVersion": "2.9",
    "lastProfiledAt": "2025-01-15T14:20:00Z",
    "latestRecordingId": "rec-abc123"
  }
]
```

## 2. Get pod JVM status

Endpoint:
- `GET /pods/{pod}/status?namespace=production`

Response body:

```json
{
  "podName": "myapp-service-0",
  "profilerStatus": "READY",
  "jvmHealth": {
    "isHealthy": true,
    "jvmUptime": "PT2H30M",
    "activeThreadCount": 42,
    "lastHeartbeat": "2025-01-15T14:00:00Z"
  },
  "latestRecording": {
    "recordingId": "rec-abc123",
    "podName": "myapp-service-0",
    "status": "READY",
    "terminationReason": "DURATION_EXPIRED",
    "createdAt": "2025-01-15T14:23:00Z",
    "terminatedAt": "2025-01-15T14:28:00Z",
    "durationSeconds": 300,
    "sizeBytes": 2097152,
    "profileType": "CPU",
    "expiresAt": "2025-01-15T15:23:00Z"
  },
  "message": "Profiler ready"
}
```

## 3. Get JVM statistics

Endpoint:
- `GET /pods/{pod}/stats`

Response body:

```json
{
  "podName": "myapp-service-0",
  "capturedAt": "2025-01-15T14:23:00Z",
  "heapUsedBytes": 629145600,
  "heapMaxBytes": 1073741824,
  "nonHeapUsedBytes": 134217728,
  "heapUsagePercent": 58.6,
  "threadCount": 42,
  "peakThreadCount": 55,
  "gcCollectionCount": 120,
  "gcCollectionTimeMs": 3400,
  "loadedClassCount": 8920
}
```

## 4. Start recording

Endpoint:
- `POST /pods/{pod}/recordings`

Request body:

```json
{
  "durationSeconds": 300,
  "profileType": "CPU"
}
```

Response body:

```json
{
  "recordingId": "rec-test-001",
  "podName": "myapp-service-0",
  "status": "RECORDING",
  "createdAt": "2025-01-15T14:23:00Z",
  "durationSeconds": 300,
  "profileType": "CPU",
  "expiresAt": "2025-01-15T15:23:00Z"
}
```

## 5. Stop recording

Endpoint:
- `DELETE /recordings/{id}`

Response body:

```json
{
  "recordingId": "rec-test-001",
  "podName": "myapp-service-0",
  "status": "DOWNLOADING",
  "terminationReason": "USER_REQUESTED",
  "createdAt": "2025-01-15T14:23:00Z",
  "terminatedAt": "2025-01-15T14:24:30Z",
  "durationSeconds": 90,
  "profileType": "CPU"
}
```

## 6. Get recording status

Endpoint:
- `GET /recordings/{id}`

Response body:

```json
{
  "recordingId": "rec-test-001",
  "podName": "myapp-service-0",
  "status": "READY",
  "terminationReason": "DURATION_EXPIRED",
  "createdAt": "2025-01-15T14:23:00Z",
  "terminatedAt": "2025-01-15T14:28:00Z",
  "durationSeconds": 300,
  "sizeBytes": 2097152,
  "profileType": "CPU",
  "expiresAt": "2025-01-15T15:23:00Z"
}
```

## 7. Get recording report

Endpoint:
- `GET /recordings/{id}/report`

Response body:

```json
{
  "schemaVersion": "1.0",
  "podName": "myapp-service-0",
  "recordingId": "rec-test-001",
  "reportGeneratedAt": "2025-01-15T14:29:00Z",
  "summary": {
    "profilingDuration": "PT5M",
    "totalSamples": 3500,
    "cpuUsagePercent": 87.3,
    "heapUsagePercent": 76.1,
    "gcEventCount": 42,
    "topHotspot": "com.example.RetainedHeap.retain",
    "observations": [
      "High CPU in retain()",
      "GC pressure elevated"
    ]
  },
  "cpu": {
    "totalCpuTimeMs": 12450.5,
    "topFrames": [
      {
        "frameSignature": "com.example.RetainedHeap.retain(byte[])",
        "sampleCount": 1207,
        "percentOfTotal": 34.5,
        "frameType": "JAVA_COMPILED"
      }
    ],
    "cpuByThread": {
      "main": 45.2,
      "worker-1": 23.8
    }
  },
  "memory": {
    "heapUsedBytes": 817889280,
    "heapMaxBytes": 1073741824,
    "nonHeapUsedBytes": 134217728,
    "heapUsagePercent": 76.1,
    "topAllocators": [
      {
        "allocatorClass": "byte[]",
        "bytesAllocated": 268435456,
        "objectCount": 1048576,
        "percentOfTotal": 31.4
      }
    ]
  },
  "gc": {
    "gcEventCount": 42,
    "totalGcPauseMs": 1800,
    "avgGcPauseMs": 42.8,
    "maxGcPauseMs": 120.5,
    "recentEvents": [
      {
        "timestamp": "2025-01-15T14:27:45Z",
        "gcType": "Young",
        "pauseMs": 38,
        "freedBytes": 52428800,
        "heapBeforeBytes": 838860800,
        "heapAfterBytes": 786432000
      }
    ]
  },
  "threads": {
    "peakThreadCount": 55,
    "currentThreadCount": 42,
    "topThreads": [
      {
        "threadName": "main",
        "threadId": 1,
        "state": "RUNNABLE",
        "cpuTimeMs": 5400,
        "topStackFrame": "com.example.RetainedHeap.retain(byte[])"
      }
    ]
  },
  "locks": {
    "contentionEventCount": 3,
    "topContended": [
      {
        "lockClass": "java.util.concurrent.ConcurrentHashMap",
        "contentionCount": 3,
        "totalWaitMs": 18,
        "avgWaitMs": 6.0
      }
    ]
  },
  "jvm": {
    "jvmName": "OpenJDK 64-Bit Server VM",
    "jvmVersion": "21.0.2+13",
    "vmVendor": "Eclipse Adoptium",
    "vmStartTime": "2025-01-15T10:00:00Z",
    "uptimeMs": 15480000
  }
}
```

Notes:
- The response can be partial. The DTO uses nullable sections, so the controller does not need to populate every top-level section in all cases.
- The sample in [`07-get-report.json`](07-get-report.json) is a valid minimal report shape.

## 8. Get flame graph

Endpoint:
- `GET /recordings/{id}/flamegraph?format=json`

Response body for `format=json`:

```json
{
  "frames": [
    {
      "name": "com.example.RetainedHeap.retain",
      "value": 1207,
      "children": []
    }
  ]
}
```

Response body for `format=svg`:
- raw SVG payload returned as a string

## Summary

The JSON files in the repo root are mock/stub definitions. For controller implementation, use the payloads in this document:
- response payload = inner `jsonBody` from the mock files
- request payload for starting a recording = [`StartRecordingRequest`](src/main/java/io/causa/profiler/dto/Dtos.java:121)
