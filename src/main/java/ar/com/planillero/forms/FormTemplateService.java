package ar.com.planillero.forms;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.com.planillero.common.ApiException;
import ar.com.planillero.forms.dto.FormTemplateResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Catálogo de plantillas de formulario.
 *
 * <p>Sólo lee: las plantillas se publican por migración. Cuando exista el módulo de administración
 * del backoffice, el alta va a entrar por acá, y la inmutabilidad ya está garantizada por la base.
 */
@Service
@Transactional(readOnly = true)
public class FormTemplateService {

    private final FormTemplateRepository repository;
    private final ObjectMapper objectMapper;

    public FormTemplateService(FormTemplateRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** Las plantillas vigentes, que son las que un cliente puede ofrecer para completar hoy. */
    public List<FormTemplateResponse> listActive() {
        return repository.findByActiveTrueOrderByTemplateKeyAscVersionDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    /** La última versión vigente de una clave. */
    public FormTemplateResponse getLatestActive(String templateKey) {
        return toResponse(requireLatestActive(templateKey));
    }

    /**
     * Resuelve la plantilla contra la que hay que validar un envío.
     *
     * <p>Si el envío indica una versión, se usa esa —aunque ya no esté vigente, porque un
     * formulario cargado hace una semana se tiene que poder enviar con las reglas que tenía—; si no,
     * la última vigente.
     *
     * @throws ApiException {@code 400 template_not_found} si la plantilla o la versión no existen
     */
    public FormTemplate resolveForSubmission(String templateKey, Integer version) {
        if (version == null) {
            return repository.findFirstByTemplateKeyAndActiveTrueOrderByVersionDesc(templateKey)
                    .orElseThrow(() -> ApiException.badRequest(
                            "template_not_found",
                            "No hay ninguna plantilla vigente con la clave «" + templateKey + "»."));
        }

        return repository.findByTemplateKeyAndVersion(templateKey, version)
                .orElseThrow(() -> ApiException.badRequest(
                        "template_not_found",
                        "No existe la versión " + version + " de la plantilla «" + templateKey + "»."));
    }

    private FormTemplate requireLatestActive(String templateKey) {
        return repository.findFirstByTemplateKeyAndActiveTrueOrderByVersionDesc(templateKey)
                .orElseThrow(() -> ApiException.notFound(
                        "template_not_found",
                        "No hay ninguna plantilla vigente con la clave «" + templateKey + "»."));
    }

    private FormTemplateResponse toResponse(FormTemplate template) {
        return new FormTemplateResponse(
                template.getId(),
                template.getTemplateKey(),
                template.getVersion(),
                template.getName(),
                template.getDescription(),
                // El schema sale como JSON embebido y no como texto escapado: el cliente lo usa tal
                // cual para armar los campos.
                objectMapper.readTree(template.getSchemaJson()),
                template.getCreatedAt());
    }
}
