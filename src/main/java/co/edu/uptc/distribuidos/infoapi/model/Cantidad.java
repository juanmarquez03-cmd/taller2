package co.edu.uptc.distribuidos.infoapi.model;

import java.util.Locale;

/**
 * Una cantidad de bytes acompañada de su versión legible (por ejemplo "384.0 MiB").
 *
 * <p>Se devuelven las dos formas: los bytes sirven para programas y comparaciones,
 * el texto sirve para leer la respuesta en el navegador durante la demostración.</p>
 *
 * @param bytes   valor exacto en bytes
 * @param legible valor formateado en unidades binarias (KiB, MiB, GiB)
 */
public record Cantidad(long bytes, String legible) {

    private static final String[] UNIDADES = {"B", "KiB", "MiB", "GiB", "TiB"};

    /** Crea una cantidad calculando el texto legible. Devuelve null si el valor es null. */
    public static Cantidad de(Long bytes) {
        if (bytes == null) {
            return null;
        }
        return new Cantidad(bytes, formatear(bytes));
    }

    /** Formatea bytes con unidades binarias y un decimal. */
    public static String formatear(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double valor = bytes;
        int unidad = 0;
        while (valor >= 1024 && unidad < UNIDADES.length - 1) {
            valor /= 1024;
            unidad++;
        }
        return String.format(Locale.ROOT, "%.1f %s", valor, UNIDADES[unidad]);
    }
}
