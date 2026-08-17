package io.causa.profiler.mcp;

import io.causa.profiler.dto.Dtos.*;
import io.causa.profiler.service.ProfilerService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for all 8 MCP tools.
 *
 * ProfilerService is mocked — the tests verify that:
 *   a) the correct service method is called with the right arguments, and
 *   b) the tool returns the value from the service unchanged.
 *
 * Tool-name → Java-method mapping (per contract):
 *   list_profiled_pods   → listProfiledPods
 *   get_pod_jvm_status   → getPodJvmStatus
 *   get_jvm_statistics   → getJvmStatistics
 *   start_recording      → startRecording
 *   stop_recording       → stopRecording
 *   get_recording        → getRecording
 *   get_recording_report → getRecordingReport
 *   get_flame_graph      → getFlameGraph
 */
@QuarkusTest
class ProfilingToolsTest {

    @InjectMock
    ProfilerService profilerService;

    // ProfilingTools is a CDI bean; Quarkus injects the mock automatically.
    @jakarta.inject.Inject
    ProfilingTools tools;

    // ── 1. list_profiled_pods ──────────────────────────────────────────────────

    @Test
    void listProfiledPods_returnsDelegateResult() {
        var pod = new ProfiledPodMetadata();
        pod.podName = "myapp-0";
        pod.namespace = "default";
        pod.profilerStatus = "READY";
        when(profilerService.listProfiledPods()).thenReturn(List.of(pod));

        var result = tools.listProfiledPods(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).podName).isEqualTo("myapp-0");
        verify(profilerService).listProfiledPods();
    }

    // ── 2. get_pod_jvm_status ──────────────────────────────────────────────────

    @Test
    void getPodJvmStatus_delegatesCorrectly() {
        var status = new PodJvmStatus();
        status.podName = "myapp-0";
        status.profilerStatus = "READY";
        when(profilerService.getPodJvmStatus("myapp-0", "default")).thenReturn(status);

        var result = tools.getPodJvmStatus("myapp-0", "default");

        assertThat(result.podName).isEqualTo("myapp-0");
        assertThat(result.profilerStatus).isEqualTo("READY");
    }

    // ── 3. get_jvm_statistics ──────────────────────────────────────────────────

    @Test
    void getJvmStatistics_returnsHeapInfo() {
        var stats = new JvmStatistics();
        stats.podName = "myapp-0";
        stats.heapUsedBytes    = 600_000_000L;
        stats.heapMaxBytes     = 1_024_000_000L;
        stats.heapUsagePercent = 58.6;
        when(profilerService.getJvmStatistics("myapp-0")).thenReturn(stats);

        var result = tools.getJvmStatistics("myapp-0");

        assertThat(result.heapUsagePercent).isEqualTo(58.6);
        verify(profilerService).getJvmStatistics("myapp-0");
    }

    // ── 4. start_recording ─────────────────────────────────────────────────────

    @Test
    void startRecording_usesDefaultDurationAndTypeWhenNull() {
        var meta = recordingMeta("rec-001", RecordingStatus.RECORDING);
        when(profilerService.startRecording("myapp-0", 300, "CPU")).thenReturn(meta);

        var result = tools.startRecording("myapp-0", null, null);

        assertThat(result.recordingId).isEqualTo("rec-001");
        assertThat(result.status).isEqualTo(RecordingStatus.RECORDING);
        verify(profilerService).startRecording("myapp-0", 300, "CPU");
    }

    @Test
    void startRecording_respectsExplicitDurationAndType() {
        var meta = recordingMeta("rec-002", RecordingStatus.RECORDING);
        when(profilerService.startRecording("myapp-0", 120, "MEMORY")).thenReturn(meta);

        tools.startRecording("myapp-0", 120, "MEMORY");

        verify(profilerService).startRecording("myapp-0", 120, "MEMORY");
    }

    @Test
    void startRecording_zeroDurationMeansRunUntilStop() {
        var meta = recordingMeta("rec-003", RecordingStatus.RECORDING);
        when(profilerService.startRecording("myapp-0", 0, "CPU")).thenReturn(meta);

        tools.startRecording("myapp-0", 0, null);

        verify(profilerService).startRecording("myapp-0", 0, "CPU");
    }

    // ── 5. stop_recording ──────────────────────────────────────────────────────

    @Test
    void stopRecording_setsTerminationReason() {
        var meta = recordingMeta("rec-001", RecordingStatus.DOWNLOADING);
        meta.terminationReason = TerminationReason.USER_REQUESTED;
        when(profilerService.stopRecording("rec-001")).thenReturn(meta);

        var result = tools.stopRecording("rec-001");

        assertThat(result.terminationReason).isEqualTo(TerminationReason.USER_REQUESTED);
        verify(profilerService).stopRecording("rec-001");
    }

    // ── 6. get_recording ──────────────────────────────────────────────────────

    @Test
    void getRecording_returnsAnalysingWhileProcessing() {
        var meta = recordingMeta("rec-001", RecordingStatus.ANALYSING);
        when(profilerService.getRecording("rec-001")).thenReturn(meta);

        var result = tools.getRecording("rec-001");

        assertThat(result.status).isEqualTo(RecordingStatus.ANALYSING);
    }

    @Test
    void getRecording_returnsReadyWhenDone() {
        var meta = recordingMeta("rec-001", RecordingStatus.READY);
        when(profilerService.getRecording("rec-001")).thenReturn(meta);

        var result = tools.getRecording("rec-001");

        assertThat(result.status).isEqualTo(RecordingStatus.READY);
    }

    @Test
    void getRecording_reflectsContainerOom() {
        var meta = recordingMeta("rec-001", RecordingStatus.ERROR);
        meta.terminationReason = TerminationReason.CONTAINER_OOM;
        when(profilerService.getRecording("rec-001")).thenReturn(meta);

        var result = tools.getRecording("rec-001");

        assertThat(result.terminationReason).isEqualTo(TerminationReason.CONTAINER_OOM);
    }

    // ── 7. get_recording_report ────────────────────────────────────────────────

    @Test
    void getRecordingReport_returnsFullReport() {
        var report = new ProfilingReport();
        report.podName = "myapp-0";
        report.recordingId = "rec-001";
        report.reportGeneratedAt = Instant.now();

        report.summary = new ProfilingReport.SummarySection();
        report.summary.cpuUsagePercent  = 87.3;
        report.summary.heapUsagePercent = 76.1;
        report.summary.gcEventCount     = 42;

        report.cpu = new ProfilingReport.CpuSection();
        var frame = new ProfilingReport.CpuSection.HotFrame();
        frame.frameSignature = "com.example.RetainedHeap.retain";
        frame.sampleCount    = 1200;
        frame.percentOfTotal = 34.5;
        report.cpu.topFrames = List.of(frame);

        when(profilerService.getRecordingReport("rec-001")).thenReturn(report);

        var result = tools.getRecordingReport("rec-001");

        assertThat(result.summary.cpuUsagePercent).isEqualTo(87.3);
        assertThat(result.cpu.topFrames).hasSize(1);
        assertThat(result.cpu.topFrames.get(0).frameSignature)
                .isEqualTo("com.example.RetainedHeap.retain");
    }

    // ── 8. get_flame_graph ─────────────────────────────────────────────────────

    @Test
    void getFlameGraph_defaultsToJson() {
        when(profilerService.getFlameGraph("rec-001", "json"))
                .thenReturn("{\"frames\":[]}");

        var result = tools.getFlameGraph("rec-001", null);

        assertThat(result).contains("frames");
        verify(profilerService).getFlameGraph("rec-001", "json");
    }

    @Test
    void getFlameGraph_returnsSvgWhenRequested() {
        when(profilerService.getFlameGraph("rec-001", "svg"))
                .thenReturn("<svg>...</svg>");

        var result = tools.getFlameGraph("rec-001", "svg");

        assertThat(result).startsWith("<svg>");
        verify(profilerService).getFlameGraph("rec-001", "svg");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private RecordingMetadata recordingMeta(String id, RecordingStatus status) {
        var m = new RecordingMetadata();
        m.recordingId = id;
        m.podName     = "myapp-0";
        m.status      = status;
        m.createdAt   = Instant.now();
        return m;
    }
}
