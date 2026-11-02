package co.edu.uptc.distribuidos.infoapi.model;

/**
 * Identidad del contenedor que responde.
 *
 * <p>En Kubernetes el hostname del contenedor es el nombre del pod, así que
 * {@code hostname} basta para saber quién respondió. Los demás campos llegan por
 * la Downward API (variables de entorno definidas en el Deployment) y son null si
 * la aplicación corre fuera de Kubernetes.</p>
 *
 * @param hostname            nombre del host del contenedor (= nombre del pod)
 * @param pod                 nombre del pod según Kubernetes
 * @param uidPod              identificador único del pod (cambia cada vez que se recrea)
 * @param namespace           namespace del pod
 * @param ipPod               IP del pod en la red de pods (flannel)
 * @param nodo                worker donde está corriendo el pod
 * @param tiempoActivoSegundos segundos desde que arrancó la JVM de este contenedor
 */
public record ContenedorInfo(
        String hostname,
        String pod,
        String uidPod,
        String namespace,
        String ipPod,
        String nodo,
        long tiempoActivoSegundos) {
}
