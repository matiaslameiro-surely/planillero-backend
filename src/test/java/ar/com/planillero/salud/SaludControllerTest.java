package ar.com.planillero.salud;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifica el endpoint de salud.
 *
 * <p>Usa {@code @WebMvcTest} en lugar de {@code @SpringBootTest} para levantar sólo la capa web:
 * es más rápido y no depende de que el resto del contexto exista.
 */
@WebMvcTest(SaludController.class)
class SaludControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /salud responde 200 con estado ok")
    void saludRespondeOk() throws Exception {
        mockMvc.perform(get("/salud"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ok"));
    }

    @Test
    @DisplayName("GET /salud devuelve JSON e informa el momento de la consulta")
    void saludDevuelveJsonConMomento() throws Exception {
        mockMvc.perform(get("/salud"))
                .andExpect(status().isOk())
                .andExpect(content -> {
                    var tipo = content.getResponse().getContentType();
                    if (tipo == null || !tipo.startsWith(MediaType.APPLICATION_JSON_VALUE)) {
                        throw new AssertionError("Se esperaba JSON y llegó: " + tipo);
                    }
                })
                .andExpect(jsonPath("$.momento").isNotEmpty());
    }
}
