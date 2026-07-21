package com.campusguinness.interfaces.web.media;

import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.media.application.command.RegisterMediaCommand;
import com.campusguinness.media.application.result.MediaResult;
import com.campusguinness.media.application.service.MediaApplicationService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final MediaApplicationService service;
    private final CurrentActor currentActor;

    public MediaController(MediaApplicationService service, CurrentActor currentActor) {
        this.service = service;
        this.currentActor = currentActor;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER')")
    public ResponseEntity<MediaResponse> register(@Valid @RequestBody RegisterMediaRequest req) {
        UUID uploaderId = currentActor.requireUserId();
        var cmd = new RegisterMediaCommand(req.schoolId(), req.activityId(), uploaderId,
                req.fileKey(), req.fileName(), req.fileType(), req.fileFormat(),
                req.fileSizeBytes(), req.checksum(), req.description());
        MediaResult r = service.register(cmd);
        return ResponseEntity.created(URI.create("/api/v1/media/" + r.id()))
                .body(new MediaResponse(r.id(), r.internalStatus(), r.publicStatus(), req.fileName()));
    }

    @PostMapping("/{id}/internal-review")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public ResponseEntity<MediaResponse> submitForInternalReview(@PathVariable UUID id) {
        MediaResult r = service.submitForInternalReview(id);
        return ResponseEntity.ok(new MediaResponse(r.id(), r.internalStatus(), r.publicStatus(), null));
    }

    @PostMapping("/{id}/internal-approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public ResponseEntity<MediaResponse> approveInternal(@PathVariable UUID id) {
        MediaResult r = service.approveInternal(id);
        return ResponseEntity.ok(new MediaResponse(r.id(), r.internalStatus(), r.publicStatus(), null));
    }
}
