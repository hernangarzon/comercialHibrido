package com.comercialhibrido.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * La landing pública vive en "/" y el panel en "/app/". Spring solo sirve
 * index.html automáticamente en la raíz, así que el panel se reenvía aquí.
 */
@Controller
public class PanelPageController {

    @GetMapping({"/app", "/app/"})
    public String panel() {
        return "forward:/app/index.html";
    }
}
