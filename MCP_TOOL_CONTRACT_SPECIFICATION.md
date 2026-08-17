# MCP Tool Contract Specification

**Version:** 1.0  
**Schema Version:** 1.0  
**Last Updated:** 2025-01  
**Target Audience:** Causa Engine team, Profiling Controller team

---

## Overview

This document defines the MCP (Model Context Protocol) tool interface for JVM profiling. The **async-profiler-mcp-server** server exposes 8 tools that Causa Engine can call via MCP (JSON-RPC over stdio or SSE). All request/response schemas are strict contracts that both teams must follow.

### Key Principles

1. **Strict schemas:** Every field has a defined type and nullability
2. **Versioning:** Schema version 1.0 — changes bump major version
3. **Errors:** All errors follow the `ProfilingError` envelope
4. **Asynchronous:** Recording and analysis are long-running; poll status with `get_recording_status`
5. **Kubernetes-native:** All references use pod name + namespace (optional, defaults to `default`)

---

## Tool Inventory

| # | Tool | Purpose |
|---|------|---------|
| 1 | `list_profiled_pods` | Discover pods with profiler sidecar |
| 2 | `get_pod_jvm_status` | Check profiler readiness + latest recording |
| 3 | `get_jvm_statistics` | Live JVM snapshot (heap, threads, GC) |
| 4 | `start_recording` | Trigger on-demand JFR recording |
| 5 | `stop_recording` | Stop recording early |
| 6 | `get_recording` | Poll recording status (with state machine) |
| 7 | `get_recording_report` | Retrieve structured analysis JSON |
| 8 | `get_flame_graph` | Retrieve flame graph (SVG or JSON) |

---

## Tool Specifications

---

## 1. `list_profiled_pods`

**Discover JVM applications available for profiling.**

### Description

Returns a list of Kubernetes pods that have an async-profiler sidecar ready for profiling. Useful for discovering target pods during an alert.

### Purpose

- **When:** Alert fires; need to find which pods to profile
- **Who calls:** Causa Engine (no user input required)
- **Frequency:** Once per alert cycle (or cached)

### Input Schema

```json
{
  "namespace": "string (optional, default: 'default')"
}
```

| Parameter | Type | Required | Description | Example |
|-----------|------|----------|-------------|---------|
| `namespace` | `string` | No | Kubernetes namespace to filter by. If omitted, lists all namespaces. | `"production"` |

### Output Schema

```json
{
  "type": "array",
  "items": {
    "type": "object",
    "properties": {
      "podName": {
        "type": "string",
        "description": "Kubernetes pod name"
      },
      "namespace": {
        "type": "string",
        "description": "Kubernetes namespace"
      },
      "profilerStatus": {
        "type": "string",
        "enum": ["READY", "NOT_READY", "UNKNOWN"],
        "description": "Async-profiler sidecar status"
      },
      "jvmVersion": {
        "type": "string",
        "nullable": true,
        "description": "Java version (e.g., '21.0.2')"
      },
      "profilerVersion": {
        "type": "string",
        "nullable": true,
        "description": "Async-profiler version"
      },
      "lastProfiledAt": {
        "type": "string (ISO-8601)",
        "nullable": true,
        "description": "Timestamp of last successful recording"
      },
      "latestRecordingId": {
        "type": "string",
        "nullable": true,
        "description": "Most recent recording ID on this pod"
      }
    },
    "required": ["podName", "namespace", "profilerStatus"]
  }
}
```

### Error Codes

| Code | HTTP Status | Meaning | Mitigation |
|------|-------------|---------|-----------|
| `CLUSTER_UNREACHABLE` | 503 | Cannot connect to Kubernetes API | Retry after 30s |
| `RBAC_FORBIDDEN` | 403 | No permission to list pods | Check service account permissions |
| `INTERNAL_ERROR` | 500 | Unexpected server error | Check logs; retry |

### Example Request

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "list_profiled_pods",
    "arguments": {
      "namespace": "production"
    }
  }
}
```

### Example Response

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": [
    {
      "podName": "myapp-service-0",
      "namespace": "production",
      "profilerStatus": "READY",
      "jvmVersion": "21.0.2",
      "profilerVersion": "2.9",
      "lastProfiledAt": "2025-01-15T14:23:00Z",
      "latestRecordingId": "rec-abc123"
    },
    {
      "podName": "myapp-service-1",
      "namespace": "production",
      "profilerStatus": "READY",
      "jvmVersion": "21.0.2",
      "profilerVersion": "2.9",
      "lastProfiledAt": "2025-01-15T13:45:00Z",
      "latestRecordingId": "rec-def456"
    }
  ]
}
```

