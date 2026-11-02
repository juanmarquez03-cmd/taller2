package co.edu.uptc.distribuidos.infoapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.uptc.distribuidos.infoapi.model.AlmacenamientoInfo;
import co.edu.uptc.distribuidos.infoapi.model.Cantidad;
import co.edu.uptc.distribuidos.infoapi.model.ContenedorInfo;
import co.edu.uptc.distribuidos.infoapi.model.MemoriaInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pruebas del lector de recursos con un cgroup falso en un directorio temporal,
 * para no depender de la máquina donde se compila.
 */
class LectorRecursosTest {

    @TempDir
    Path tmp;

    @Test
    void leeLimiteYUsoDeCgroupV2() throws IOException {
        Files.writeString(tmp.resolve("memory.max"), "402653184\n");
        Files.writeString(tmp.resolve("memory.current"), "157286400\n");
        LectorRecursos lector = new LectorRecursos(tmp, tmp, Map.of("MEMORY_LIMIT_BYTES", "402653184")::get);

        MemoriaInfo memoria = lector.memoria();

        assertThat(memoria.fuente()).isEqualTo("cgroup v2");
        assertThat(memoria.limiteContenedor().bytes()).isEqualTo(402_653_184L);
        assertThat(memoria.limiteContenedor().legible()).isEqualTo("384.0 MiB");
        assertThat(memoria.usoContenedor().legible()).isEqualTo("150.0 MiB");
        assertThat(memoria.limiteKubernetes().bytes()).isEqualTo(402_653_184L);
        assertThat(memoria.heapMaximoJvm().bytes()).isPositive();
    }

    @Test
    void cgroupV2SinLimiteDevuelveNull() throws IOException {
        Files.writeString(tmp.resolve("memory.max"), "max\n");
        Files.writeString(tmp.resolve("memory.current"), "1048576\n");
        LectorRecursos lector = new LectorRecursos(tmp, tmp, Map.<String, String>of()::get);

        MemoriaInfo memoria = lector.memoria();

        assertThat(memoria.limiteContenedor()).isNull();
        assertThat(memoria.usoContenedor().legible()).isEqualTo("1.0 MiB");
    }

    @Test
    void cgroupV1ConValorGiganteEsSinLimite() throws IOException {
        Path v1 = Files.createDirectory(tmp.resolve("memory"));
        Files.writeString(v1.resolve("memory.limit_in_bytes"), "9223372036854771712\n");
        Files.writeString(v1.resolve("memory.usage_in_bytes"), "2048\n");
        LectorRecursos lector = new LectorRecursos(tmp, tmp, Map.<String, String>of()::get);

        MemoriaInfo memoria = lector.memoria();

        assertThat(memoria.fuente()).isEqualTo("cgroup v1");
        assertThat(memoria.limiteContenedor()).isNull();
        assertThat(memoria.usoContenedor().bytes()).isEqualTo(2048L);
    }

    @Test
    void sinCgroupInformaNoDisponible() {
        LectorRecursos lector = new LectorRecursos(tmp.resolve("no-existe"), tmp, Map.<String, String>of()::get);

        assertThat(lector.memoria().fuente()).isEqualTo("no disponible");
    }

    @Test
    void leeDatosDelPodDesdeLaDownwardApi() {
        Map<String, String> env = Map.of(
                "HOSTNAME", "info-api-abc12",
                "POD_NAME", "info-api-abc12",
                "NODE_NAME", "k8s-worker-david1",
                "POD_IP", "10.244.3.7");
        LectorRecursos lector = new LectorRecursos(tmp, tmp, env::get);

        ContenedorInfo c = lector.contenedor();

        assertThat(c.hostname()).isEqualTo("info-api-abc12");
        assertThat(c.nodo()).isEqualTo("k8s-worker-david1");
        assertThat(c.ipPod()).isEqualTo("10.244.3.7");
        assertThat(c.namespace()).isNull();
    }

    @Test
    void almacenamientoMideElDiscoYElLimiteEfimero() {
        LectorRecursos lector = new LectorRecursos(tmp, tmp, Map.of("STORAGE_LIMIT_BYTES", "536870912")::get);

        AlmacenamientoInfo disco = lector.almacenamiento();

        assertThat(disco.total().bytes()).isPositive();
        assertThat(disco.disponible().bytes()).isLessThanOrEqualTo(disco.total().bytes());
        assertThat(disco.limiteEfimeroKubernetes().legible()).isEqualTo("512.0 MiB");
    }

    @Test
    void formateaUnidadesBinarias() {
        assertThat(Cantidad.formatear(512)).isEqualTo("512 B");
        assertThat(Cantidad.formatear(1536)).isEqualTo("1.5 KiB");
        assertThat(Cantidad.formatear(3L * 1024 * 1024 * 1024)).isEqualTo("3.0 GiB");
        assertThat(Cantidad.de(null)).isNull();
    }
}
