package io.causa.profiler.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Shared DTOs that form the contract between the MCP tool layer and
 * the Profiling Controller backend.  The controller implements the REST
 * endpoints; this MCP server consumes them and exposes the data as tools.
 *
 * Schema version: 1.0
 */
public final class Dtos {
    private Dtos() {}

    // ──────────────────────────────────────────────
    // Recording lifecycle enums
    // ──────────────────────────────────────────────

    public enum RecordingStatus {
        /** Waiting for a profiler slot. */
        QUEUED,
        /** async-profiler is actively recording. */
        RECORDING,
        /** JFR file is being copied out of the pod. */
        DOWNLOADING,
        /** JFR file is being parsed and analysed. */
        ANALYSING,
        /** Analysis is complete; report is available. */
        READY,
        /** Report has been retrieved at least once. */
        DELIVERED,
        /** Exceeded the retention window. */
        EXPIRED,
        /** Something went wrong. */
        ERROR
    }

    public enum TerminationReason {
        /** Causa called stop_recording(). */
        USER_REQUESTED,
        /** Hit the configured duration limit (default 5 min). */
        DURATION_EXPIRED,
        /** The target container ran out of memory. */
        CONTAINER_OOM,
        /** The pod was deleted or evicted. */
        POD_DELETED,
        /** Recording or download failed. */
        ERROR
    }

    // ──────────────────────────────────────────────
    // Pod discovery
    // ──────────────────────────────────────────────

    @RegisterForReflection
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProfiledPodMetadata {
        public String podName;
        public String namespace;
        /** One of: READY, NOT_READY, UNKNOWN */
        public String profilerStatus;
        public String jvmVersion;
        public String profilerVersion;
        public Instant lastProfiledAt;
        public String latestRecordingId;

        public ProfiledPodMetadata() {}
    }

    @RegisterForReflection
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PodJvmStatus {
        public String podName;
        /** One of: READY, NOT_READY, UNKNOWN */
        public String profilerStatus;
        public JvmHealth jvmHealth;
        public RecordingMetadata latestRecording;
        public String message;

        @RegisterForReflection
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class JvmHealth {
            public boolean isHealthy;
            public String jvmUptime;
            public int activeThreadCount;
            public Instant lastHeartbeat;
        }
    }

    // ──────────────────────────────────────────────
    // Recording lifecycle
    // ──────────────────────────────────────────────

    @RegisterForReflection
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RecordingMetadata {
        public String recordingId;
        public String podName;
        public RecordingStatus status;
        /** Populated only after recording ends; null while still QUEUED/RECORDING. */
        public TerminationReason terminationReason;
        public Instant createdAt;
        /** Null while the recording is still active (QUEUED / RECORDING). */
        public Instant terminatedAt;
        public long durationSeconds;
        /** Null until the JFR file is fully downloaded; zero-valued fields are hidden via NON_NULL. */
        public Long sizeBytes;
        /** e.g. CPU, MEMORY, LOCK, ALLOCATION, ALL */
        public String profileType;
        /** When this record expires from the controller's store. */
        public Instant expiresAt;
    }

    @RegisterForReflection
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StartRecordingRequest {
        /** Profiling duration in seconds.  0 means "run until stop is called". */
        public int durationSeconds = 300;
        /** CPU | MEMORY | LOCK | ALLOCATION | ALL */
        public String profileType = "CPU";
    }

    // ──────────────────────────────────────────────
    // Analysis report
    // ──────────────────────────────────────────────

