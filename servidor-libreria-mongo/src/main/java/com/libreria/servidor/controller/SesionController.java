package com.libreria.servidor.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Lo usan las pantallas de login: responde 200 si el usuario y la contraseña son correctos. */
@RestController
public class SesionController {

    @GetMapping("/api/sesion")
    public Map<String, String> sesion(Authentication autenticacion) {
        return Map.of("usuario", autenticacion.getName());
    }
}
