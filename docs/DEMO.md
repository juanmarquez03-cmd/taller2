# Guion de la demostración

Todo se ejecuta en el **master**, salvo el apagado del worker. Conviene tener **tres terminales** abiertas en el master:

| Terminal | Para qué |
|---|---|
| T1 | Ver los pods en vivo: `kubectl get pods -n taller -o wide -w` |
| T2 | Bucle de peticiones (ver abajo) |
| T3 | Ejecutar los comandos de la demostración |

Bucle para T2. Muestra quién responde cada segundo y marca si una petición falla:

```bash
while true; do
  curl -s --max-time 2 http://100.105.119.107:30080/api/resumen || echo "[sin respuesta]"
  sleep 1
done
```

Antes de empezar, en T3:

```bash
kubectl get nodes -o wide
```

Los 6 nodos deben estar en `Ready`.

---

## Parte 1. Todos los workers ejecutan un pod

```bash
kubectl get pods -n taller -o wide
```

Se ven 5 pods en `Running`, **cada uno en un worker distinto** (columna `NODE`). En T2 los nombres de pod y de nodo van cambiando: el Service reparte las peticiones entre todos.

Para verlo agrupado por nodo:

```bash
kubectl get pods -n taller -o custom-columns=POD:.metadata.name,NODO:.spec.nodeName,ESTADO:.status.phase
```

También se puede abrir `http://100.105.119.107:30080/api/info` en el navegador (con Tailscale en el PC) y recargar.

**Qué decir:** el Deployment pide 5 réplicas. La regla `topologySpreadConstraints` obliga al scheduler a repartirlas con una diferencia máxima de un pod entre workers, y como hay 5 workers queda uno en cada uno. El master no recibe pods porque tiene el taint `node-role.kubernetes.io/control-plane:NoSchedule`.

---

## Parte 2. Borrar pods

### 2.1 Borrar un pod

En T3 (copiar el nombre de un pod de T1):

```bash
kubectl delete pod -n taller NOMBRE_DEL_POD
```

Qué se ve:
- **T1:** el pod pasa a `Terminating` y casi al mismo tiempo aparece uno nuevo con **otro nombre** en `Pending` → `ContainerCreating` → `Running`, y luego `READY 1/1`. Normalmente queda en el mismo worker, porque es el único que quedó sin pod.
- **T2:** el servicio **no se interrumpe**; los otros 4 pods siguen respondiendo. El pod nuevo aparece con `activo 0 s`.

### 2.2 Borrar todos los pods a la vez

```bash
kubectl delete pods -n taller -l app=info-api
```

Se recrean los 5. Aquí sí puede haber unos segundos de `[sin respuesta]` en T2, porque durante el arranque de la JVM no hay ningún pod listo. Es un buen contraste: **con réplicas no hay caída; sin ninguna réplica viva, sí**.

### 2.3 Comandos para mostrar quién lo hizo

```bash
kubectl get deployment,replicaset -n taller
kubectl describe replicaset -n taller -l app=info-api | tail -15
kubectl get events -n taller --sort-by=.lastTimestamp | tail -15
```

En los eventos aparecen `SuccessfulDelete` y `SuccessfulCreate` del ReplicaSet, `Scheduled` del scheduler y `Pulled`/`Started` del kubelet.

### Explicación: borrado y recuperación automática de pods

Kubernetes funciona por **estado deseado**. El Deployment declara "quiero 5 réplicas de esta imagen" y eso se guarda en **etcd**. Unos procesos llamados **controladores** comparan todo el tiempo el estado deseado con el real y corrigen la diferencia (bucle de reconciliación).

Lo que pasa al borrar un pod, paso a paso:

1. `kubectl delete pod` le pide al **API server** borrar el pod. El API server lo marca para borrado (le pone `deletionTimestamp`) y el pod pasa a `Terminating`.
2. El **EndpointSlice controller** saca ese pod de la lista del Service, y **kube-proxy** deja de enviarle tráfico. Por eso no se pierden peticiones.
3. El **kubelet** del worker le envía SIGTERM al contenedor. Spring Boot termina las peticiones en curso (apagado ordenado) y se cierra; si no lo hace en 20 s, recibe SIGKILL.
4. Al mismo tiempo, el **controlador del ReplicaSet** (dentro del `kube-controller-manager`) ve que hay 4 pods activos y se desean 5. Crea un pod nuevo a partir de la plantilla, con nombre nuevo y UID nuevo.
5. El **scheduler** elige un nodo para el pod nuevo. Respeta la regla de reparto, así que lo pone en el worker que quedó sin pod.
6. El **kubelet** de ese worker le pide a **containerd** crear el contenedor (la imagen ya está descargada), le asigna una IP de la red de pods (**flannel**) y lo arranca.
7. Cuando la **readinessProbe** responde bien, el pod entra al Service y empieza a recibir peticiones.

Ideas clave:
- No es el pod el que "revive": es **otro pod nuevo** (nombre, UID e IP distintos). Los pods son desechables.
- Quien lo recrea es el **ReplicaSet**, que el Deployment crea y administra. Un pod creado a mano, sin controlador, no se recrearía.
- Funciona porque la app **no guarda estado**: el pod nuevo es igual de válido que el anterior.

---

## Parte 3. Apagar un worker

Elegir un worker, por ejemplo `k8s-worker-david2`. Antes, ubicar qué pod tiene:

```bash
kubectl get pods -n taller -o wide | grep david2
```

### 3.1 Apagar

En la **VM del worker** (su dueño):

```bash
sudo poweroff
```

En T3 del master, observar los nodos:

```bash
kubectl get nodes -w
```

### 3.2 Qué se ve y cuándo (aproximado)

| Tiempo | Qué pasa | Dónde se ve |
|---|---|---|
| 0 s | El worker se apaga | — |
| 0–50 s | El nodo sigue figurando `Ready`; algunas peticiones al pod de ese nodo pueden fallar | T2 puede mostrar algún `[sin respuesta]` |
| ~40–50 s | El nodo pasa a `NotReady`; su pod sale del Service | `kubectl get nodes`; T2 vuelve a responder siempre |
| +30 s | El pod del nodo apagado pasa a `Terminating` y se crea uno nuevo en otro worker | T1 |
| ~1,5–2 min | El pod nuevo queda `Running 1/1` | T1: 5 pods listos en 4 workers |

El pod viejo se queda en `Terminating` mientras el nodo esté apagado: el API server no puede confirmar que el contenedor se detuvo, porque el kubelet no responde. No afecta, porque ya no cuenta como réplica ni recibe tráfico.

```bash
kubectl get pods -n taller -o wide
kubectl describe node k8s-worker-david2 | grep -A3 Taints
```

### 3.3 Volver a encender el worker

Encender la VM. En 1 o 2 minutos el nodo vuelve a `Ready` y el pod viejo en `Terminating` desaparece.

Los pods **no se reparten solos** de vuelta: Kubernetes no mueve pods que funcionan bien. Para redistribuir:

```bash
kubectl rollout restart deployment/info-api -n taller
kubectl rollout status deployment/info-api -n taller
```

El reinicio es gradual (`maxUnavailable: 0`, `maxSurge: 1`): crea un pod nuevo, espera que esté listo y recién entonces borra uno viejo. En T2 no hay cortes. Al terminar vuelve a haber un pod por worker.

### Explicación: qué pasa cuando se apaga un worker

