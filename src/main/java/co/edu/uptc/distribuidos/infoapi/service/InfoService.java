package co.edu.uptc.distribuidos.infoapi.service;

import co.edu.uptc.distribuidos.infoapi.config.TallerProperties;
import co.edu.uptc.distribuidos.infoapi.model.ContenedorInfo;
import co.edu.uptc.distribuidos.infoapi.model.InfoResponse;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;

/**
 * Arma la respuesta del endpoint combinando la configuración del grupo con los
 * recursos que lee {@link LectorRecursos}.
 *
 * <p>No guarda estado entre peticiones: cada llamada lee los valores en ese momento.
 * Eso es importante en Kubernetes, porque cualquier réplica puede atender cualquier
 * petición y un pod puede desaparecer en cualquier momento sin perder nada.</p>
 */
@Service
public class InfoService {

    private static final DateTimeFormatter HORA_CORTA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final TallerProperties taller;
    private final LectorRecursos lector;

    public InfoService(TallerProperties taller, LectorRecursos lector) {
        this.taller = taller;
        this.lector = lector;
    }

    /** Respuesta completa de /api/info. */
    public InfoResponse info() {
        ZonedDateTime ahora = ZonedDateTime.now();
        return new InfoResponse(
                ahora.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                ZoneId.systemDefault().getId(),
                taller.grupo(),
                taller.integrantes(),
                lector.contenedor(),
                lector.almacenamiento(),
                lector.memoria());
    }

    /**
     * Una sola línea para seguir la demostración desde la terminal, por ejemplo:
     * {@code [20:15:03] pod info-api-7d9f-abcde | nodo k8s-worker-david1 | activo 42 s}.
     */
    public String resumen() {
        ContenedorInfo c = lector.contenedor();
        String pod = c.pod() != null ? c.pod() : c.hostname();
        String nodo = c.nodo() != null ? c.nodo() : "fuera de Kubernetes";
        return "[%s] pod %s | nodo %s | activo %d s".formatted(
                ZonedDateTime.now().format(HORA_CORTA), pod, nodo, c.tiempoActivoSegundos());
    }
}
