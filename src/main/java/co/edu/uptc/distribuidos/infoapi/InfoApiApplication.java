package co.edu.uptc.distribuidos.infoapi;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de entrada de la API del taller de Kubernetes.
 *
 * <p>La aplicación expone un único recurso de negocio ({@code /api/info}) que describe
 * el contenedor que atiende la petición: hora, integrantes del grupo, nombre del pod,
 * almacenamiento y memoria. Con eso se puede ver en vivo cómo Kubernetes reparte las
 * peticiones entre los pods y cómo los reemplaza cuando fallan.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@OpenAPIDefinition(info = @Info(
        title = "Info API - Taller de Kubernetes",
        version = "1.0.0",
        description = "Informa qué contenedor responde, en qué nodo corre y qué recursos tiene. "
                + "Sistemas Distribuidos, UPTC."))
public class InfoApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(InfoApiApplication.class, args);
    }
}
