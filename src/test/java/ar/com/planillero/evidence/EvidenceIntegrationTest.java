package ar.com.planillero.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

import ar.com.planillero.AbstractIntegrationTest;
import ar.com.planillero.evidence.crypto.CryptoService;
import ar.com.planillero.evidence.dto.CreateManifestRequest;
import ar.com.planillero.evidence.model.Evidence;
import ar.com.planillero.evidence.repository.EvidenceRepository;
import ar.com.planillero.evidence.storage.ObjectStorageService;
import ar.com.planillero.evidence.storage.StorageProperties;
import ar.com.planillero.evidence.storage.WormPolicyViolationException;

@SpringBootTest
@AutoConfigureMockMvc
class EvidenceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CryptoService cryptoService;

    @Autowired
    private ObjectStorageService storageService;

    @Autowired
    private StorageProperties storageProperties;

    @Autowired
    private EvidenceRepository evidenceRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String tokenOperador;

    @BeforeEach
    void autenticar() throws Exception {
        if (tokenOperador != null) return;

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"operador.demo\",\"password\":\"Operador123!\"}"))
                .andExpect(status().isOk())
                .andReturn();

        tokenOperador = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    @Test
    @DisplayName("Subida multipart de foto con cálculo automático de SHA-256 en streaming")
    void uploadEvidenceSuccess() throws Exception {
        UUID visitId = UUID.randomUUID();
        byte[] content = "fotografia pericial de fachada".getBytes(StandardCharsets.UTF_8);
        String expectedHash = cryptoService.calculateSha256(content);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fachada.jpg",
                "image/jpeg",
                content);

        mockMvc.perform(multipart("/api/v1/visits/{visitId}/evidences", visitId)
                        .file(file)
                        .param("type", "PHOTO")
                        .header("Authorization", "Bearer " + tokenOperador))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.visitId").value(visitId.toString()))
                .andExpect(jsonPath("$.evidenceType").value("PHOTO"))
                .andExpect(jsonPath("$.sha256Hash").value(expectedHash))
                .andExpect(jsonPath("$.fileSize").value(content.length));
    }

    @Test
    @DisplayName("Rechazo con 400 cuando el hash declarado en X-Content-SHA256 difiere del contenido real")
    void uploadEvidenceIntegrityMismatch() throws Exception {
        UUID visitId = UUID.randomUUID();
        byte[] content = "imagen legitima".getBytes(StandardCharsets.UTF_8);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "prueba.jpg",
                "image/jpeg",
                content);

        mockMvc.perform(multipart("/api/v1/visits/{visitId}/evidences", visitId)
                        .file(file)
                        .param("type", "PHOTO")
                        .header("X-Content-SHA256", "0000000000000000000000000000000000000000000000000000000000000000")
                        .header("Authorization", "Bearer " + tokenOperador))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("integrity_mismatch"));
    }

    @Test
    @DisplayName("Ciclo completo de sellado pericial y verificación criptográfica (VERIFIED vs TAMPERED)")
    void manifestLifecycleAndTamperDetection() throws Exception {
        UUID visitId = UUID.randomUUID();

        // 1. Subir foto
        byte[] photoBytes = "foto del entorno pericial".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile photo = new MockMultipartFile("file", "ambiente.jpg", "image/jpeg", photoBytes);

        MvcResult photoResult = mockMvc.perform(multipart("/api/v1/visits/{visitId}/evidences", visitId)
                        .file(photo)
                        .param("type", "PHOTO")
                        .header("Authorization", "Bearer " + tokenOperador))
                .andExpect(status().isCreated())
                .andReturn();

        String photoId = JsonPath.read(photoResult.getResponse().getContentAsString(), "$.id");

        // 2. Subir firma ológrafa
        byte[] sigBytes = "trazo de firma digitalizada png".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile signature = new MockMultipartFile("file", "firma.png", "image/png", sigBytes);

        MvcResult sigResult = mockMvc.perform(multipart("/api/v1/visits/{visitId}/evidences", visitId)
                        .file(signature)
                        .param("type", "SIGNATURE")
                        .header("Authorization", "Bearer " + tokenOperador))
                .andExpect(status().isCreated())
                .andReturn();

        String sigId = JsonPath.read(sigResult.getResponse().getContentAsString(), "$.id");

        // 3. Descarga del binario de firma y validación de ETag y headers
        mockMvc.perform(get("/api/v1/visits/{visitId}/evidences/{evidenceId}/file", visitId, sigId)
                        .header("Authorization", "Bearer " + tokenOperador))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"" + cryptoService.calculateSha256(sigBytes) + "\""));

        // 4. Sellar manifiesto con ambas evidencias
        CreateManifestRequest manifestRequest = new CreateManifestRequest(
                "Motorola G84 - Android 14",
                List.of(UUID.fromString(photoId), UUID.fromString(sigId)));

        mockMvc.perform(post("/api/v1/visits/{visitId}/manifest", visitId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(manifestRequest))
                        .header("Authorization", "Bearer " + tokenOperador))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.hmacSignature").isNotEmpty());

        // 5. Verificar integridad del manifiesto -> VERIFIED
        mockMvc.perform(post("/api/v1/visits/{visitId}/manifest/verify", visitId)
                        .header("Authorization", "Bearer " + tokenOperador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.signatureValid").value(true))
                .andExpect(jsonPath("$.allEvidencesIntact").value(true));

        // 6. Simular manipulación no autorizada (Tampering) en el almacenamiento
        Evidence ev = evidenceRepository.findById(UUID.fromString(photoId)).orElseThrow();
        File storedFile = new File(storageProperties.getLocalDir(), ev.getStoragePath());
        try (FileOutputStream out = new FileOutputStream(storedFile, true)) {
            out.write("byte corrupto".getBytes(StandardCharsets.UTF_8));
        }

        // 7. Volver a verificar integridad -> debe detectar TAMPERED
        mockMvc.perform(post("/api/v1/visits/{visitId}/manifest/verify", visitId)
                        .header("Authorization", "Bearer " + tokenOperador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TAMPERED"))
                .andExpect(jsonPath("$.allEvidencesIntact").value(false));
    }

    @Test
    @DisplayName("Política WORM: no permite sobreescribir un archivo existente")
    void wormPolicyViolation() {
        String key = "test/worm-policy-" + UUID.randomUUID() + ".bin";
        byte[] data = "primer registro inmutable".getBytes(StandardCharsets.UTF_8);

        storageService.save(key, new ByteArrayInputStream(data), data.length, "application/octet-stream");

        // Intentar sobreescribir debe lanzar WormPolicyViolationException
        assertThrows(WormPolicyViolationException.class, () ->
                storageService.save(key, new ByteArrayInputStream("nuevo dato".getBytes(StandardCharsets.UTF_8)),
                        10, "application/octet-stream"));
    }
}
