# Info API: taller de Kubernetes

**Sistemas Distribuidos · UPTC · Ingeniería de Sistemas y Computación**

Integrantes:
- Nicolas David Castro Cely
- Juan David Márquez Pinto
- Andres Mauricio Sanchez Sanchez

REST API en Spring Boot que responde con la información del contenedor que atiende cada petición. Se despliega en un clúster Kubernetes de 6 nodos (1 master y 5 workers) unidos por Tailscale, y sirve para demostrar alta disponibilidad: los pods se reparten entre todos los workers y Kubernetes los recupera cuando se borran o cuando se apaga un worker.

Documentos relacionados:
- [docs/DECISIONES.md](docs/DECISIONES.md): decisiones técnicas tomadas y por qué, y uso de IA.
- [docs/DEMO.md](docs/DEMO.md): guion de la demostración y explicación de cómo funciona cada caso.

---

## 1. Endpoints

| Método | Ruta | Respuesta |
|---|---|---|
| GET | `/api/info` (también `/`) | JSON con los 5 datos del taller |
| GET | `/api/resumen` | Una línea de texto: hora, pod, nodo y segundos activo |
| GET | `/swagger-ui.html` | Documentación interactiva (Swagger UI) |
| GET | `/v3/api-docs` | Especificación OpenAPI en JSON |
| GET | `/actuator/health/liveness` | Sonda de vida (la usa Kubernetes) |
| GET | `/actuator/health/readiness` | Sonda de disponibilidad (la usa Kubernetes) |

### Ejemplo de `GET /api/info`

```json
{
  "horaContenedor": "2026-11-03T08:15:30.123-05:00",
  "zonaHoraria": "America/Bogota",
  "grupo": "Sistemas Distribuidos - UPTC",
  "integrantes": [
    "Nicolas David Castro Cely",
    "Juan David Márquez Pinto",
    "Andres Mauricio Sanchez Sanchez"
  ],
  "contenedor": {
    "hostname": "info-api-6c9d8f7b5-x2k4p",
    "pod": "info-api-6c9d8f7b5-x2k4p",
    "uidPod": "3f1c2a9e-...",
    "namespace": "taller",
    "ipPod": "10.244.4.12",
    "nodo": "k8s-worker-david1",
    "tiempoActivoSegundos": 842
  },
  "almacenamiento": {
    "puntoMontaje": "/",
    "total":      { "bytes": 19166093312, "legible": "17.9 GiB" },
    "usado":      { "bytes": 5905580032,  "legible": "5.5 GiB" },
    "disponible": { "bytes": 12304384000, "legible": "11.5 GiB" },
    "limiteEfimeroKubernetes": { "bytes": 536870912, "legible": "512.0 MiB" }
  },
  "memoria": {
    "fuente": "cgroup v2",
    "limiteContenedor": { "bytes": 402653184, "legible": "384.0 MiB" },
    "usoContenedor":    { "bytes": 171966464, "legible": "164.0 MiB" },
    "limiteKubernetes": { "bytes": 402653184, "legible": "384.0 MiB" },
    "heapMaximoJvm":    { "bytes": 301989888, "legible": "288.0 MiB" },
    "heapUsadoJvm":     { "bytes": 48234496,  "legible": "46.0 MiB" }
  }
}
```

Correspondencia con lo que pide el taller:

| Pide el taller | Campo |
|---|---|
| Hora del contenedor | `horaContenedor`, `zonaHoraria` |
| Nombres de los integrantes | `integrantes` |
| Nombre o id del contenedor que responde | `contenedor.hostname` / `contenedor.pod` (y `nodo`, `uidPod`) |
| Cantidad de almacenamiento | `almacenamiento` |
| Cantidad de RAM | `memoria` |

---

## 2. Estructura del proyecto

```
info-api/
├── pom.xml                         Dependencias (Spring Boot 4.1, Java 21)
├── Dockerfile                      Compila con Maven y genera la imagen final
├── .github/workflows/imagen.yml    Publica la imagen en ghcr.io con cada push
├── k8s/
│   ├── 01-namespace.yaml           Namespace "taller"
│   ├── 02-deployment.yaml          5 réplicas repartidas en los 5 workers
│   └── 03-service.yaml             NodePort 30080
├── docs/
│   ├── DECISIONES.md
│   └── DEMO.md
└── src/
    ├── main/java/co/edu/uptc/distribuidos/infoapi/
    │   ├── InfoApiApplication.java     Arranque y metadatos de OpenAPI
    │   ├── config/TallerProperties.java  Integrantes (desde application.yml)
    │   ├── config/RecursosConfig.java    Registra el lector de recursos
    │   ├── controller/InfoController.java  Endpoints HTTP
    │   ├── service/InfoService.java        Arma la respuesta
    │   ├── service/LectorRecursos.java     Lee cgroup, disco y Downward API
    │   └── model/                          Records de la respuesta JSON
    ├── main/resources/application.yml
    └── test/java/.../LectorRecursosTest.java
```