### Example Error Response

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "error": {
    "code": -32603,
    "message": "Internal error",
    "data": {
      "errorCode": "CLUSTER_UNREACHABLE",
      "message": "Cannot reach Kubernetes API server",
      "details": "connection timeout after 5s",
      "timestamp": "2025-01-15T14:30:00Z"
    }
  }
}
```

---

## 2. `get_pod_jvm_status`

**Return profiler/JVM health and latest recording.**

### Description

Check whether the async-profiler sidecar is healthy and ready to record. Also returns the most recent recording metadata (if any) for the pod.

### Purpose

- **When:** Before starting a new recording, verify readiness
- **Who calls:** Causa Engine
- **Frequency:** Once before `start_recording`; or repeatedly to monitor health

### Input Schema

```json
{
  "pod_name": "string (required)",
  "namespace": "string (optional, default: 'default')"
}
```

| Parameter | Type | Required | Description | Example |
|-----------|------|----------|-------------|---------|
| `pod_name` | `string` | Yes | Kubernetes pod name | `"myapp-0"` |
| `namespace` | `string` | No | Kubernetes namespace | `"production"` |

### Output Schema

```json
{
  "type": "object",
  "properties": {
    "podName": {
      "type": "string",
      "description": "Echo of input pod name"
    },
    "profilerStatus": {
      "type": "string",
      "enum": ["READY", "NOT_READY", "UNKNOWN"],
      "description": "Sidecar readiness"
    },
    "jvmHealth": {
      "type": "object",
      "properties": {
        "isHealthy": {
          "type": "boolean",
          "description": "True if JVM is running and responding"
        },
        "jvmUptime": {
          "type": "string",
          "description": "JVM uptime (e.g., '2h 30m')"
        },
        "activeThreadCount": {
          "type": "integer",
          "description": "Current thread count"
        },
        "lastHeartbeat": {
          "type": "string (ISO-8601)",
          "description": "Last successful health check"
        }
      },
      "required": ["isHealthy"]
    },
    "latestRecording": {
      "type": "object (RecordingMetadata)",
      "nullable": true,
      "description": "Most recent recording; null if none"
    },
    "message": {
      "type": "string",
      "nullable": true,
      "description": "Human-readable status message"
    }
  },
  "required": ["podName", "profilerStatus", "jvmHealth"]
}
```

### Error Codes

| Code | HTTP Status | Meaning | Mitigation |
|------|-------------|---------|-----------|
| `POD_NOT_FOUND` | 404 | Pod does not exist | Verify pod name; check namespace |
| `SIDECAR_NOT_READY` | 503 | Sidecar not attached or initializing | Retry after 10s |
| `JVM_UNREACHABLE` | 503 | Cannot reach JVM inside pod | Check pod logs |
| `UNAUTHORIZED` | 401 | Invalid credentials to pod | Check auth config |
| `INTERNAL_ERROR` | 500 | Server error | Check logs; retry |

### Example Request

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "get_pod_jvm_status",
    "arguments": {
      "pod_name": "myapp-0",
      "namespace": "production"
    }
  }
}
```

