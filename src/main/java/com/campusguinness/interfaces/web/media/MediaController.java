package com.campusguinness.interfaces.web.media;

import com.campusguinness.media.application.command.RegisterMediaCommand;
import com.campusguinness.media.application.result.MediaResult;
import com.campusguinness.media.application.service.MediaApplicationService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class MediaController {

    private final MediaApplicationService service;

    public MediaController(MediaApplicationService service) {
        this.service = service;
    }

    @PostMapping("/media")
    public ResponseEntity<MediaResponse> register(@Valid @RequestBody RegisterMediaRequest req) {
        var cmd = new RegisterMediaCommand(req.schoolId(), req.activityId(), req.uploaderId(),
                req.fileKey(), req.fileName(), req.fileType(), req.fileFormat(),
                req.fileSizeBytes(), req.checksum(), req.description());
        MediaResult r = service.register(cmd);
        return ResponseEntity.created(URI.create("/api/v1/media/" + r.id()))
                .body(new MediaResponse(r.id(), r.internalStatus(), r.publicStatus(), req.fileName()));
    }

    @PostMapping("/media/{id}/internal-review")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public ResponseEntity<MediaResponse> submitForInternalReview(@PathVariable UUID id) {
        MediaResult r = service.submitForInternalReview(id);
        return ResponseEntity.ok(new MediaResponse(r.id(), r.internalStatus(), r.publicStatus(), null));
    }

    @PostMapping("/media/{id}/internal-approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public ResponseEntity<MediaResponse> approveInternal(@PathVariable UUID id) {
        MediaResult r = service.approveInternal(id);
        return ResponseEntity.ok(new MediaResponse(r.id(), r.internalStatus(), r.publicStatus(), null));
    }

    @PostMapping("/media/{id}/make-public")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public ResponseEntity<MediaResponse> makePublic(@PathVariable UUID id) {
        MediaResult r = service.makePublic(id);
        return ResponseEntity.ok(new MediaResponse(r.id(), r.internalStatus(), r.publicStatus(), null));
    }

    @GetMapping("/media/school/{schoolId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public List<MediaResult> listBySchool(@PathVariable UUID schoolId) {
        return service.listBySchool(schoolId);
    }

    @GetMapping("/public/activities/{activityId}/media")
    public List<MediaResult> listPublicByActivity(@PathVariable UUID activityId) {
        return service.listPublicByActivity(activityId);
    }
}
