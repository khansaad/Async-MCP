package io.causa.profiler.service;

import io.causa.profiler.dto.Dtos.*;

import java.util.List;

/**
 * Contract between the MCP tool layer and the Profiling Controller backend.
 *
 * The controller is responsible for:
 *   - Sidecar injection lifecycle
 *   - Attaching to the target JVM (SYS_PTRACE or jcmd fallback)
 *   - Driving async-profiler to generate a JFR recording
 *   - Downloading the JFR file from the pod
 *   - Parsing and analysing the JFR file
 *   - Storing the resulting report and serving it through the REST API below
 *
 * The MCP server only calls this interface; it knows nothing about async-profiler
 * or JFR internals.
 */
public interface ProfilerService {

    // ── Pod discovery ──────────────────────────────────────────────────────────

    /**
     * Return all pods that have an async-profiler sidecar available.
     *
     * <p>MCP tool: {@code list_profiled_pods}
     */
    List<ProfiledPodMetadata> listProfiledPods();

    /**
     * Return JVM health and profiler readiness for a single pod.
     *
     * <p>MCP tool: {@code get_pod_jvm_status}
     *
     * @param podName   Kubernetes pod name
     * @param namespace Kubernetes namespace (null → use configured default)
     */
    PodJvmStatus getPodJvmStatus(String podName, String namespace);

    /**
     * Return a live JVM statistics snapshot.  Does not require a recording.
     *
     * <p>MCP tool: {@code get_jvm_statistics}
     *
     * @param podName Kubernetes pod name
     */
    JvmStatistics getJvmStatistics(String podName);

    // ── Recording lifecycle ────────────────────────────────────────────────────

    /**
     * Start a JFR recording on the target pod.
     *
     * <p>MCP tool: {@code start_recording}
     *
     * @param podName         Kubernetes pod name
     * @param durationSeconds Profiling duration. 0 means "run until explicitly stopped".
     *                        Default 300 (5 min).
     * @param profileType     CPU | MEMORY | LOCK | ALLOCATION | ALL
     * @return Recording metadata including the {@code recordingId} to use in subsequent calls
     */
    RecordingMetadata startRecording(String podName, int durationSeconds, String profileType);

    /**
     * Stop an in-progress recording early.
     *
     * <p>MCP tool: {@code stop_recording}
     *
     * @param recordingId The recording ID returned by {@link #startRecording}
     * @return Updated recording metadata
     */
    RecordingMetadata stopRecording(String recordingId);

    /**
     * Poll the current status of a recording.
     *
     * <p>MCP tool: {@code get_recording}
     *
     * @param recordingId The recording ID
     * @return Current recording metadata including status
     */
    RecordingMetadata getRecording(String recordingId);

    // ── Analysis ───────────────────────────────────────────────────────────────

    /**
     * Return the structured profiling report for a completed recording.
     *
     * <p>MCP tool: {@code get_recording_report}
     *
     * @param recordingId The recording ID
     * @return Full structured report: CPU hotspots, memory sites, GC pauses, thread breakdown
     */
    ProfilingReport getRecordingReport(String recordingId);

    /**
     * Return flame-graph data for a completed recording.
     *
     * <p>MCP tool: {@code get_flame_graph}
     *
     * @param recordingId The recording ID
     * @param format      "svg" for an interactive HTML page, "json" for a hotspot tree
     * @return Flame-graph content as a string (SVG XML or JSON)
     */
    String getFlameGraph(String recordingId, String format);
}
