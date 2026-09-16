package ar.com.planillero.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Responde 403 en JSON cuando el token es válido pero el rol no alcanza. */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final String BODY =
            "{\"error\":\"forbidden\",\"message\":\"El usuario no tiene permisos para este recurso.\"}";

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(BODY);
    }
}
