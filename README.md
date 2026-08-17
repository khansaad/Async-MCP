# async-profiler-mcp-server

MCP server that gives Causa Engine remote control over JVM profiling.

## What it does

1. **Exposes 8 MCP tools** so Causa can trigger, monitor and retrieve JFR profiling data from any pod in the cluster.
2. **Delegates all complexity** to the Profiling Controller (separate service, separate repo) via a clean REST API contract.
3. **Zero knowledge of async-profiler** — the controller owns sidecar injection, JVM attachment, JFR generation, and report generation.

## Architecture

```
Prometheus Alert (heap > 75%)
        │
        ▼
Causa Engine
        │  MCP (stdio / SSE)
        ▼
async-profiler-mcp-server   ← this repo
        │  REST
        ▼
Profiling Controller  ← other repo
        │
        ├─ Sidecar injection
        ├─ async-profiler / jcmd
        ├─ JFR download from pod
        └─ JFR parse + report
```

## Documentation

📋 **For Causa Engine team:**
- **[MCP Tool Contract Specification](MCP_TOOL_CONTRACT_SPECIFICATION.md)** — Complete formal specification of all 8 tools with schemas, error codes, and examples
- **[MCP Tool Quick Reference](MCP_TOOL_QUICK_REFERENCE.md)** — Quick lookup guide with examples and polling strategy

🚀 **Getting started:**
- **[Quick Start Guide](QUICK_START.md)** — Local dev, Minikube, and OpenShift deployment instructions

## MCP Tools

| Tool                   | What it does                                                                                                 |
|------------------------|--------------------------------------------------------------------------------------------------------------|
| `list_profiled_pods`   | Discover pods with an async-profiler sidecar                                                                 |
| `get_pod_jvm_status`   | Check profiler readiness before starting a recording                                                         |
| `get_jvm_statistics`   | Live heap / thread / GC snapshot (no recording needed)                                                       |
| `start_recording`      | Trigger a recording (returns `recording_id`). Default 5 min. Pass `duration_seconds=0` to run until stopped. |
| `stop_recording`       | Stop an in-progress recording early (`terminationReason=USER_REQUESTED`)                                     |
| `get_recording`        | Poll status: `QUEUED → RECORDING → DOWNLOADING → ANALYSING → READY`                                          |
| `get_recording_report` | Retrieve full structured analysis (call after status=READY)                                                  |
| `get_flame_graph`      | Retrieve flame graph as SVG or JSON hotspot list                                                             |

## Typical Causa flow

```
1. Alert: heap at 78%
2. list_profiled_pods()           → find myapp-0
3. get_pod_jvm_status(myapp-0)   → profiler READY
4. start_jfr_recording(myapp-0, duration_seconds=300)
                                  → recording_id = "rec-abc"
5. poll get_recording_status(rec-abc) every 15 s
                                  → RECORDING … ANALYSING … READY
6. get_jfr_report(rec-abc)       → ProfilingReport JSON
   • summary.observations        → human-readable findings
   • cpu.topFrames                → top-5 hotspot methods
   • memory.topAllocators         → top-5 allocation sites
   • gc.recentEvents              → GC pause history
   • threads.topThreads           → per-thread CPU usage
7. (optionally) get_flame_graph(rec-abc, format=json)
8. Causa correlates with Quarkus metrics + K8s events → RCA
```

## Controller REST API contract

The Profiling Controller **must** implement these endpoints:

```
GET    /pods                                         → List<ProfiledPodMetadata>
GET    /pods/{pod}/status?namespace=                 → PodJvmStatus
GET    /pods/{pod}/stats                             → JvmStatistics
POST   /pods/{pod}/recordings                        → RecordingMetadata
DELETE /recordings/{id}                              → RecordingMetadata
GET    /recordings/{id}/status                       → RecordingMetadata
GET    /recordings/{id}/report                       → ProfilingReport
GET    /recordings/{id}/flamegraph?format=svg|json   → String
GET    /pods/{pod}/recordings?limit=                 → List<RecordingMetadata>
```

All request/response types are defined in [`Dtos.java`](src/main/java/io/causa/profiler/dto/Dtos.java).

## Configuration

```yaml
# src/main/resources/application.yml
quarkus:
  rest-client:
    profiler-controller:
      url: "http://profiling-controller:8081"   # or set PROFILER_CONTROLLER_URL env var

profiler:
  controller:
    default-namespace: "default"               # or set PROFILER_DEFAULT_NAMESPACE env var
```

## Building

```bash
./mvnw clean package -DskipTests
```

## Running (dev)

```bash
PROFILER_CONTROLLER_URL=http://localhost:8081 ./mvnw quarkus:dev
```

## Testing

```bash
./mvnw test
```

## Project layout

```
async-profiler-mcp-server/
├── Dockerfile
├── LICENSE
├── QUICK_START.md
├── mvnw / mvnw.cmd
├── .mvn/wrapper/maven-wrapper.properties
├── manifests/
│   ├── async-profiler-mcp-server-minikube.yaml
│   └── async-profiler-mcp-server-openshift.yaml
├── pom.xml
└── src/
    ├── main/
    │   ├── java/io/causa/profiler/
    │   │   ├── dto/
    │   │   │   └── Dtos.java               ← shared DTO contract (enums + records)
    │   │   ├── mcp/
    │   │   │   └── ProfilingTools.java     ← all 8 @Tool methods
    │   │   └── service/
    │   │       ├── ProfilerService.java    ← interface (controller contract)
    │   │       └── ProfilerServiceClient.java ← MicroProfile REST client impl
    │   └── resources/
    │       └── application.yml
    └── test/
        └── java/io/causa/profiler/mcp/
            └── ProfilingToolsTest.java     ← unit tests (8 tools, mocked service)
```
