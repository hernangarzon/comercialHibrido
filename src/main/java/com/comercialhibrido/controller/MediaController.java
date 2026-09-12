package com.comercialhibrido.controller;

import com.comercialhibrido.service.WhatsAppMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final WhatsAppMediaService whatsAppMediaService;

    @GetMapping("/{mediaId}")
    public ResponseEntity<byte[]> obtenerMedia(@PathVariable("mediaId") String mediaId) {
        return whatsAppMediaService.descargarArchivo(mediaId);
    }
}