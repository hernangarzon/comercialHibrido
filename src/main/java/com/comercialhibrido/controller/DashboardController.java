package com.comercialhibrido.controller;

import com.comercialhibrido.security.JwtService;
import com.comercialhibrido.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Set;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private static final Set<Integer> PERIODS = Set.of(7, 30, 90);

    private final DashboardService dashboardService;

    /**
     * @param days período en días (7, 30 o 90)
     * @param tz   zona horaria del navegador, para agrupar los mensajes por día local
     */
    @GetMapping
    public DashboardService.Dashboard obtener(
        @RequestAttribute("authenticatedUser") JwtService.JwtPayload user,
        @RequestParam(value = "days", defaultValue = "7") int days,
        @RequestParam(value = "tz", defaultValue = "UTC") String tz
    ) {
        if (!PERIODS.contains(days)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El período debe ser 7, 30 o 90 días");
        }
        ZoneId zone;
        try {
            zone = ZoneId.of(tz);
        } catch (DateTimeException e) {
            zone = ZoneId.of("UTC");
        }
        return dashboardService.calcular(user.companyId(), days, zone);
    }
}