### Example Response

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "podName": "myapp-0",
    "profilerStatus": "READY",
    "jvmHealth": {
      "isHealthy": true,
      "jvmUptime": "5d 12h 30m",
      "activeThreadCount": 42,
      "lastHeartbeat": "2025-01-15T14:30:05Z"
    },
    "latestRecording": {
      "recordingId": "rec-xyz789",
      "podName": "myapp-0",
      "status": "DELIVERED",
      "terminationReason": "DURATION_EXPIRED",
      "createdAt": "2025-01-15T12:00:00Z",
      "terminatedAt": "2025-01-15T12:05:30Z",
      "durationSeconds": 300,
      "sizeBytes": 15728640,
      "profileType": "CPU",
      "expiresAt": "2025-01-22T12:05:30Z"
    },
    "message": "Profiler ready; last recording completed successfully"
  }
}
```

---

## 3. `get_jvm_statistics`

**Return live JVM snapshot (heap, threads, GC).**

### Description

Capture a live snapshot of JVM metrics without requiring a full JFR recording. Useful for quick health checks or establishing a baseline before starting a recording.

### Purpose

- **When:** Need quick JVM metrics without waiting for a recording
- **Who calls:** Causa Engine
- **Frequency:** Multiple times per minute (high-frequency monitoring)

### Input Schema

```json
{
  "pod_name": "string (required)"
}
```

| Parameter | Type | Required | Description | Example |
|-----------|------|----------|-------------|---------|
| `pod_name` | `string` | Yes | Kubernetes pod name | `"myapp-0"` |

### Output Schema

```json
{
  "type": "object",
  "properties": {
    "podName": {
      "type": "string",
      "description": "Echo of input pod name"
    },
    "capturedAt": {
      "type": "string (ISO-8601)",
      "description": "Timestamp when snapshot was captured"
    },
    "heapUsedBytes": {
      "type": "long (int64)",
      "description": "Heap memory in use (bytes)"
    },
    "heapMaxBytes": {
      "type": "long (int64)",
      "description": "Maximum heap memory (bytes)"
    },
    "nonHeapUsedBytes": {
      "type": "long (int64)",
      "description": "Non-heap memory in use (bytes)"
    },
    "heapUsagePercent": {
      "type": "double (0.0–100.0)",
      "description": "Heap usage percentage"
    },
    "threadCount": {
      "type": "integer",
      "description": "Current thread count"
    },
    "peakThreadCount": {
      "type": "integer",
      "description": "Peak thread count since JVM start"
    },
    "gcCollectionCount": {
      "type": "long (int64)",
      "description": "Total garbage collection invocations"
    },
    "gcCollectionTimeMs": {
      "type": "long (int64)",
      "description": "Total GC collection time (milliseconds)"
    },
    "loadedClassCount": {
      "type": "integer",
      "description": "Number of loaded classes"
    }
  },
  "required": ["podName", "capturedAt", "heapUsedBytes", "heapMaxBytes", "heapUsagePercent"]
}
```

### Error Codes

| Code | HTTP Status | Meaning | Mitigation |
|------|-------------|---------|-----------|
| `POD_NOT_FOUND` | 404 | Pod does not exist | Verify pod name |
| `JVM_UNREACHABLE` | 503 | Cannot reach JVM | Check pod logs |
| `TIMEOUT` | 504 | JVM did not respond in time | Retry after 5s |
| `INTERNAL_ERROR` | 500 | Server error | Check logs; retry |

### Example Request

```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "tools/call",
  "params": {
    "name": "get_jvm_statistics",
    "arguments": {
      "pod_name": "myapp-0"
    }
  }
}
```

### Example Response

```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "result": {
    "podName": "myapp-0",
    "capturedAt": "2025-01-15T14:32:15Z",
    "heapUsedBytes": 1610612736,
    "heapMaxBytes": 2147483648,
    "nonHeapUsedBytes": 134217728,
    "heapUsagePercent": 75.0,
    "threadCount": 42,
    "peakThreadCount": 64,
    "gcCollectionCount": 127,
    "gcCollectionTimeMs": 3450,
    "loadedClassCount": 18392
  }
}
```

---

## 4. `start_recording`

**Trigger an on-demand JFR recording.**

### Description

Start a JFR recording on a target pod. Returns a `recording_id` immediately. The recording runs for `duration_seconds` (default 300 seconds = 5 minutes). Pass `duration_seconds=0` to run indefinitely until `stop_recording` is called.

**Note:** Recording is asynchronous. Poll `get_recording_status` every 10–15 seconds to track progress.

### Purpose

- **When:** Prometheus alert fires (e.g., heap > 75%)
- **Who calls:** Causa Engine
- **Frequency:** Once per alert

### Input Schema

```json
{
  "pod_name": "string (required)",
  "duration_seconds": "integer (optional, default: 300)",
  "profile_type": "string (optional, default: 'CPU')"
}
```

| Parameter | Type | Required | Description | Example |
|-----------|------|----------|-------------|---------|
| `pod_name` | `string` | Yes | Kubernetes pod name to profile | `"myapp-0"` |
| `duration_seconds` | `integer` | No | Recording duration in seconds. Default 300. Pass 0 for indefinite. | `300` or `0` |
| `profile_type` | `string` | No | What to profile: `CPU`, `MEMORY`, `LOCK`, `ALLOCATION`, or `ALL`. Default `CPU`. | `"CPU"` |

### Output Schema

**Returns:** `RecordingMetadata` (see below)

```json
{
  "type": "object",
  "properties": {
    "recordingId": {
      "type": "string",
      "description": "Unique recording identifier"
    },
    "podName": {
      "type": "string",
      "description": "Echo of input pod name"
    },
    "status": {
      "type": "string",
      "enum": ["QUEUED", "RECORDING", "DOWNLOADING", "ANALYSING", "READY", "DELIVERED", "EXPIRED", "ERROR"],
      "description": "Current recording state"
    },
    "terminationReason": {
      "type": "string",
      "enum": ["USER_REQUESTED", "DURATION_EXPIRED", "CONTAINER_OOM", "POD_DELETED", "ERROR"],
      "nullable": true,
      "description": "Why recording ended (populated after termination)"
    },
    "createdAt": {
      "type": "string (ISO-8601)",
      "description": "When recording was created"
    },
    "terminatedAt": {
      "type": "string (ISO-8601)",
      "nullable": true,
      "description": "When recording ended (null if still running)"
    },
    "durationSeconds": {
      "type": "long",
      "description": "Requested duration in seconds"
    },
    "sizeBytes": {
      "type": "long",
      "nullable": true,
      "description": "Final JFR file size (null until complete)"
    },
    "profileType": {
      "type": "string",
      "description": "What was profiled (CPU, MEMORY, etc)"
    },
    "expiresAt": {
      "type": "string (ISO-8601)",
      "description": "When this recording will be deleted from storage"
    }
  },
  "required": ["recordingId", "podName", "status", "createdAt", "profileType"]
}
```

### Error Codes

| Code | HTTP Status | Meaning | Mitigation |
|------|-------------|---------|-----------|
| `POD_NOT_FOUND` | 404 | Pod does not exist | Verify pod name |
| `SIDECAR_NOT_READY` | 503 | Sidecar not attached | Wait and retry |
| `RECORDING_IN_PROGRESS` | 409 | Pod already has active recording | Wait for it to finish or call `stop_recording` |
| `JVM_UNREACHABLE` | 503 | Cannot reach JVM | Check pod logs |
| `STORAGE_FULL` | 507 | Not enough disk space for JFR file | Free space on controller |
| `INTERNAL_ERROR` | 500 | Server error | Check logs; retry |

### Example Request

```json
{
  "jsonrpc": "2.0",
  "id": 4,
  "method": "tools/call",
  "params": {
    "name": "start_recording",
    "arguments": {
      "pod_name": "myapp-0",
      "duration_seconds": 300,
      "profile_type": "CPU"
    }
  }
}
```

### Example Response

```json
{
  "jsonrpc": "2.0",
  "id": 4,
  "result": {
    "recordingId": "rec-2025-01-15-143200-abc123",
    "podName": "myapp-0",
    "status": "QUEUED",
    "terminationReason": null,
    "createdAt": "2025-01-15T14:32:00Z",
    "terminatedAt": null,
    "durationSeconds": 300,
    "sizeBytes": null,
    "profileType": "CPU",
    "expiresAt": "2025-01-22T14:32:00Z"
  }
}
```

---

## 5. `stop_recording`

**Stop recording early (before duration expires).**

### Description

Stop an in-progress recording. The controller finalizes the JFR file and begins analysis immediately. Poll `get_recording_status` to track the transition to `READY`, then call `get_recording_report`.

### Purpose

- **When:** Alert resolves; recording duration not yet expired
- **Who calls:** Causa Engine
- **Frequency:** Once per active recording

### Input Schema

```json
{
  "recording_id": "string (required)"
}
```

| Parameter | Type | Required | Description | Example |
|-----------|------|----------|-------------|---------|
| `recording_id` | `string` | Yes | The recording ID returned by `start_recording` | `"rec-2025-01-15-143200-abc123"` |

### Output Schema

**Returns:** `RecordingMetadata` with `status` transitioned to `DOWNLOADING` and `terminationReason=USER_REQUESTED`

### Error Codes

| Code | HTTP Status | Meaning | Mitigation |
|------|-------------|---------|-----------|
| `RECORDING_NOT_FOUND` | 404 | Recording ID does not exist | Verify recording ID |
| `RECORDING_ALREADY_STOPPED` | 409 | Recording is not active | Already stopped; call `get_recording` instead |
| `INTERNAL_ERROR` | 500 | Server error | Check logs; retry |

### Example Request

```json
{
  "jsonrpc": "2.0",
  "id": 5,
  "method": "tools/call",
  "params": {
    "name": "stop_recording",
    "arguments": {
      "recording_id": "rec-2025-01-15-143200-abc123"
    }
  }
}
```

### Example Response

```json
{
  "jsonrpc": "2.0",
  "id": 5,
  "result": {
    "recordingId": "rec-2025-01-15-143200-abc123",
    "podName": "myapp-0",
    "status": "DOWNLOADING",
    "terminationReason": "USER_REQUESTED",
    "createdAt": "2025-01-15T14:32:00Z",
    "terminatedAt": "2025-01-15T14:33:45Z",
    "durationSeconds": 300,
    "sizeBytes": null,
    "profileType": "CPU",
    "expiresAt": "2025-01-22T14:32:00Z"
  }
}
```

---

## 6. `get_recording`

**Poll recording status (state machine).**

### Description

Poll the current state of a recording. The status follows this lifecycle:

```
QUEUED → RECORDING → DOWNLOADING → ANALYSING → READY → DELIVERED
```

Poll every 10–15 seconds. Once status reaches `READY`, call `get_recording_report` to retrieve the analysis.

### Purpose

- **When:** Polling for recording progress
- **Who calls:** Causa Engine
- **Frequency:** Every 10–15 seconds until status=READY

### Input Schema

```json
{
  "recording_id": "string (required)"
}
```

| Parameter | Type | Required | Description | Example |
|-----------|------|----------|-------------|---------|
| `recording_id` | `string` | Yes | The recording ID from `start_recording` | `"rec-2025-01-15-143200-abc123"` |

### Output Schema

**Returns:** `RecordingMetadata`

### Status State Machine

| From | To | Reason |
|------|----|----|
| `QUEUED` | `RECORDING` | Profiler acquired a slot; recording started |
| `RECORDING` | `DOWNLOADING` | Duration expired OR `stop_recording` called |
| `DOWNLOADING` | `ANALYSING` | JFR file retrieved from pod |
| `ANALYSING` | `READY` | Analysis complete; report available |
| `READY` | `DELIVERED` | `get_recording_report` called |
| Any | `ERROR` | Exception occurred (check `terminationReason`) |
| Any | `EXPIRED` | Exceeded retention window (usually 7 days) |

### Error Codes

| Code | HTTP Status | Meaning | Mitigation |
|------|-------------|---------|-----------|
| `RECORDING_NOT_FOUND` | 404 | Recording ID does not exist | Verify recording ID |
| `INTERNAL_ERROR` | 500 | Server error | Check logs; retry |

### Example Requests & Responses

**Request:**

```json
{
  "jsonrpc": "2.0",
  "id": 6,
  "method": "tools/call",
  "params": {
    "name": "get_recording",
    "arguments": {
      "recording_id": "rec-2025-01-15-143200-abc123"
    }
  }
}
```

**Response (Status: RECORDING):**

```json
{
  "jsonrpc": "2.0",
  "id": 6,
  "result": {
    "recordingId": "rec-2025-01-15-143200-abc123",
    "podName": "myapp-0",
    "status": "RECORDING",
    "terminationReason": null,
    "createdAt": "2025-01-15T14:32:00Z",
    "terminatedAt": null,
    "durationSeconds": 300,
    "sizeBytes": null,
    "profileType": "CPU",
    "expiresAt": "2025-01-22T14:32:00Z"
  }
}
```

**Response (Status: READY):**

```json
{
  "jsonrpc": "2.0",
  "id": 6,
  "result": {
    "recordingId": "rec-2025-01-15-143200-abc123",
    "podName": "myapp-0",
    "status": "READY",
    "terminationReason": "DURATION_EXPIRED",
    "createdAt": "2025-01-15T14:32:00Z",
    "terminatedAt": "2025-01-15T14:37:00Z",
    "durationSeconds": 300,
    "sizeBytes": 15728640,
    "profileType": "CPU",
    "expiresAt": "2025-01-22T14:32:00Z"
  }
}
```

**Response (Status: ERROR):**

```json
{
  "jsonrpc": "2.0",
  "id": 6,
  "result": {
    "recordingId": "rec-2025-01-15-143200-abc123",
    "podName": "myapp-0",
    "status": "ERROR",
    "terminationReason": "CONTAINER_OOM",
    "createdAt": "2025-01-15T14:32:00Z",
    "terminatedAt": "2025-01-15T14:33:15Z",
    "durationSeconds": 300,
    "sizeBytes": null,
    "profileType": "CPU",
    "expiresAt": "2025-01-22T14:32:00Z"
  }
}
```

---

## 7. `get_recording_report`

**Retrieve structured analysis JSON.**

### Description

Return the full structured analysis report for a completed JFR recording. Only callable after `get_recording_status` returns `status=READY`.

The report includes: CPU hotspots, memory allocation sites, GC statistics, thread activity, lock contention, and JVM metadata. All subsections are human-readable and suitable for both Causa AI reasoning and human review.

### Purpose

- **When:** Recording analysis is complete (status=READY)
- **Who calls:** Causa Engine
- **Frequency:** Once per recording

### Input Schema

```json
{
  "recording_id": "string (required)"
}
```

| Parameter | Type | Required | Description | Example |
|-----------|------|----------|-------------|---------|
| `recording_id` | `string` | Yes | The recording ID | `"rec-2025-01-15-143200-abc123"` |

### Output Schema

**Returns:** `ProfilingReport`

```json
{
  "type": "object",
  "properties": {
    "schemaVersion": {
      "type": "string",
      "description": "Report schema version (currently '1.0')"
    },
    "podName": {
      "type": "string",
      "description": "Pod being profiled"
    },
    "recordingId": {
      "type": "string",
      "description": "Recording ID"
    },
    "reportGeneratedAt": {
      "type": "string (ISO-8601)",
      "description": "When the report was generated"
    },
    "summary": {
      "type": "object",
      "properties": {
        "profilingDuration": {
          "type": "string",
          "description": "How long the recording ran (e.g., '5m 30s')"
        },
        "totalSamples": {
          "type": "integer",
          "description": "Total stack trace samples collected"
        },
        "cpuUsagePercent": {
          "type": "double",
          "description": "Average CPU usage during recording"
        },
        "heapUsagePercent": {
          "type": "double",
          "description": "Average heap usage during recording"
        },
        "gcEventCount": {
          "type": "integer",
          "description": "Number of GC events during recording"
        },
        "topHotspot": {
          "type": "string",
          "description": "Top CPU-consuming method"
        },
        "observations": {
          "type": "array of strings",
          "description": "Human-readable findings (ready for Causa to reason over)"
        }
      }
    },
    "cpu": {
      "type": "object",
      "properties": {
        "totalCpuTimeMs": {
          "type": "long",
          "description": "Total CPU time (milliseconds)"
        },
        "topFrames": {
          "type": "array",
          "description": "Top-5 CPU hotspot methods",
          "items": {
            "type": "object",
            "properties": {
              "frameSignature": {
                "type": "string",
                "description": "Fully qualified method name (e.g., 'com.example.MyClass.process')"
              },
              "sampleCount": {
                "type": "integer",
                "description": "Number of times this frame appeared in samples"
              },
              "percentOfTotal": {
                "type": "double",
                "description": "Percentage of all CPU samples"
              },
              "frameType": {
                "type": "string",
                "enum": ["KERNEL", "NATIVE", "CPP", "JAVA_COMPILED", "INLINED", "INTERPRETED"],
                "description": "Type of frame"
              }
            }
          }
        },
        "cpuByThread": {
          "type": "object (map)",
          "description": "CPU usage keyed by thread name (e.g., {'main': 45.2, 'worker-1': 32.1})"
        }
      }
    },
    "memory": {
      "type": "object",
      "properties": {
        "heapUsedBytes": {
          "type": "long",
          "description": "Peak heap usage during recording"
        },
        "heapMaxBytes": {
          "type": "long",
          "description": "Maximum heap size"
        },
        "nonHeapUsedBytes": {
          "type": "long",
          "description": "Peak non-heap (e.g., metaspace) usage"
        },
        "heapUsagePercent": {
          "type": "double",
          "description": "Peak heap usage percentage"
        },
        "topAllocators": {
          "type": "array",
          "description": "Top-5 memory allocation sites",
          "items": {
            "type": "object",
            "properties": {
              "allocatorClass": {
                "type": "string",
                "description": "Class doing the allocation"
              },
              "bytesAllocated": {
                "type": "long",
                "description": "Total bytes allocated from this site"
              },
              "objectCount": {
                "type": "long",
                "description": "Number of objects allocated"
              },
              "percentOfTotal": {
                "type": "double",
                "description": "Percentage of all allocations"
              }
            }
          }
        }
      }
    },
    "gc": {
      "type": "object",
      "properties": {
        "gcEventCount": {
          "type": "integer",
          "description": "Number of GC events"
        },
        "totalGcPauseMs": {
          "type": "long",
          "description": "Total pause time"
        },
        "avgGcPauseMs": {
          "type": "double",
          "description": "Average pause time"
        },
        "maxGcPauseMs": {
          "type": "double",
          "description": "Longest single pause"
        },
        "recentEvents": {
          "type": "array",
          "description": "Last 10 GC events",
          "items": {
            "type": "object",
            "properties": {
              "timestamp": {
                "type": "string (ISO-8601)",
                "description": "When GC occurred"
              },
              "gcType": {
                "type": "string",
                "enum": ["Young", "Old", "Full", "Mixed"],
                "description": "Type of GC"
              },
              "pauseMs": {
                "type": "long",
                "description": "Pause time"
              },
              "freedBytes": {
                "type": "long",
                "description": "Memory freed by this GC"
              },
              "heapBeforeBytes": {
                "type": "long",
                "description": "Heap before GC"
              },
              "heapAfterBytes": {
                "type": "long",
                "description": "Heap after GC"
              }
            }
          }
        }
      }
    },
    "threads": {
      "type": "object",
      "properties": {
        "peakThreadCount": {
          "type": "integer",
          "description": "Peak thread count during recording"
        },
        "currentThreadCount": {
          "type": "integer",
          "description": "Thread count at end of recording"
        },
        "topThreads": {
          "type": "array",
          "description": "Top-10 threads by CPU time",
          "items": {
            "type": "object",
            "properties": {
              "threadName": {
                "type": "string",
                "description": "Thread name"
              },
              "threadId": {
                "type": "long",
                "description": "Thread ID"
              },
              "state": {
                "type": "string",
                "enum": ["RUNNABLE", "WAITING", "TIMED_WAITING", "BLOCKED"],
                "description": "Thread state at end of recording"
              },
              "cpuTimeMs": {
                "type": "long",
                "description": "CPU time spent in this thread"
              },
              "topStackFrame": {
                "type": "string",
                "description": "Most common method on stack"
              }
            }
          }
        }
      }
    },
    "locks": {
      "type": "object",
      "properties": {
        "contentionEventCount": {
          "type": "integer",
          "description": "Total lock contention events"
        },
        "topContended": {
          "type": "array",
          "description": "Top-5 contended locks",
          "items": {
            "type": "object",
            "properties": {
              "lockClass": {
                "type": "string",
                "description": "Class holding the lock"
              },
              "contentionCount": {
                "type": "integer",
                "description": "Number of contention events"
              },
              "totalWaitMs": {
                "type": "long",
                "description": "Total time waiting for this lock"
              },
              "avgWaitMs": {
                "type": "double",
                "description": "Average wait time per event"
              }
            }
          }
        }
      }
    },
    "jvm": {
      "type": "object",
      "properties": {
        "jvmName": {
          "type": "string",
          "description": "JVM name (e.g., 'OpenJDK 64-Bit Server VM')"
        },
        "jvmVersion": {
          "type": "string",
          "description": "JVM version (e.g., '21.0.2')"
        },
        "vmVendor": {
          "type": "string",
          "description": "Vendor (e.g., 'Oracle Corporation')"
        },
        "vmStartTime": {
          "type": "string (ISO-8601)",
          "description": "When JVM started"
        },
        "uptimeMs": {
          "type": "long",
          "description": "JVM uptime (milliseconds)"
        }
      }
    }
  },
  "required": ["schemaVersion", "podName", "recordingId", "reportGeneratedAt", "summary"]
}
```

### Error Codes

| Code | HTTP Status | Meaning | Mitigation |
|------|-------------|---------|-----------|
| `RECORDING_NOT_FOUND` | 404 | Recording ID does not exist | Verify recording ID |
| `RECORDING_NOT_READY` | 409 | Analysis not complete yet | Poll `get_recording` until status=READY |
| `ANALYSIS_FAILED` | 500 | Analysis encountered an error | Check logs; retry |
| `INTERNAL_ERROR` | 500 | Server error | Check logs; retry |

### Example Request

```json
{
  "jsonrpc": "2.0",
  "id": 7,
  "method": "tools/call",
  "params": {
    "name": "get_recording_report",
    "arguments": {
      "recording_id": "rec-2025-01-15-143200-abc123"
    }
  }
}
```

### Example Response (Partial)

```json
{
  "jsonrpc": "2.0",
  "id": 7,
  "result": {
    "schemaVersion": "1.0",
    "podName": "myapp-0",
    "recordingId": "rec-2025-01-15-143200-abc123",
    "reportGeneratedAt": "2025-01-15T14:40:00Z",
    "summary": {
      "profilingDuration": "5m 30s",
      "totalSamples": 11000,
      "cpuUsagePercent": 42.5,
      "heapUsagePercent": 75.0,
      "gcEventCount": 3,
      "topHotspot": "com.myapp.processor.ProcessorService.process",
      "observations": [
        "CPU usage is moderate but concentrated in request processing",
        "Heap is at 75% — consider monitoring after deployment",
        "GC events are infrequent but pauses are up to 250ms",
        "Lock contention observed on OrderService synchronization"
      ]
    },
    "cpu": {
      "totalCpuTimeMs": 135000,
      "topFrames": [
        {
          "frameSignature": "com.myapp.processor.ProcessorService.process",
          "sampleCount": 4200,
          "percentOfTotal": 38.2,
          "frameType": "JAVA_COMPILED"
        },
        {
          "frameSignature": "java.util.HashMap.get",
          "sampleCount": 2100,
          "percentOfTotal": 19.1,
          "frameType": "JAVA_COMPILED"
        }
      ],
      "cpuByThread": {
        "main": 45.2,
        "worker-1": 32.1,
        "worker-2": 28.9
      }
    },
    "memory": {
      "heapUsedBytes": 1610612736,
      "heapMaxBytes": 2147483648,
      "nonHeapUsedBytes": 134217728,
      "heapUsagePercent": 75.0,
      "topAllocators": [
        {
          "allocatorClass": "java.util.HashMap",
          "bytesAllocated": 536870912,
          "objectCount": 2000000,
          "percentOfTotal": 45.3
        }
      ]
    },
    "gc": {
      "gcEventCount": 3,
      "totalGcPauseMs": 450,
      "avgGcPauseMs": 150.0,
      "maxGcPauseMs": 250.0,
      "recentEvents": [
        {
          "timestamp": "2025-01-15T14:35:12Z",
          "gcType": "Young",
          "pauseMs": 120,
          "freedBytes": 268435456,
          "heapBeforeBytes": 1879048192,
          "heapAfterBytes": 1610612736
        }
      ]
    },
    "threads": {
      "peakThreadCount": 64,
      "currentThreadCount": 42,
      "topThreads": [
        {
          "threadName": "worker-1",
          "threadId": 24,
          "state": "RUNNABLE",
          "cpuTimeMs": 25000,
          "topStackFrame": "com.myapp.processor.ProcessorService.process"
        }
      ]
    },
    "locks": {
      "contentionEventCount": 127,
      "topContended": [
        {
          "lockClass": "com.myapp.service.OrderService",
          "contentionCount": 45,
          "totalWaitMs": 3500,
          "avgWaitMs": 77.8
        }
      ]
    },
    "jvm": {
      "jvmName": "OpenJDK 64-Bit Server VM",
      "jvmVersion": "21.0.2",
      "vmVendor": "Oracle Corporation",
      "vmStartTime": "2025-01-10T08:00:00Z",
      "uptimeMs": 432000000
    }
  }
}
```

---

## 8. `get_flame_graph`

**Retrieve flame graph (SVG or JSON).**

### Description

Return flame-graph data for a completed recording. Two output formats:
- **`svg`**: Interactive HTML/SVG page suitable for viewing in a browser
- **`json`**: JSON hotspot list consumable directly by Causa for reasoning

### Purpose

- **When:** Need visual representation or programmatic hotspot analysis
- **Who calls:** Causa Engine (AI reasoning) or human developer (visual inspection)
- **Frequency:** Once or twice per recording

### Input Schema

```json
{
  "recording_id": "string (required)",
  "format": "string (optional, default: 'json')"
}
```

| Parameter | Type | Required | Description | Example |
|-----------|------|----------|-------------|---------|
| `recording_id` | `string` | Yes | The recording ID | `"rec-2025-01-15-143200-abc123"` |
| `format` | `string` | No | Output format: `svg` or `json`. Default `json`. | `"json"` |

### Output Schema

**For `format=json`:**

```json
{
  "type": "object",
  "properties": {
    "recordingId": {
      "type": "string"
    },
    "format": {
      "type": "string",
      "enum": ["json"]
    },
    "frames": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "signature": {
            "type": "string",
            "description": "Fully qualified method name"
          },
          "samples": {
            "type": "integer",
            "description": "Number of samples in which this frame appeared"
          },
          "percent": {
            "type": "double",
            "description": "Percentage of total samples (0.0–100.0)"
          },
          "children": {
            "type": "array",
            "description": "Callees (methods called from this frame)",
            "items": {
              "type": "object"
            }
          }
        }
      }
    }
  }
}
```

**For `format=svg`:**

```
[HTML/SVG content as raw string]
```

A self-contained interactive SVG/HTML page suitable for opening in a browser. No external dependencies required.

### Error Codes

| Code | HTTP Status | Meaning | Mitigation |
|------|-------------|---------|-----------|
| `RECORDING_NOT_FOUND` | 404 | Recording ID does not exist | Verify recording ID |
| `RECORDING_NOT_READY` | 409 | Analysis not complete yet | Poll `get_recording` until status=READY |
| `INVALID_FORMAT` | 400 | Format is not 'svg' or 'json' | Use 'svg' or 'json' |
| `INTERNAL_ERROR` | 500 | Server error | Check logs; retry |

### Example Request (JSON Format)

```json
{
  "jsonrpc": "2.0",
  "id": 8,
  "method": "tools/call",
  "params": {
    "name": "get_flame_graph",
    "arguments": {
      "recording_id": "rec-2025-01-15-143200-abc123",
      "format": "json"
    }
  }
}
```

### Example Response (JSON Format)

```json
{
  "jsonrpc": "2.0",
  "id": 8,
  "result": {
    "recordingId": "rec-2025-01-15-143200-abc123",
    "format": "json",
    "frames": [
      {
        "signature": "java.lang.Thread.run",
        "samples": 11000,
        "percent": 100.0,
        "children": [
          {
            "signature": "com.myapp.processor.ProcessorService.process",
            "samples": 4200,
            "percent": 38.2,
            "children": [
              {
                "signature": "com.myapp.data.Repository.query",
                "samples": 2800,
                "percent": 25.5,
                "children": [
                  {
                    "signature": "java.sql.ResultSet.next",
                    "samples": 1500,
                    "percent": 13.6,
                    "children": []
                  }
                ]
              }
            ]
          },
          {
            "signature": "java.util.HashMap.get",
            "samples": 2100,
            "percent": 19.1,
            "children": []
          }
        ]
      }
    ]
  }
}
```

### Example Request (SVG Format)

```json
{
  "jsonrpc": "2.0",
  "id": 9,
  "method": "tools/call",
  "params": {
    "name": "get_flame_graph",
    "arguments": {
      "recording_id": "rec-2025-01-15-143200-abc123",
      "format": "svg"
    }
  }
}
```

### Example Response (SVG Format)

```json
{
  "jsonrpc": "2.0",
  "id": 9,
  "result": "<html>...[self-contained SVG/HTML page]...</html>"
}
```

---

## Shared Type Definitions

### RecordingMetadata

All recording-related operations return or reference this object:

```json
{
  "recordingId": "string",
  "podName": "string",
  "status": "enum: QUEUED | RECORDING | DOWNLOADING | ANALYSING | READY | DELIVERED | EXPIRED | ERROR",
  "terminationReason": "enum | null: USER_REQUESTED | DURATION_EXPIRED | CONTAINER_OOM | POD_DELETED | ERROR",
  "createdAt": "ISO-8601",
  "terminatedAt": "ISO-8601 | null",
  "durationSeconds": "long",
  "sizeBytes": "long | null",
  "profileType": "string: CPU | MEMORY | LOCK | ALLOCATION | ALL",
  "expiresAt": "ISO-8601"
}
```

### ProfiledPodMetadata

Pod discovery object:

```json
{
  "podName": "string",
  "namespace": "string",
  "profilerStatus": "enum: READY | NOT_READY | UNKNOWN",
  "jvmVersion": "string | null",
  "profilerVersion": "string | null",
  "lastProfiledAt": "ISO-8601 | null",
  "latestRecordingId": "string | null"
}
```

### ProfilingError

Standard error envelope used in all error responses:

```json
{
  "errorCode": "string (e.g., 'POD_NOT_FOUND')",
  "message": "string (human-readable message)",
  "details": "string | null (additional context)",
  "timestamp": "ISO-8601 (when error occurred)"
}
```

---

## Polling & State Machine Guide

### Recording Lifecycle

Typical workflow:

1. **Alert fires** (e.g., heap > 75%)
2. **Causa calls `start_recording`** → returns `recording_id`, status=QUEUED
3. **Causa polls `get_recording` every 10–15 s**
   - QUEUED → RECORDING (sidecar started profiling)
   - RECORDING → DOWNLOADING (duration expired or manually stopped)
   - DOWNLOADING → ANALYSING (JFR file retrieved, analysis started)
   - ANALYSING → READY (analysis complete)
4. **Causa calls `get_recording_report`** → receives full structured report
5. **(Optional) Causa calls `get_flame_graph`** → receives flame graph for visualization

### Error Handling

- If status transitions to ERROR, check `terminationReason` for details
- Possible early termination reasons: CONTAINER_OOM, POD_DELETED
- If CONTAINER_OOM occurs, the pod may have crashed; check pod logs

---

## Backwards Compatibility

**Schema Version: 1.0**

- Added fields are always nullable and non-required
- Removed fields will trigger a major version bump (2.0)
- Minor additions within 1.x are guaranteed safe

---

## Summary

This contract defines all 8 MCP tools, their inputs, outputs, error codes, and example payloads. Both Causa Engine and the Profiling Controller team should use this as the source of truth for integration.

**Questions?** Reach out to the Platform team.
