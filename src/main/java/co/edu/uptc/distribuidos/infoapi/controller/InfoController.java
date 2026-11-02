package co.edu.uptc.distribuidos.infoapi.controller;

import co.edu.uptc.distribuidos.infoapi.model.InfoResponse;
import co.edu.uptc.distribuidos.infoapi.service.InfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints públicos de la API.
 *
 * <p>El controlador solo traduce HTTP a llamadas del servicio; la lógica está en
 * {@link InfoService} y {@link co.edu.uptc.distribuidos.infoapi.service.LectorRecursos}.</p>
 */
@RestController
@Tag(name = "Contenedor", description = "Información del contenedor que atiende la petición")
public class InfoController {

    private final InfoService infoService;

    public InfoController(InfoService infoService) {
        this.infoService = infoService;
    }

    @GetMapping(value = {"/", "/api/info"}, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Información del contenedor",
            description = "Devuelve la hora del contenedor, los integrantes del grupo, el nombre del pod "
                    + "y el nodo que responden, el almacenamiento y la memoria RAM del contenedor. "
                    + "Al recargar varias veces cambia el pod, porque el Service reparte las peticiones.")
    public InfoResponse info() {
        return infoService.info();
    }

    @GetMapping(value = "/api/resumen", produces = "text/plain;charset=UTF-8")
    @Operation(
            summary = "Resumen en una línea",
            description = "Hora, pod, nodo y segundos activo. Pensado para un bucle con curl durante la "
                    + "demostración de borrado de pods y apagado de un worker.")
    public String resumen() {
        return infoService.resumen() + "\n";
    }
}
