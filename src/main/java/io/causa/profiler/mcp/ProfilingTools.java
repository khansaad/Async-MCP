package io.causa.profiler.mcp;

import io.causa.profiler.dto.Dtos.*;
import io.causa.profiler.service.ProfilerService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * MCP tool definitions for the Async Profiler MCP Server.
 *
 * Every public method annotated with {@link Tool} becomes an MCP tool that Causa
 * can discover and call.  The Quarkus MCP extension handles JSON-RPC serialisation,
 * tool-list advertising, and transport (stdio / SSE).
 *
 * Tool inventory (8 tools):
 *   1. list_profiled_pods    – discover pods with an async-profiler sidecar
 *   2. get_pod_jvm_status    – profiler readiness + latest recording
 *   3. get_jvm_statistics    – live JVM snapshot (no recording needed)
 *   4. start_recording       – trigger a new recording (returns recording_id)
 *   5. stop_recording        – stop an in-progress recording early
 *   6. get_recording         – poll status until READY
 *   7. get_recording_report  – retrieve full structured analysis
 *   8. get_flame_graph       – retrieve SVG or JSON flame-graph
 */
@ApplicationScoped
public class ProfilingTools {

    @Inject
    ProfilerService profilerService;

    // ── 1. list_profiled_pods ─────────────────────────────────────────────────

    @Tool(name = "list_profiled_pods",
          description = """
              Discover all JVM pods that have an async-profiler sidecar ready for profiling.
              Returns pod name, namespace, JVM version, profiler status, and the ID of the
              most recent recording (if any).
              """)
    public List<ProfiledPodMetadata> listProfiledPods(
            @ToolArg(name = "namespace",
                     description = "Kubernetes namespace to filter by. Optional — omit to list all namespaces.",
                     required = false)
            String namespace) {

        Log.infof("[MCP] list_profiled_pods namespace=%s", namespace);
        return profilerService.listProfiledPods();
    }

    // ── 2. get_pod_jvm_status ─────────────────────────────────────────────────

    @Tool(name = "get_pod_jvm_status",
          description = """
              Return the profiler readiness and JVM health for a specific pod.
              Use this before starting a recording to confirm the sidecar is attached.
              Returns latestRecording (null if none) so callers can check recent activity
              without a separate call.
              """)
    public PodJvmStatus getPodJvmStatus(
            @ToolArg(name = "pod_name", description = "Kubernetes pod name.") String podName,
            @ToolArg(name = "namespace",
                     description = "Kubernetes namespace. Optional — omit to use the configured default.",
                     required = false)
            String namespace) {

        Log.infof("[MCP] get_pod_jvm_status pod=%s", podName);
        return profilerService.getPodJvmStatus(podName, namespace);
    }

    // ── 3. get_jvm_statistics ─────────────────────────────────────────────────

    @Tool(name = "get_jvm_statistics",
          description = """
              Return a live JVM statistics snapshot for a pod: heap usage, thread count,
              GC collection totals, and loaded class count.
              No recording is required — data is collected on demand via the sidecar.
              """)
    public JvmStatistics getJvmStatistics(
            @ToolArg(name = "pod_name", description = "Kubernetes pod name.") String podName) {

        Log.infof("[MCP] get_jvm_statistics pod=%s", podName);
        return profilerService.getJvmStatistics(podName);
    }

    // ── 4. start_recording ────────────────────────────────────────────────────

