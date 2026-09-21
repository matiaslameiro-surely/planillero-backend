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
     * <p>Si el envío indica una versión, se usa esa; si no, la última vigente. En los dos casos la
     * versión tiene que estar <strong>vigente</strong>: una plantilla dada de baja ya no acepta
     * envíos nuevos.
     *
     * @throws ApiException {@code 400 template_not_found} si la plantilla o la versión no existen;
     *                      {@code 400 template_inactive} si la versión existe pero fue dada de baja
     */
    public FormTemplate resolveForSubmission(String templateKey, Integer version) {
        if (version == null) {
            return repository.findFirstByTemplateKeyAndActiveTrueOrderByVersionDesc(templateKey)
                    .orElseThrow(() -> ApiException.badRequest(
                            "template_not_found",
                            "No hay ninguna plantilla vigente con la clave «" + templateKey + "»."));
        }

        FormTemplate template = repository.findByTemplateKeyAndVersion(templateKey, version)
                .orElseThrow(() -> ApiException.badRequest(
                        "template_not_found",
                        "No existe la versión " + version + " de la plantilla «" + templateKey + "»."));

        // Código propio y no template_not_found: el cliente tiene que poder distinguir "esa plantilla
        // no existe" de "la versión que tenés guardada quedó vieja, descargá la vigente".
        if (!template.isActive()) {
            throw ApiException.badRequest(
                    "template_inactive",
                    "La versión " + version + " de la plantilla «" + templateKey
                            + "» ya no está vigente. Descargá la versión actual y volvé a completar el formulario.");
        }
        return template;
    }

    /**
     * La plantilla con la que se validó un formulario ya guardado.
     *
     * <p>Devuelve la versión puntual, vigente o no: lo que ya se aceptó se interpreta con las reglas
     * que lo aceptaron, no con las de hoy.
     */
    public FormTemplate requireById(java.util.UUID id) {
        return repository.findById(id).orElseThrow(() -> new IllegalStateException(
                "Un formulario guardado apunta a la plantilla " + id + ", que no existe."));
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
