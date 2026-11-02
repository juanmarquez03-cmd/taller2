package co.edu.uptc.distribuidos.infoapi.model;

/**
 * Memoria RAM del contenedor.
 *
 * <p>El límite y el uso real se leen del cgroup del contenedor, que es el mecanismo
 * del kernel con el que containerd aplica los {@code resources.limits.memory} del
 * Deployment. Leer {@code /proc/meminfo} sería un error: muestra la RAM del nodo
 * completo, no la del contenedor.</p>
 *
 * @param fuente                 de dónde salió el dato: "cgroup v2", "cgroup v1" o "no disponible"
 * @param limiteContenedor       memory.max del cgroup (null si no hay límite)
 * @param usoContenedor          memory.current del cgroup: RAM que usa todo el contenedor
 * @param limiteKubernetes       limits.memory declarado en el Deployment (Downward API)
 * @param heapMaximoJvm          heap máximo que la JVM se permite (75 % del límite)
 * @param heapUsadoJvm           heap ocupado en este momento
 */
public record MemoriaInfo(
        String fuente,
        Cantidad limiteContenedor,
        Cantidad usoContenedor,
        Cantidad limiteKubernetes,
        Cantidad heapMaximoJvm,
        Cantidad heapUsadoJvm) {
}