1. **Latidos del kubelet.** Cada kubelet renueva un objeto `Lease` en el namespace `kube-node-lease` cada 10 s aproximadamente. Es su forma de decir "sigo vivo".
2. **Detección.** El **node lifecycle controller** (en el `kube-controller-manager`) vigila esos latidos. Si pasan unos 40–50 s sin renovación (`node-monitor-grace-period`), marca el nodo con condición `Ready=Unknown`, que `kubectl` muestra como `NotReady`.
3. **Taints.** Ese mismo controlador le pone al nodo los taints `node.kubernetes.io/unreachable:NoSchedule` (no programar pods nuevos ahí) y `node.kubernetes.io/unreachable:NoExecute` (sacar los que hay).
4. **Sacar los pods del Service.** Los pods de ese nodo quedan con `Ready=false`; el EndpointSlice controller los quita del Service y kube-proxy, en todos los nodos, deja de enviarles tráfico.
5. **Desalojo.** Nuestros pods toleran el taint `NoExecute` solo 30 s (`tolerationSeconds: 30`). Pasado ese tiempo, el controlador de desalojo los marca para borrado.
6. **Reemplazo.** El **ReplicaSet** ve 4 réplicas activas de 5 deseadas y crea una nueva. El **scheduler** la pone en un worker sano: descarta el apagado por el taint y aplica la regla de reparto.
7. **Recuperación.** El pod nuevo arranca, pasa la readinessProbe y entra al Service. Hay de nuevo 5 réplicas, en 4 workers.

Por qué el usuario casi no nota nada:
- Hay **varias réplicas** en **varios nodos**: la caída de uno no deja el servicio sin pods.
- El **Service** es una dirección estable delante de pods que cambian, y solo apunta a los listos.
- Se entra por el **master**, que sigue encendido. Si se usara la IP del worker apagado, esa dirección dejaría de responder, aunque los pods sigan vivos en otros nodos.

Límites que conviene mencionar:
- El **master** es un punto único de falla: si se apaga, los pods que ya corren siguen funcionando, pero nadie detecta caídas ni recrea pods hasta que vuelva.
- Los primeros ~40–50 s algunas peticiones pueden fallar, mientras Kubernetes aún no sabe que el nodo cayó.
- Por defecto la espera es de 300 s; aquí se bajó a 30 s solo para la demostración.

---

## Preguntas probables y respuestas cortas

**¿Qué diferencia hay entre Deployment, ReplicaSet y Pod?**
El Pod es la unidad que corre uno o más contenedores. El ReplicaSet mantiene una cantidad fija de pods iguales. El Deployment administra ReplicaSets y agrega actualizaciones graduales y vuelta atrás.

**¿Por qué no un DaemonSet?**
Pone un pod por nodo, pero si el nodo se cae no lo recrea en otro. Para demostrar tolerancia a fallas se necesita que la réplica se mueva, y eso lo hace el ReplicaSet del Deployment.

**¿Cómo sabe el pod en qué nodo está?**
Por la Downward API: el Deployment define variables de entorno (`NODE_NAME`, `POD_NAME`...) que Kubernetes llena al crear el pod.

**¿Por qué la RAM se lee del cgroup y no de `/proc/meminfo`?**
`/proc/meminfo` muestra la memoria del nodo completo. El límite del contenedor lo aplica el kernel con cgroups, así que el cgroup tiene el límite y el uso reales del contenedor.

**¿Qué pasa si se borra el Deployment?**
Se borran su ReplicaSet y todos sus pods, y nadie los recrea, porque ya no existe el estado deseado.

**¿Qué hace kube-proxy?**
Programa reglas de red en cada nodo para que el tráfico al Service (y al NodePort 30080) llegue a alguno de los pods listos, esté en el nodo que esté.

**¿Qué hace flannel?**
Crea la red de pods: le da a cada pod una IP y conecta pods de nodos distintos. En este clúster ese tráfico viaja por la interfaz `tailscale0`.

**¿Qué papel tiene Tailscale?**
Une las VMs de los tres integrantes, que están en redes distintas, en una red privada cifrada. Cada nodo se registró en Kubernetes con su IP de Tailscale (`--node-ip`).

**¿Por qué 5 réplicas?**
Una por worker. Se puede escalar con `kubectl scale deployment/info-api -n taller --replicas=10`, y la regla de reparto deja 2 por worker.

---

## Comandos de emergencia

```bash
# Ver por qué un pod no arranca
kubectl describe pod -n taller NOMBRE
kubectl logs -n taller NOMBRE --previous

# Volver a crear todo desde cero
kubectl delete -f k8s/ && kubectl apply -f k8s/

# Nodo que no vuelve a Ready (en la VM del worker)
tailscale status
sudo systemctl restart kubelet
```
