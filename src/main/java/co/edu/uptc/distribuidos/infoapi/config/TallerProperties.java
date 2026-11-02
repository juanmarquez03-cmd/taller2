package co.edu.uptc.distribuidos.infoapi.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Datos del grupo, leídos de {@code application.yml} (prefijo {@code taller}).
 *
 * <p>Se dejan como configuración y no como constantes en el código para poder
 * cambiarlos sin recompilar: por ejemplo con variables de entorno
 * ({@code TALLER_INTEGRANTES_0_...}) o un ConfigMap de Kubernetes.</p>
 *
 * @param grupo       nombre del curso o del grupo
 * @param integrantes nombres completos de los integrantes
 */
@ConfigurationProperties(prefix = "taller")
public record TallerProperties(String grupo, List<String> integrantes) {

    public TallerProperties {
        integrantes = integrantes == null ? List.of() : List.copyOf(integrantes);
    }
}
