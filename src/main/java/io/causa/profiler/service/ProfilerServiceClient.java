package io.causa.profiler.service;

import io.causa.profiler.dto.Dtos.*;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Objects;

/**
 * REST client that talks to the Profiling Controller backend.
 *
 * The controller REST API contract:
 *
 *   GET    /pods                                          → List<ProfiledPodMetadata>
 *   GET    /pods/{pod}/status?namespace=                 → PodJvmStatus
 *   GET    /pods/{pod}/stats                             → JvmStatistics
 *   POST   /pods/{pod}/recordings                        → RecordingMetadata   (body: StartRecordingRequest)
 *   DELETE /recordings/{id}                              → RecordingMetadata   (stop early; terminationReason=USER_REQUESTED)
 *   GET    /recordings/{id}                              → RecordingMetadata   (poll status)
 *   GET    /recordings/{id}/report                       → ProfilingReport
 *   GET    /recordings/{id}/flamegraph?format=svg|json   → String
 */
@ApplicationScoped
public class ProfilerServiceClient implements ProfilerService {

    /** Typed REST client auto-generated from the @RegisterRestClient interface below. */
    @Inject
    @RestClient
    ControllerApi api;

    @ConfigProperty(name = "profiler.controller.default-namespace", defaultValue = "default")
    String defaultNamespace;

    // ── Pod discovery ──────────────────────────────────────────────────────────

    @Override
    public List<ProfiledPodMetadata> listProfiledPods() {
        Log.debug("listProfiledPods");
        return api.listPods();
    }

    @Override
    public PodJvmStatus getPodJvmStatus(String podName, String namespace) {
        Log.debugf("getPodJvmStatus pod=%s", podName);
        return api.getPodStatus(podName,
                Objects.requireNonNullElse(namespace, defaultNamespace));
    }

    @Override
    public JvmStatistics getJvmStatistics(String podName) {
        Log.debugf("getJvmStatistics pod=%s", podName);
        return api.getPodStats(podName);
    }

    // ── Recording lifecycle ────────────────────────────────────────────────────

    @Override
    public RecordingMetadata startRecording(String podName, int durationSeconds, String profileType) {
        Log.infof("startRecording pod=%s duration=%d type=%s", podName, durationSeconds, profileType);
        var req = new StartRecordingRequest();
        req.durationSeconds = durationSeconds;
        req.profileType = profileType;
        return api.startRecording(podName, req);
    }

    @Override
    public RecordingMetadata stopRecording(String recordingId) {
        Log.infof("stopRecording id=%s", recordingId);
        return api.stopRecording(recordingId);
    }

    @Override
    public RecordingMetadata getRecording(String recordingId) {
        Log.debugf("getRecording id=%s", recordingId);
        return api.getRecording(recordingId);
    }

    // ── Analysis ───────────────────────────────────────────────────────────────

    @Override
    public ProfilingReport getRecordingReport(String recordingId) {
        Log.infof("getRecordingReport id=%s", recordingId);
        return api.getReport(recordingId);
    }

    @Override
    public String getFlameGraph(String recordingId, String format) {
        Log.infof("getFlameGraph id=%s format=%s", recordingId, format);
        return api.getFlameGraph(recordingId, format);
    }

    // ── Inner REST client interface ────────────────────────────────────────────

    /**
     * MicroProfile typed REST client generated from the controller API contract.
     * URL is configured via {@code profiler.controller.url} in application.yml.
     */
    @RegisterRestClient(configKey = "profiler-controller")
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public interface ControllerApi {

        @GET
        @Path("/pods")
        List<ProfiledPodMetadata> listPods();

        @GET
        @Path("/pods/{pod}/status")
        PodJvmStatus getPodStatus(
                @PathParam("pod") String pod,
                @QueryParam("namespace") String namespace);

        @GET
        @Path("/pods/{pod}/stats")
        JvmStatistics getPodStats(@PathParam("pod") String pod);

        @POST
        @Path("/pods/{pod}/recordings")
        RecordingMetadata startRecording(
                @PathParam("pod") String pod,
                StartRecordingRequest request);

        @DELETE
        @Path("/recordings/{id}")
        RecordingMetadata stopRecording(@PathParam("id") String recordingId);

        @GET
        @Path("/recordings/{id}")
        RecordingMetadata getRecording(@PathParam("id") String recordingId);

        @GET
        @Path("/recordings/{id}/report")
        ProfilingReport getReport(@PathParam("id") String recordingId);

        @GET
        @Path("/recordings/{id}/flamegraph")
        @Produces({MediaType.APPLICATION_JSON, "image/svg+xml"})
        String getFlameGraph(
                @PathParam("id") String recordingId,
                @QueryParam("format") String format);
    }
}