    @Tool(name = "start_recording",
          description = """
              Start a JFR recording on the target pod via async-profiler.
              Call this when a Prometheus alert fires (e.g. heap > 75%).

              Returns a recording_id immediately.  The recording runs for
              duration_seconds (default 300 / 5 min).  Pass duration_seconds=0
              to run indefinitely until stop_recording is called.

              Poll get_recording until status=READY, then call get_recording_report.
              """)
    public RecordingMetadata startRecording(
            @ToolArg(name = "pod_name", description = "Kubernetes pod name to profile.") String podName,
            @ToolArg(name = "duration_seconds",
                     description = "How long to record in seconds. Default 300 (5 min). Pass 0 to run until stop_recording is called.",
                     required = false)
            Integer durationSeconds,
            @ToolArg(name = "profile_type",
                     description = "What to profile: CPU | MEMORY | LOCK | ALLOCATION | ALL. Default CPU.",
                     required = false)
            String profileType) {

        int duration = (durationSeconds != null && durationSeconds >= 0) ? durationSeconds : 300;
        String type  = (profileType != null && !profileType.isBlank()) ? profileType : "CPU";

        Log.infof("[MCP] start_recording pod=%s duration=%d type=%s", podName, duration, type);
        return profilerService.startRecording(podName, duration, type);
    }

    // ── 5. stop_recording ─────────────────────────────────────────────────────

    @Tool(name = "stop_recording",
          description = """
              Stop an in-progress JFR recording before its configured duration expires.
              The controller finalises the JFR file and begins analysis immediately.
              The returned metadata will have terminationReason=USER_REQUESTED.

              Poll get_recording to track the transition to READY,
              then call get_recording_report.
              """)
    public RecordingMetadata stopRecording(
            @ToolArg(name = "recording_id",
                     description = "The recording_id returned by start_recording.")
            String recordingId) {

        Log.infof("[MCP] stop_recording id=%s", recordingId);
        return profilerService.stopRecording(recordingId);
    }

    // ── 6. get_recording ──────────────────────────────────────────────────────

    @Tool(name = "get_recording",
          description = """
              Poll the current status of a JFR recording.

              Status lifecycle:
                QUEUED → RECORDING → DOWNLOADING → ANALYSING → READY → DELIVERED

              Poll every 10–15 s after starting a recording.
              Call get_recording_report once status=READY.
              CONTAINER_OOM or POD_DELETED will appear in terminationReason if the
              pod was killed while profiling.
              """)
    public RecordingMetadata getRecording(
            @ToolArg(name = "recording_id",
                     description = "The recording_id returned by start_recording.")
            String recordingId) {

        Log.debugf("[MCP] get_recording id=%s", recordingId);
        return profilerService.getRecording(recordingId);
    }

    // ── 7. get_recording_report ───────────────────────────────────────────────

    @Tool(name = "get_recording_report",
          description = """
              Retrieve the full structured analysis report for a completed JFR recording.
              Only callable after get_recording returns status=READY.

              The report includes:
              • summary   — overall CPU/memory/GC numbers and human-readable observations
              • cpu       — top-5 CPU hotspot methods with sample counts
              • memory    — top-5 allocation sites by bytes allocated
              • gc        — GC pause summary and recent GC events
              • threads   — thread state breakdown and per-thread CPU time
              • locks     — top lock contention events
              • jvm       — JVM version, vendor, uptime
              """)
    public ProfilingReport getRecordingReport(
            @ToolArg(name = "recording_id",
                     description = "The recording_id returned by start_recording.")
            String recordingId) {

        Log.infof("[MCP] get_recording_report id=%s", recordingId);
        return profilerService.getRecordingReport(recordingId);
    }

    // ── 8. get_flame_graph ────────────────────────────────────────────────────

    @Tool(name = "get_flame_graph",
          description = """
              Return flame-graph data for a completed recording.

              format=svg  — returns a self-contained interactive HTML/SVG page
              format=json — returns a JSON tree of hotspot frames consumable
                            directly by Causa without rendering

              Prefer format=json when Causa needs to reason over the data.
              Prefer format=svg when a developer needs a visual.
              """)
    public String getFlameGraph(
            @ToolArg(name = "recording_id",
                     description = "The recording_id returned by start_recording.")
            String recordingId,
            @ToolArg(name = "format",
                     description = "Output format: 'svg' or 'json'. Default 'json'.",
                     required = false)
            String format) {

        String fmt = ("svg".equalsIgnoreCase(format)) ? "svg" : "json";
        Log.infof("[MCP] get_flame_graph id=%s format=%s", recordingId, fmt);
        return profilerService.getFlameGraph(recordingId, fmt);
    }
}
