# Async Profiler MCP Server — Integration Reference

---

## Server Details

| Property | Value |
|---|---|
| **Image** | `quay.io/khansaad/async-profiler-mcp-server:0.0.1` |
| **Architectures** | `amd64`, `arm64` |
| **Base** | `eclipse-temurin:21-jre` |
| **Transports** | stdio (default) · SSE (`/mcp/sse`) · Streamable HTTP (`/mcp`) |
| **Port** | `8080` (OpenShift) · `8083` (Minikube, to avoid conflict) |
| **Java** | 21 |
| **Framework** | Quarkus 3.36.1 + `quarkus-mcp-server-stdio` 1.1.0 |

---

## Transport Modes

The server ships with **stdio enabled by default** (suitable for AI agents that spawn it as a subprocess).  
To switch to **HTTP / SSE mode** for network deployment, set:

```
QUARKUS_MCP_SERVER_STDIO_ENABLED=false
QUARKUS_HTTP_HOST_ENABLED=true
```

| Mode | When to use | Endpoint |
|---|---|---|
| **stdio** (default) | AI agent spawns the server as a child process | stdin / stdout |
| **SSE** | Network deployment; Causa connects over HTTP | `http://<host>:<port>/mcp/sse` |
| **Streamable HTTP** | Network deployment; single HTTP request/response | `http://<host>:<port>/mcp` |

---

## Probes

TCP socket on port `8080` (OpenShift) or `8083` (Minikube).

```yaml
livenessProbe:
  tcpSocket:
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 15
readinessProbe:
  tcpSocket:
    port: 8080
  initialDelaySeconds: 5
  periodSeconds: 10
```

---

## Port-forward

**OpenShift:**
```bash
kubectl port-forward deployment/async-profiler-mcp-server 8080:8080 -n <namespace>
```

**Minikube:**
```bash
kubectl port-forward service/async-profiler-mcp-server-service 8083:8083 -n monitoring
```

---

## Required Environment Variables

These **must** be set in the manifest before deployment:

| Variable | Required | Description | Example |
|---|---|---|---|
| `PROFILER_CONTROLLER_URL` | **Yes** | Base URL of the Profiling Controller backend — the service that owns JFR recording and analysis | `http://profiling-controller.profiling:8081` |
| `PROFILER_DEFAULT_NAMESPACE` | No | Fallback Kubernetes namespace used when a caller omits `namespace`. Defaults to `default` | `production` |
| `QUARKUS_HTTP_PORT` | No | Override the HTTP port. Only needed on Minikube to avoid conflicts. Defaults to `8080` | `8083` |

> **`PROFILER_CONTROLLER_URL` is required.** The MCP server is a thin proxy — every tool call is forwarded to this URL. Without it the server will start but all tool calls will fail with a connection error.

### How to set in manifests

**OpenShift** — [`manifests/async-profiler-mcp-server-openshift.yaml`](manifests/async-profiler-mcp-server-openshift.yaml):
```yaml
env:
  - name: PROFILER_CONTROLLER_URL
    value: "http://profiling-controller.profiling:8081"   # ← REPLACE with actual controller service DNS
  - name: PROFILER_DEFAULT_NAMESPACE
    value: "default"                                       # ← REPLACE if your pods are in a different namespace
```

**Minikube** — [`manifests/async-profiler-mcp-server-minikube.yaml`](manifests/async-profiler-mcp-server-minikube.yaml):
```yaml
env:
  - name: PROFILER_CONTROLLER_URL
    value: "http://<minikube-ip>:<controller-port>"        # ← REPLACE with minikube IP + controller NodePort
  - name: PROFILER_DEFAULT_NAMESPACE
    value: "default"
  - name: QUARKUS_HTTP_PORT
    value: "8083"
```

---

## MCP Tools Exposed (8 tools)

| Tool | Arguments | Description |
|---|---|---|
| `list_profiled_pods` | `namespace` (optional) | Discover pods with profiler sidecar; returns `latestRecordingId` per pod |
| `get_pod_jvm_status` | `pod_name`, `namespace` (optional) | Sidecar health + latest recording metadata |
| `get_jvm_statistics` | `pod_name` | Live JVM snapshot: heap, threads, GC — no recording needed |
| `start_recording` | `pod_name`, `duration_seconds` (optional), `profile_type` (optional) | Trigger a JFR recording; returns `recording_id` |
| `stop_recording` | `recording_id` | Stop a recording early |
| `get_recording` | `recording_id` | Poll recording status (`QUEUED→RECORDING→DOWNLOADING→ANALYSING→READY`) |
| `get_recording_report` | `recording_id` | Full structured analysis report (call after status=READY) |
| `get_flame_graph` | `recording_id`, `format` (`json`\|`svg`, optional, default `json`) | Flame graph as JSON tree or interactive SVG |

---

## Connect with MCP Inspector

```bash
# Install once
npm install -g @modelcontextprotocol/inspector

# Connect (after port-forward or in-cluster)
npx @modelcontextprotocol/inspector http://localhost:8080/mcp/
```
