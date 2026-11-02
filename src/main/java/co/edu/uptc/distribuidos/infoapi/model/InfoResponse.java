package co.edu.uptc.distribuidos.infoapi.model;

import java.util.List;

/**
 * Respuesta de {@code GET /api/info}: los cinco datos que pide el taller.
 *
 * @param horaContenedor  hora actual del contenedor, en ISO-8601 con zona
 * @param zonaHoraria     zona horaria del contenedor (variable TZ)
 * @param grupo           curso o grupo
 * @param integrantes     nombres de los integrantes
 * @param contenedor      quién responde: pod, nodo, IP
 * @param almacenamiento  disco del contenedor
 * @param memoria         RAM del contenedor
 */
public record InfoResponse(
        String horaContenedor,
        String zonaHoraria,
        String grupo,
        List<String> integrantes,
        ContenedorInfo contenedor,
        AlmacenamientoInfo almacenamiento,
        MemoriaInfo memoria) {
}
