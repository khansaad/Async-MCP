# async-profiler-mcp-server — Quick Start Guide

Get the async-profiler MCP Server running in minutes.

## Prerequisites

- Java 21
- Maven 3.9+ (or use the included `./mvnw`)
- Docker (for container builds)
- kubectl / oc (for Kubernetes / OpenShift deployment)
- Node.js + npm (for the MCP Inspector)
- **Profiling Controller running** (the backend service that owns sidecar injection and JFR analysis)

---

## Step 0: Start the Profiling Controller (if not already running)

The MCP server proxies all requests to the Profiling Controller. Ensure it is reachable before starting this server.

```bash
# Verify the controller is up (replace URL as needed)
curl http://localhost:8081/pods
```

---

## Local Development

### Build

```bash
./mvnw clean package -DskipTests
```

### Run

```bash
# Default — connects to controller on http://localhost:8081
PROFILER_CONTROLLER_URL=http://localhost:8081 \
  java -jar target/quarkus-app/quarkus-run.jar
```

Custom port (e.g. to avoid conflicts):

```bash
QUARKUS_HTTP_PORT=8083 \
PROFILER_CONTROLLER_URL=http://localhost:8081 \
  java -jar target/quarkus-app/quarkus-run.jar
```

### Connect MCP Inspector

```bash
# Install once
npm install -g @modelcontextprotocol/inspector

# Launch
npx @modelcontextprotocol/inspector http://localhost:8080/mcp/
```

In the browser:
1. Verify URL is `http://localhost:8080/mcp/`
2. Click **Connect**
3. Click **List Tools** — you should see all 8 tools:
   - `list_profiled_pods`
   - `get_pod_jvm_status`
   - `get_jvm_statistics`
   - `start_recording`
   - `stop_recording`
   - `get_recording`
   - `get_recording_report`
   - `get_flame_graph`

---

## Minikube Deployment

### Step 1: Build and push the image

```bash
./mvnw clean package -DskipTests
docker build -t <registry>/<your-org>/async-profiler-mcp-server:latest .
docker push <registry>/<your-org>/async-profiler-mcp-server:latest
```

### Step 2: Get the Profiling Controller URL

```bash
CONTROLLER_URL=$(echo "http://$(minikube ip):$(kubectl get svc profiling-controller -n monitoring -o jsonpath='{.spec.ports[0].nodePort}')")
echo "Controller URL: $CONTROLLER_URL"
```

### Step 3: Update the manifest and deploy

Edit [`manifests/async-profiler-mcp-server-minikube.yaml`](manifests/async-profiler-mcp-server-minikube.yaml) and replace `<minikube-ip>:<controller-port>` with the value above, then:

```bash
kubectl apply -f manifests/async-profiler-mcp-server-minikube.yaml
kubectl wait --for=condition=ready pod -l app=async-profiler-mcp-server -n monitoring --timeout=120s
```

### Step 4: Port-forward and connect Inspector

```bash
# Terminal 1
kubectl port-forward -n monitoring service/async-profiler-mcp-server-service 8083:8083

# Terminal 2
npx @modelcontextprotocol/inspector http://localhost:8083/mcp/
```

---

## OpenShift Deployment

### Step 1: Build and push the image

```bash
./mvnw clean package -DskipTests
docker build -t <registry>/<your-org>/async-profiler-mcp-server:latest .
docker push <registry>/<your-org>/async-profiler-mcp-server:latest
```

### Step 2: Deploy

```bash
oc apply -f manifests/async-profiler-mcp-server-openshift.yaml
oc expose service async-profiler-mcp-server-service
```

### Step 3: Get the route and connect Inspector

```bash
ROUTE=$(oc get route async-profiler-mcp-server-service --template='{{ .spec.host }}')
npx @modelcontextprotocol/inspector http://$ROUTE/mcp/
```

---

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `PROFILER_CONTROLLER_URL` | `http://localhost:8081` | Base URL of the Profiling Controller backend |
| `PROFILER_DEFAULT_NAMESPACE` | `default` | Fallback Kubernetes namespace when callers omit it |
| `QUARKUS_HTTP_PORT` | `8080` | HTTP port the MCP server listens on |

---

## Run Tests

```bash
./mvnw test
```
