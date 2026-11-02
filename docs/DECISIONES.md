# Decisiones técnicas

Este documento explica **qué se decidió y por qué** en cada parte del servicio. Sirve como documentación del código para la sustentación.

## 0. Uso de IA

El taller exige que el código sea generado en su totalidad con IA. Así se hizo:

- **Herramienta:** Claude (modelo Opus 5.5, de Anthropic), en conversación, el 1 de noviembre de 2026.
- **Entrada:** el enunciado del taller (PDF), la guía del clúster (Kubernetes v1.37.1, 1 master y 5 workers unidos por Tailscale, flannel como red de pods, VMs Ubuntu de unos 1,6 GB de RAM) y los nombres de los integrantes.
- **Qué generó la IA:** todo el código Java, las pruebas, el `pom.xml`, el `Dockerfile`, el flujo de GitHub Actions, los manifiestos de Kubernetes y estos documentos.
- **Qué hizo el grupo:** revisar el código, publicar la imagen, desplegarla en el clúster, ejecutar la demostración y verificar que la respuesta del endpoint fuera correcta.

Las decisiones de abajo son las que tomó la IA al generar el proyecto, con su justificación.

---

## 1. Lenguaje y framework

**Decisión:** Java 21 con Spring Boot 4.1 (`spring-boot-starter-webmvc`).

**Por qué:**
- El taller pide Spring Boot. La línea 4.1 es la versión estable vigente; la 3.5 ya terminó su soporte gratuito.
- Java 21 es LTS y la JVM reconoce los límites de memoria del contenedor (lee el cgroup), lo que evita que el proceso se pase del límite y lo mate el kernel.
- Spring MVC (servlets, Tomcat embebido) en lugar de WebFlux: un endpoint de lectura sin concurrencia alta no necesita programación reactiva, y MVC es más fácil de leer y explicar.

## 2. Diseño de la respuesta

**Decisión:** un JSON con `records` de Java, agrupado en `contenedor`, `almacenamiento` y `memoria`. Cada cantidad se entrega en bytes y en texto legible (`Cantidad`).

**Por qué:**
- Los `records` son inmutables y sin código repetitivo (getters, equals): el modelo de la respuesta se lee de un vistazo.
- Bytes para programas, texto para personas: en la demostración se lee "384.0 MiB" en el navegador sin hacer cuentas.
- Se agregó `nodo` y `tiempoActivoSegundos` aunque el taller no los pide: el nodo muestra en qué worker corre el pod y el tiempo activo deja ver que un pod recreado es nuevo (empieza en 0).

## 3. Cómo se identifica el contenedor

**Decisión:** el `hostname` del contenedor más datos de la **Downward API** (variables `POD_NAME`, `POD_UID`, `POD_IP`, `NODE_NAME`, `POD_NAMESPACE` definidas en el Deployment).

**Por qué:**
- En Kubernetes el hostname del contenedor es el nombre del pod; es la forma estándar de saber quién responde.
- El nodo, la IP y el UID no se pueden averiguar de forma fiable desde dentro del contenedor. La Downward API es el mecanismo oficial para que Kubernetes se los entregue.
- Se descartó extraer el ID del contenedor de `/proc/self/...`: depende del runtime (containerd, Docker) y no es estable entre versiones.

## 4. Cómo se mide la RAM

**Decisión:** leer el **cgroup** del contenedor (`/sys/fs/cgroup/memory.max` y `memory.current` en cgroup v2, con respaldo para cgroup v1). Además se informa el límite declarado en Kubernetes y el heap de la JVM.

**Por qué:**
- Los `resources.limits.memory` del Deployment los aplica containerd con cgroups. Leer el cgroup da el límite y el uso **del contenedor**.
- `/proc/meminfo` o `free` mostrarían la RAM del **nodo completo**, que es un error común en este tipo de ejercicio.
- El heap de la JVM es solo una parte de la memoria del contenedor (también están el metaspace, los hilos y el código nativo); por eso se muestran ambos.

## 5. Cómo se mide el almacenamiento

**Decisión:** espacio total, usado y disponible del sistema de archivos raíz del contenedor (`/`), más el límite `ephemeral-storage` del Deployment.

**Por qué:**
- El sistema de archivos del contenedor (overlay) está sobre el disco del nodo, así que total y disponible reflejan ese disco. Eso es lo que el contenedor puede ver y usar.
- El límite real por contenedor lo fija Kubernetes con `ephemeral-storage`; si el contenedor escribe más, el kubelet lo expulsa. Se muestra para que la respuesta diga cuánto le corresponde al contenedor.

## 6. Lógica separada de Spring y probada

**Decisión:** `LectorRecursos` no usa Spring y recibe por constructor la ruta del cgroup, la del disco y el acceso a las variables de entorno. Las pruebas (`LectorRecursosTest`) le pasan archivos falsos en un directorio temporal.

**Por qué:**
- Se puede probar cgroup v1, v2, "sin límite" y la Downward API en cualquier PC, sin Kubernetes.
- El controlador queda delgado: solo traduce HTTP a llamadas del servicio.

## 7. Servicio sin estado

**Decisión:** no hay base de datos ni sesión; cada petición lee los datos en ese momento.

