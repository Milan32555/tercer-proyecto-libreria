package com.libreria.servidor.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Login básico con Spring Security: los clientes envían usuario y contraseña (HTTP Basic) en cada
 * petición. Sin credenciales válidas se responde 401; el diálogo del navegador solo se pide en
 * GraphiQL, para que la web y la app muestren su propia pantalla de acceso.
 */
@Configuration
public class SeguridadConfig {

    @Bean
    SecurityFilterChain seguridad(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(b -> b.authenticationEntryPoint(entradaNoAutorizada()))
                .exceptionHandling(e -> e.authenticationEntryPoint(entradaNoAutorizada()))
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOriginPatterns(List.of("*"));
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT,
                RegistroOperaciones.ENCABEZADO_CLIENTE));
        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", cors);
        return fuente;
    }

    private static AuthenticationEntryPoint entradaNoAutorizada() {
        return (peticion, respuesta, error) -> {
            if (vieneDeGraphiql(peticion)) {
                respuesta.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"Libreria\"");
            }
            respuesta.setStatus(HttpStatus.UNAUTHORIZED.value());
        };
    }

    private static boolean vieneDeGraphiql(HttpServletRequest peticion) {
        String referente = peticion.getHeader(HttpHeaders.REFERER);
        return peticion.getRequestURI().startsWith("/graphiql")
                || (referente != null && referente.contains("/graphiql"));
    }
}
