package co.edu.uptc.distribuidos.infoapi.service;

import co.edu.uptc.distribuidos.infoapi.model.AlmacenamientoInfo;
import co.edu.uptc.distribuidos.infoapi.model.Cantidad;
import co.edu.uptc.distribuidos.infoapi.model.ContenedorInfo;
import co.edu.uptc.distribuidos.infoapi.model.MemoriaInfo;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * Lee los recursos del contenedor directamente del sistema operativo.
 *
 * <p>No depende de Spring a propósito: así se puede probar con archivos falsos en un
 * directorio temporal (ver {@code LectorRecursosTest}). Las rutas del cgroup, la raíz
 * del disco y el acceso a variables de entorno se inyectan por el constructor.</p>
 */
public class LectorRecursos {

    /** cgroup v1 usa un número enorme (casi 2^63) para decir "sin límite". */
    private static final long UMBRAL_SIN_LIMITE = 1L << 60;

    private final Path cgroupRaiz;
    private final Path discoRaiz;
    private final Function<String, String> entorno;

    /** Constructor para producción: cgroup real, disco "/" y variables de entorno reales. */
    public LectorRecursos() {
        this(Path.of("/sys/fs/cgroup"), Path.of("/"), System::getenv);
    }

    /** Constructor para pruebas. */
    public LectorRecursos(Path cgroupRaiz, Path discoRaiz, Function<String, String> entorno) {
        this.cgroupRaiz = cgroupRaiz;
        this.discoRaiz = discoRaiz;
        this.entorno = entorno;
    }

    // ------------------------------------------------------------------ contenedor

    /** Identidad del contenedor: hostname y los datos de la Downward API. */
    public ContenedorInfo contenedor() {
        return new ContenedorInfo(
                hostname(),
                entorno.apply("POD_NAME"),
                entorno.apply("POD_UID"),
                entorno.apply("POD_NAMESPACE"),
                entorno.apply("POD_IP"),
                entorno.apply("NODE_NAME"),
                ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
    }

    private String hostname() {
        String desdeEntorno = entorno.apply("HOSTNAME");
        if (desdeEntorno != null && !desdeEntorno.isBlank()) {
            return desdeEntorno;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (IOException e) {
            return "desconocido";
        }
    }

    // -------------------------------------------------------------- almacenamiento

    /** Espacio del sistema de archivos raíz del contenedor. */
    public AlmacenamientoInfo almacenamiento() {
        File raiz = discoRaiz.toFile();
        long total = raiz.getTotalSpace();
        long libre = raiz.getFreeSpace();
        long disponible = raiz.getUsableSpace();
        return new AlmacenamientoInfo(
                discoRaiz.toString(),
                Cantidad.de(total),
                Cantidad.de(Math.max(0, total - libre)),
                Cantidad.de(disponible),
                Cantidad.de(numeroDeEntorno("STORAGE_LIMIT_BYTES")));
    }

    // --------------------------------------------------------------------- memoria

    /**
     * Memoria del contenedor según el cgroup.
     *
     * <p>Primero intenta cgroup v2 (Ubuntu 22.04 en adelante, que es lo que usan los
     * nodos del clúster); si no existe, cgroup v1. El heap de la JVM se informa aparte
     * porque es solo una parte de la memoria del contenedor.</p>
     */
    public MemoriaInfo memoria() {
        String fuente;
        Long limite = null;
        Long uso = null;

        Path v2Max = cgroupRaiz.resolve("memory.max");
        Path v1Dir = cgroupRaiz.resolve("memory");
        if (Files.isReadable(v2Max)) {
            fuente = "cgroup v2";
            limite = leerLimite(v2Max);
            uso = leerNumero(cgroupRaiz.resolve("memory.current"));
        } else if (Files.isReadable(v1Dir.resolve("memory.limit_in_bytes"))) {
            fuente = "cgroup v1";
            limite = leerLimite(v1Dir.resolve("memory.limit_in_bytes"));
            uso = leerNumero(v1Dir.resolve("memory.usage_in_bytes"));
        } else {
            fuente = "no disponible";
        }

        Runtime runtime = Runtime.getRuntime();
        long heapUsado = runtime.totalMemory() - runtime.freeMemory();

        return new MemoriaInfo(
                fuente,
                Cantidad.de(limite),
                Cantidad.de(uso),
                Cantidad.de(numeroDeEntorno("MEMORY_LIMIT_BYTES")),
                Cantidad.de(runtime.maxMemory()),
                Cantidad.de(heapUsado));
    }

    // ------------------------------------------------------------------- utilidades

    /** Lee un límite del cgroup: "max" o un valor gigante significan "sin límite" (null). */
    private Long leerLimite(Path archivo) {
        String texto = leerTexto(archivo);
        if (texto == null || texto.equals("max")) {
            return null;
        }
        Long valor = aNumero(texto);
        return (valor == null || valor >= UMBRAL_SIN_LIMITE) ? null : valor;
    }

    private Long leerNumero(Path archivo) {
        return aNumero(leerTexto(archivo));
    }

    private String leerTexto(Path archivo) {
        try {
            return Files.readString(archivo, StandardCharsets.US_ASCII).trim();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private Long numeroDeEntorno(String variable) {
        return aNumero(entorno.apply(variable));
    }

    private static Long aNumero(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(texto.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
