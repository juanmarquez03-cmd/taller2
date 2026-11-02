package co.edu.uptc.distribuidos.infoapi.model;

/**
 * Almacenamiento visto desde dentro del contenedor.
 *
 * <p>El sistema de archivos raíz del contenedor (overlay) vive en el disco del nodo,
 * por eso total, usado y disponible reflejan ese disco. El límite que Kubernetes
 * le impone al contenedor es {@code limiteEfimeroKubernetes} (ephemeral-storage):
 * si el contenedor escribe más que eso, el kubelet lo expulsa.</p>
 *
 * @param puntoMontaje             ruta medida (normalmente "/")
 * @param total                    tamaño total del sistema de archivos
 * @param usado                    espacio ocupado
 * @param disponible               espacio que el contenedor todavía puede usar
 * @param limiteEfimeroKubernetes  límite ephemeral-storage del contenedor (null fuera de Kubernetes)
 */
public record AlmacenamientoInfo(
        String puntoMontaje,
        Cantidad total,
        Cantidad usado,
        Cantidad disponible,
        Cantidad limiteEfimeroKubernetes) {
}
