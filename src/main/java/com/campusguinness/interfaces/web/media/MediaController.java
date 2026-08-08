package com.campusguinness.interfaces.web.media;

import com.campusguinness.media.application.command.RegisterMediaCommand;
import com.campusguinness.media.application.result.MediaResult;
import com.campusguinness.media.application.service.MediaApplicationService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final MediaApplicationService service;

    public MediaController(MediaApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MediaResponse> register(@Valid @RequestBody RegisterMediaRequest req) {
        var cmd = new RegisterMediaCommand(req.schoolId(), req.activityId(),
                req.fileKey(), req.fileName(), req.fileType(), req.fileFormat(),
                req.fileSizeBytes(), req.checksum(), req.description());
        MediaResult r = service.register(cmd);
        return ResponseEntity.created(URI.create("/api/v1/media/" + r.id()))
                .body(new MediaResponse(r.id(), r.internalStatus(), r.publicStatus(), req.fileName()));
    }

    @PostMapping("/{id}/internal-review")
    public ResponseEntity<MediaResponse> submitForInternalReview(@PathVariable UUID id) {
        MediaResult r = service.submitForInternalReview(id);
        return ResponseEntity.ok(new MediaResponse(r.id(), r.internalStatus(), r.publicStatus(), null));
    }

    @PostMapping("/{id}/internal-approve")
    public ResponseEntity<MediaResponse> approveInternal(@PathVariable UUID id) {
        MediaResult r = service.approveInternal(id);
        return ResponseEntity.ok(new MediaResponse(r.id(), r.internalStatus(), r.publicStatus(), null));
    }
}