**Por qué:** cualquier réplica puede atender cualquier petición y un pod puede borrarse en cualquier momento sin perder nada. Es requisito para escalar y para que la recuperación automática no tenga efectos secundarios.

## 8. Imagen del contenedor

**Decisión:** `Dockerfile` en dos etapas: compila con `maven:3.9-eclipse-temurin-21` y ejecuta sobre `eclipse-temurin:21-jre-alpine`, con un usuario sin privilegios (UID 10001).

**Por qué:**
- La compilación ocurre dentro de Docker: nadie del grupo necesita instalar Java ni Maven.
- La imagen final solo lleva el JRE y el `.jar` (unos 100 MB comprimida), lo que acelera la descarga en los workers por Tailscale.
- `-XX:MaxRAMPercentage=75`: el heap se ajusta al límite del contenedor y deja un 25 % para el resto de la JVM. `-XX:+UseSerialGC` usa menos memoria que G1, suficiente para una API pequeña.
- Correr sin root reduce el daño si la aplicación fuera comprometida; el Deployment lo exige con `runAsNonRoot`.

## 9. Publicación de la imagen

**Decisión:** GitHub Actions publica la imagen en GitHub Container Registry (`ghcr.io`) con cada push. Docker Hub queda como alternativa.

**Por qué:** no requiere Docker en los PCs del grupo (que tienen poca RAM y ya corren VMs) ni guardar contraseñas: el flujo usa el token automático de GitHub.

## 10. Deployment y no DaemonSet

**Decisión:** un **Deployment** de 5 réplicas.

**Por qué:**
- Un DaemonSet garantiza un pod por nodo, pero si un nodo se apaga **no** recrea ese pod en otro lado. El taller pide demostrar que los pods siguen funcionando al apagar un worker; eso es justamente lo que hace el Deployment a través de su ReplicaSet.
- 5 réplicas porque hay 5 workers: así cada uno ejecuta un pod.

## 11. Reparto en todos los workers

**Decisión:** `topologySpreadConstraints` con `maxSkew: 1` por `kubernetes.io/hostname`, `whenUnsatisfiable: DoNotSchedule` y `nodeTaintsPolicy: Honor`.

**Por qué:**
- Sin esta regla el scheduler podría poner dos pods en el mismo worker y dejar otro vacío; el taller exige que todos los workers ejecuten algún pod.
- `maxSkew: 1` obliga a que la diferencia de pods entre dos workers sea como máximo uno.
- `nodeTaintsPolicy: Honor` es clave: sin ella, el master (que tiene el taint `control-plane` y no acepta pods) y un worker apagado (taint `unreachable`) contarían como nodos con 0 pods, y el scheduler dejaría pods en `Pending`.

## 12. Recuperación rápida al apagar un worker

**Decisión:** `tolerations` para `node.kubernetes.io/not-ready` y `node.kubernetes.io/unreachable` con `tolerationSeconds: 30`.

**Por qué:** por defecto Kubernetes espera 300 s (5 minutos) antes de sacar los pods de un nodo caído, para no reaccionar ante cortes cortos de red. En la demostración se baja a 30 s para que todo se vea en un minuto y medio. En producción se dejaría más alto.

## 13. Sondas de salud

**Decisión:** tres sondas sobre los endpoints de Actuator:
- **startupProbe** (`/actuator/health/liveness`, hasta 3 minutos): las VMs son lentas y la JVM tarda en arrancar; mientras tanto no se evalúan las otras.
- **livenessProbe**: si la app se cuelga, el kubelet reinicia el contenedor.
- **readinessProbe** (`/actuator/health/readiness`): el Service solo envía tráfico a pods que responden; un pod que arranca o que se está apagando no recibe peticiones.

## 14. Recursos

**Decisión:** `requests` de 100m de CPU y 192 Mi de RAM; `limits` de 1 CPU y 384 Mi.

**Por qué:** los workers tienen unos 1,6 GB de RAM. Con estos valores un worker puede recibir 2 o 3 pods cuando otro está apagado sin quedarse sin memoria. Las `requests` las usa el scheduler para decidir dónde cabe un pod; los `limits` los aplica el kernel.

## 15. Exposición del servicio

**Decisión:** Service tipo **NodePort** en el puerto **30080**.

**Por qué:** el clúster no tiene balanceador de carga externo (no es una nube). NodePort abre el puerto en todos los nodos; cualquier nodo recibe la petición y kube-proxy la reenvía a un pod listo, esté en el nodo que esté. Se puede entrar por la IP de Tailscale del master, que no se apaga en la demostración.

## 16. Apagado ordenado

**Decisión:** `server.shutdown: graceful` con 10 s de espera y `terminationGracePeriodSeconds: 20`.

**Por qué:** al borrar un pod, Kubernetes le envía SIGTERM. La app termina las peticiones en curso antes de cerrarse, y mientras tanto el pod ya salió del Service, así que no se cortan respuestas a medias.

## 17. Documentación de la API

**Decisión:** springdoc-openapi, que genera la especificación OpenAPI y Swagger UI (`/swagger-ui.html`) a partir de las anotaciones `@Operation` y `@Tag`.

**Por qué:** la documentación sale del propio código y no se desactualiza; además permite probar el endpoint desde el navegador.
