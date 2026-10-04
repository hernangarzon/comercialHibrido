package com.comercialhibrido.controller;

import com.comercialhibrido.domain.entity.Company;
import com.comercialhibrido.repository.CompanyRepository;
import com.comercialhibrido.repository.MessageRepository;
import com.comercialhibrido.security.JwtService;
import com.comercialhibrido.service.WhatsAppMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final WhatsAppMediaService whatsAppMediaService;
    private final MessageRepository messageRepository;
    private final CompanyRepository companyRepository;

    @GetMapping("/{mediaId}")
    public ResponseEntity<byte[]> obtenerMedia(
        @PathVariable("mediaId") String mediaId,
        @RequestAttribute("authenticatedUser") JwtService.JwtPayload user
    ) {
        // Solo se sirven archivos recibidos en conversaciones de la empresa del usuario.
        if (!messageRepository.existsByMediaIdAndConversation_Company_Id(mediaId, user.companyId())) {
            return ResponseEntity.notFound().build();
        }
        String accessToken = companyRepository.findById(user.companyId())
            .map(Company::getWhatsappAccessToken)
            .orElse(null);
        return whatsAppMediaService.descargarArchivo(mediaId, accessToken);
    }
}