---

## 3. Publicar la imagen

Los workers descargan la imagen de un registro público, así que primero hay que publicarla. Hay dos opciones; la A no necesita instalar nada en el PC.

### Opción A (recomendada): GitHub Actions + GitHub Container Registry

1. Crear un repositorio **público** en GitHub (por ejemplo `info-api`) y subir esta carpeta:
   ```bash
   git init
   git add .
   git commit -m "Info API del taller de Kubernetes"
   git branch -M main
   git remote add origin https://github.com/USUARIO_GITHUB/info-api.git
   git push -u origin main
   ```
2. En GitHub, pestaña **Actions**: el flujo "Publicar imagen" corre solo. Tarda unos 3 minutos. Debe terminar en verde.
3. Hacer pública la imagen (una sola vez): en tu perfil de GitHub → **Packages** → `info-api` → **Package settings** → **Change visibility** → **Public**. Si queda privada, los workers mostrarán `ImagePullBackOff`.
4. La imagen queda como `ghcr.io/USUARIO_GITHUB/info-api:latest` (usuario en minúsculas).

### Opción B: Docker en el PC + Docker Hub

Con Docker Desktop instalado y una cuenta en hub.docker.com:

```bash
docker build -t USUARIO_DOCKERHUB/info-api:1.0 .
docker login
docker push USUARIO_DOCKERHUB/info-api:1.0
```

### Después de cualquiera de las dos

Editar la línea `image:` de `k8s/02-deployment.yaml` con el nombre real de la imagen.

---

## 4. Desplegar en el clúster

En el **master** (donde está `kubectl` configurado), con la carpeta `k8s/` copiada o el repositorio clonado:

```bash
git clone https://github.com/USUARIO_GITHUB/info-api.git
cd info-api
kubectl apply -f k8s/
kubectl get pods -n taller -o wide -w
```

En 1 a 3 minutos los 5 pods deben quedar en `Running` y `READY 1/1`, **cada uno en un worker distinto** (columna `NODE`). La primera vez tarda más porque cada worker descarga la imagen.

---

## 5. Probar

Desde el master o cualquier nodo (todos tienen el puerto 30080 abierto):

```bash
curl -s http://100.105.119.107:30080/api/info
curl -s http://100.105.119.107:30080/api/resumen
```

Para verlo en el **navegador** el PC tiene que estar en la red de Tailscale del grupo: instalar Tailscale en el PC, unirlo con una clave que genere el administrador del master, y abrir:

- `http://100.105.119.107:30080/api/info`
- `http://100.105.119.107:30080/swagger-ui.html`

Al recargar varias veces cambia el pod que responde: el Service reparte las peticiones entre los 5.

---

## 6. Problemas frecuentes

| Síntoma | Causa probable | Solución |
|---|---|---|
| `ImagePullBackOff` / `ErrImagePull` | Imagen privada o nombre mal escrito en `image:` | Hacer pública la imagen (paso 3.A.3) y revisar el nombre con `kubectl describe pod -n taller NOMBRE` |
| Pods en `Pending` | Falta RAM en los workers o hay menos de 5 workers en Ready | `kubectl describe pod -n taller NOMBRE` (sección Events) y `kubectl get nodes` |
| `CrashLoopBackOff` | La app falla al arrancar | `kubectl logs -n taller NOMBRE --previous` |
| Pods `Running` pero `READY 0/1` mucho tiempo | La JVM tarda en arrancar en VMs lentas | Esperar; la startupProbe da hasta 3 min |
| `curl` al 30080 se queda colgado solo a veces | El nodo de entrada no alcanza pods de otros nodos (red de pods / flannel) | Revisar `kubectl get pods -n kube-flannel -o wide` y que todos los nodos estén en Ready |
| Maven no encuentra `springdoc-openapi-starter-webmvc-ui:3.2.0` al construir | Versión no publicada aún en el registro | Cambiar `springdoc.version` a `3.1.0` en `pom.xml` |

Comandos útiles:

```bash
kubectl get all -n taller
kubectl logs -n taller -l app=info-api --tail=20
kubectl get events -n taller --sort-by=.lastTimestamp
kubectl delete -f k8s/          # borra todo lo del taller
```

---

## 7. Ejecutar sin Kubernetes (opcional)

Con Java 21 y Maven:

```bash
mvn spring-boot:run
# http://localhost:8080/api/info
```

Fuera de Kubernetes los campos `pod`, `nodo`, `ipPod` y los límites de Kubernetes salen en `null`, porque los entrega la Downward API del Deployment.

---

## 8. Uso de IA

Todo el código, los manifiestos y esta documentación fueron generados con **Claude (Anthropic)**, como exige el taller. El detalle de las decisiones y de cómo se usó la IA está en [docs/DECISIONES.md](docs/DECISIONES.md).