    @RegisterForReflection
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProfilingReport {
        public String schemaVersion = "1.0";
        public String podName;
        public String recordingId;
        public Instant reportGeneratedAt;
        public SummarySection summary;
        public CpuSection cpu;
        public MemorySection memory;
        public GcSection gc;
        public ThreadsSection threads;
        public LocksSection locks;
        public JvmSection jvm;

        @RegisterForReflection
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class SummarySection {
            public String profilingDuration;
            public int totalSamples;
            public double cpuUsagePercent;
            public double heapUsagePercent;
            public int gcEventCount;
            public String topHotspot;
            /** Human-readable observations the controller generated, ready for Causa to reason over. */
            public List<String> observations;
        }

        @RegisterForReflection
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class CpuSection {
            public double totalCpuTimeMs;
            /** Top-5 CPU hotspot methods by sample count. */
            public List<HotFrame> topFrames;
            public Map<String, Double> cpuByThread;

            @RegisterForReflection
            @JsonInclude(JsonInclude.Include.NON_NULL)
            public static class HotFrame {
                public String frameSignature;
                public int sampleCount;
                public double percentOfTotal;
                /** KERNEL | NATIVE | CPP | JAVA_COMPILED | INLINED | INTERPRETED */
                public String frameType;
            }
        }

        @RegisterForReflection
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class MemorySection {
            public long heapUsedBytes;
            public long heapMaxBytes;
            public long nonHeapUsedBytes;
            public double heapUsagePercent;
            /** Top-5 memory allocation sites by bytes. */
            public List<AllocationSite> topAllocators;

            @RegisterForReflection
            @JsonInclude(JsonInclude.Include.NON_NULL)
            public static class AllocationSite {
                public String allocatorClass;
                public long bytesAllocated;
                public long objectCount;
                public double percentOfTotal;
            }
        }

        @RegisterForReflection
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class GcSection {
            public int gcEventCount;
            public long totalGcPauseMs;
            public double avgGcPauseMs;
            public double maxGcPauseMs;
            public List<GcEvent> recentEvents;

            @RegisterForReflection
            @JsonInclude(JsonInclude.Include.NON_NULL)
            public static class GcEvent {
                public Instant timestamp;
                /** Young | Old | Full | Mixed */
                public String gcType;
                public long pauseMs;
                public long freedBytes;
                public long heapBeforeBytes;
                public long heapAfterBytes;
            }
        }

        @RegisterForReflection
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class ThreadsSection {
            public int peakThreadCount;
            public int currentThreadCount;
            public List<ThreadSnapshot> topThreads;

            @RegisterForReflection
            @JsonInclude(JsonInclude.Include.NON_NULL)
            public static class ThreadSnapshot {
                public String threadName;
                public long threadId;
                /** RUNNABLE | WAITING | TIMED_WAITING | BLOCKED */
                public String state;
                public long cpuTimeMs;
                public String topStackFrame;
            }
        }

        @RegisterForReflection
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class LocksSection {
            public int contentionEventCount;
            public List<LockContention> topContended;

            @RegisterForReflection
            @JsonInclude(JsonInclude.Include.NON_NULL)
            public static class LockContention {
                public String lockClass;
                public int contentionCount;
                public long totalWaitMs;
                public double avgWaitMs;
            }
        }

        @RegisterForReflection
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class JvmSection {
            public String jvmName;
            public String jvmVersion;
            public String vmVendor;
            public Instant vmStartTime;
            public long uptimeMs;
        }
    }

    // ──────────────────────────────────────────────
    // Live JVM stats (no recording needed)
    // ──────────────────────────────────────────────

    @RegisterForReflection
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class JvmStatistics {
        public String podName;
        public Instant capturedAt;
        public long heapUsedBytes;
        public long heapMaxBytes;
        public long nonHeapUsedBytes;
        public double heapUsagePercent;
        public int threadCount;
        public int peakThreadCount;
        public long gcCollectionCount;
        public long gcCollectionTimeMs;
        public int loadedClassCount;
    }

    // ──────────────────────────────────────────────
    // Error envelope
    // ──────────────────────────────────────────────

    @RegisterForReflection
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProfilingError {
        public String errorCode;
        public String message;
        public String details;
        public Instant timestamp = Instant.now();

        public ProfilingError() {}

        public ProfilingError(String errorCode, String message) {
            this.errorCode = errorCode;
            this.message = message;
        }

        public ProfilingError(String errorCode, String message, String details) {
            this(errorCode, message);
            this.details = details;
        }
    }
}
