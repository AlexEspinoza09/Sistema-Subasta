# Sistema de Subasta Distribuida con Algoritmo Bully

## Índice
1. [Arquitectura General](#arquitectura-general)
2. [Algoritmo Bully - Elección de Coordinador](#algoritmo-bully---elección-de-coordinador)
3. [Tolerancia a Fallos](#tolerancia-a-fallos)
4. [Replicación de Estado](#replicación-de-estado)
5. [Escenarios de Funcionamiento](#escenarios-de-funcionamiento)
6. [Protocolo de Mensajes](#protocolo-de-mensajes)

---

## Arquitectura General

### Componentes Principales

**ServidorSubastaBully.java**
- Servidor principal que puede actuar como COORDINADOR o PARTICIPANTE
- Acepta conexiones de clientes cuando es coordinador
- Redirige clientes al coordinador cuando es participante

**GestorEleccion.java**
- Implementa el algoritmo Bully
- Gestiona la replicación de estado entre nodos
- Detecta fallos mediante heartbeats
- Coordina elecciones

**EstadoSubastaReplicado.java**
- Almacena las ofertas de todos los clientes: `Map<String, Double>`
- Cliente identificado por `IP:Puerto` (ejemplo: `192.168.1.100:52341`)
- Thread-safe usando `ConcurrentHashMap`
- Incluye timestamp para sincronización

**NodoSubasta.java**
- Representa un nodo del sistema distribuido
- Contiene: ID, host, puerto de clientes, puerto de coordinación

---

## Algoritmo Bully - Elección de Coordinador

### Principio Básico

**Regla de oro**: El nodo con el **ID más alto** siempre gana y se convierte en coordinador.

### Configuración Inicial (nodos.conf)

```
# id,host,puertoClientes,puertoCoordinacion
1,localhost,8080,9080
2,localhost,8081,9081
3,localhost,8082,9082
```

- **Nodo 3**: ID más alto → Coordinador por defecto
- **Nodo 2**: ID medio → Participante
- **Nodo 1**: ID más bajo → Participante

### Proceso de Elección (Paso a Paso)

#### 1. Inicio de Elección

Un nodo inicia una elección cuando:
- Detecta que el coordinador actual no responde (fallo de heartbeat)
- Se levanta por primera vez y no conoce al coordinador

```java
// En GestorEleccion.java
public void iniciarEleccion() {
    System.out.println("[BULLY] Nodo " + nodoLocal.getId() + " inicia eleccion");

    // Buscar nodos con ID superior
    List<NodoSubasta> nodosSuperiores = obtenerNodosSuperiores();

    if (nodosSuperiores.isEmpty()) {
        // Soy el de mayor ID, me convierto en coordinador
        convertirseEnCoordinador();
    } else {
        // Enviar ELECTION a todos los nodos superiores
        enviarMensajesElection(nodosSuperiores);
    }
}
```

#### 2. Envío de Mensajes ELECTION

El nodo que inicia la elección envía mensajes `ELECTION` a todos los nodos con ID superior:

```
Nodo 1 detecta fallo del coordinador
    ↓
Nodo 1 envía ELECTION a Nodo 2 y Nodo 3
    ↓
Espera respuestas OK
```

#### 3. Respuesta OK

Los nodos con ID superior responden con `OK` y también inician su propia elección:

```
Nodo 2 recibe ELECTION de Nodo 1
    ↓
Nodo 2 responde OK a Nodo 1
    ↓
Nodo 2 inicia su propia elección (envía ELECTION a Nodo 3)
    ↓
Nodo 3 recibe ELECTION de Nodo 2
    ↓
Nodo 3 responde OK a Nodo 2
    ↓
Nodo 3 inicia su propia elección
    ↓
Nodo 3 no tiene nodos superiores → Se convierte en COORDINADOR
```

#### 4. Anuncio del Coordinador

El nodo que gana la elección envía mensaje `COORDINATOR` a todos:

```java
private void convertirseEnCoordinador() {
    idCoordinadorActual = nodoLocal.getId();
    estado = EstadoNodo.COORDINADOR;

    // Solicitar estado si mi estado está vacío
    if (!estadoSubasta.tieneOfertas()) {
        solicitarEstadoDeNodos();
    }

    // Anunciar a todos los nodos
    MensajeBully anuncio = new MensajeBully(
        TipoMensaje.COORDINATOR,
        nodoLocal.getId(),
        nodoLocal.getId(),
        nodoLocal.getHost(),
        nodoLocal.getPuertoClientes()
    );

    broadcast(anuncio);

    // Iniciar heartbeats
    iniciarHeartbeats();
}
```

#### 5. Resultado de la Elección

```
ANTES:
    Nodo 1: PARTICIPANTE
    Nodo 2: PARTICIPANTE
    Nodo 3: COORDINADOR ← (caído)

ELECCIÓN:
    Nodo 1 detecta fallo
        ↓
    Nodo 1 → ELECTION → Nodo 2
        ↓
    Nodo 2 → OK → Nodo 1
    Nodo 2 → ELECTION → (no hay superiores)
        ↓
    Nodo 2 se convierte en COORDINADOR

DESPUÉS:
    Nodo 1: PARTICIPANTE
    Nodo 2: COORDINADOR ← (nuevo)
    Nodo 3: (caído)
```

---

## Tolerancia a Fallos

### 1. Detección de Fallos - Heartbeats

El coordinador envía **heartbeats cada 5 segundos** a todos los nodos:

```java
// En GestorEleccion.java
private void iniciarHeartbeats() {
    heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
    heartbeatExecutor.scheduleAtFixedRate(() -> {
        MensajeBully heartbeat = new MensajeBully(
            TipoMensaje.HEARTBEAT,
            nodoLocal.getId()
        );
        enviarATodos(heartbeat);
    }, 0, 5, TimeUnit.SECONDS);
}
```

Los participantes verifican si el coordinador sigue vivo:

```java
// En MonitorCoordinador.java
if (tiempoSinHeartbeat > TIMEOUT_HEARTBEAT) {
    System.out.println("[MONITOR] Coordinador no responde!");
    gestorEleccion.iniciarEleccion();
}
```

### 2. Escenario: Coordinador Cae

**Situación Inicial:**
```
Nodo 1 (PARTICIPANTE) ← Cliente conectado aquí
Nodo 2 (PARTICIPANTE)
Nodo 3 (COORDINADOR) ← Acepta ofertas y gestiona subasta
```

**Paso 1: Fallo Detectado**
```
t=0s:  Nodo 3 se cae
t=5s:  Nodo 1 y Nodo 2 no reciben heartbeat
t=10s: Nodo 1 y Nodo 2 no reciben heartbeat
t=15s: TIMEOUT - Nodo 2 inicia elección
```

**Paso 2: Nueva Elección**
```
Nodo 2 → ELECTION → (no hay nodos superiores)
Nodo 2 → Se convierte en COORDINADOR
Nodo 2 → COORDINATOR → Nodo 1
```

**Paso 3: Recuperación de Estado**
```
Nodo 2 detecta que su estado está vacío
    ↓
Nodo 2 → REQUEST_ESTADO → Nodo 1
    ↓
Nodo 1 → SYNC_ESTADO → Nodo 2 (envía todas las ofertas)
    ↓
Nodo 2 sincroniza su estado local
    ↓
Nodo 2 ahora tiene todas las ofertas y puede continuar la subasta
```

### 3. Escenario: Nodo Caído Se Recupera

**Situación:**
```
Nodo 1 (PARTICIPANTE)
Nodo 2 (COORDINADOR) ← Tiene 3 ofertas: $50, $75, $100
Nodo 3 (CAÍDO) ← Se va a levantar
```

**Paso 1: Nodo 3 Se Levanta**
```
Nodo 3 se inicia
    ↓
Estado local vacío (nuevo EstadoSubastaReplicado())
    ↓
Inicia proceso de elección
```

**Paso 2: Elección Automática**
```
Nodo 3 → ELECTION → (no hay superiores, ID=3 es el más alto)
    ↓
Nodo 3 → Se convierte en COORDINADOR
    ↓
Nodo 3 → COORDINATOR → Nodo 1, Nodo 2
```

**Paso 3: Solicitud de Estado**
```java
// En GestorEleccion.java - convertirseEnCoordinador()
if (!estadoSubasta.tieneOfertas()) {
    System.out.println("[SYNC] Estado local vacío. Solicitando estado...");
    solicitarEstadoDeNodos();
}
```

```
Nodo 3 → REQUEST_ESTADO → Nodo 1
Nodo 1 → SYNC_ESTADO → Nodo 3 (timestamp: 100, ofertas: 2)

Nodo 3 → REQUEST_ESTADO → Nodo 2
Nodo 2 → SYNC_ESTADO → Nodo 3 (timestamp: 150, ofertas: 3)
```

**Paso 4: Sincronización Inteligente**
```java
// Nodo 3 compara los estados recibidos
Estado de Nodo 1: timestamp=100, 2 ofertas
Estado de Nodo 2: timestamp=150, 3 ofertas ← MÁS RECIENTE
    ↓
Nodo 3 toma el estado de Nodo 2
    ↓
Nodo 3 ahora tiene las 3 ofertas: $50, $75, $100
```

**Resultado Final:**
```
Nodo 1 (PARTICIPANTE) - 2 ofertas
Nodo 2 (PARTICIPANTE) - 3 ofertas
Nodo 3 (COORDINADOR)  - 3 ofertas ← Recuperado exitosamente
```

---

## Replicación de Estado

### Sincronización Basada en Timestamp

Cada vez que se agrega una oferta, el timestamp se actualiza:

```java
// En EstadoSubastaReplicado.java
public synchronized void agregarOferta(String clienteId, double monto) {
    ofertas.put(clienteId, monto);
    timestampUltimaActualizacion = System.currentTimeMillis();
}
```

### Lógica de Sincronización

```java
public synchronized void sincronizarCon(EstadoSubastaReplicado otroEstado) {
    // CASO 1: Mi estado está vacío → Aceptar estado remoto
    if (this.ofertas.isEmpty() && !otroEstado.ofertas.isEmpty()) {
        this.ofertas = new ConcurrentHashMap<>(otroEstado.ofertas);
        this.timestampUltimaActualizacion = otroEstado.timestampUltimaActualizacion;
    }
    // CASO 2: Estado remoto más reciente → Sincronizar
    else if (otroEstado.timestampUltimaActualizacion > this.timestampUltimaActualizacion) {
        this.ofertas = new ConcurrentHashMap<>(otroEstado.ofertas);
        this.timestampUltimaActualizacion = otroEstado.timestampUltimaActualizacion;
    }
    // CASO 3: Mi estado es más reciente → No hacer nada
}
```

### Replicación Cuando Llega una Oferta

```java
// En ServidorSubastaBully.java
public boolean actualizarPropuestaMasAlta(double nuevaPropuesta, String clienteId) {
    synchronized(lockSubasta) {
        // 1. Agregar al estado local
        gestorEleccion.getEstadoSubasta().agregarOferta(clienteId, nuevaPropuesta);

        // 2. Replicar a todos los nodos
        gestorEleccion.replicarEstado();

        return esGanador;
    }
}
```

```
Cliente envía oferta $120
    ↓
Nodo 3 (COORDINADOR) recibe la oferta
    ↓
Nodo 3 agrega al estado local: {
    "192.168.1.5:52341" → $120
}
    ↓
Nodo 3 → SYNC_ESTADO → Nodo 1, Nodo 2
    ↓
Todos los nodos tienen la misma oferta
```

---

## Escenarios de Funcionamiento

### Escenario 1: Sistema Normal

```
ESTADO INICIAL:
    Nodo 1 (PARTICIPANTE) - Puerto 8080
    Nodo 2 (PARTICIPANTE) - Puerto 8081
    Nodo 3 (COORDINADOR)  - Puerto 8082

CLIENTE CONECTA:
    Cliente → Conecta a localhost:8080 (Nodo 1)
        ↓
    Nodo 1 (PARTICIPANTE) → Detecta que no es coordinador
        ↓
    Nodo 1 → REDIRECCION:localhost:8082 → Cliente
        ↓
    Cliente → Reconecta a localhost:8082 (Nodo 3)
        ↓
    Nodo 3 → Acepta cliente y gestiona ofertas

OFERTA:
    Cliente → $100 → Nodo 3
        ↓
    Nodo 3 → Agrega oferta (IP:Puerto → $100)
        ↓
    Nodo 3 → SYNC_ESTADO → Nodo 1, Nodo 2
        ↓
    Todos tienen la oferta replicada
```

### Escenario 2: Failover con Cliente Conectado

```
ESTADO INICIAL:
    Cliente conectado a Nodo 3 (COORDINADOR)
    Cliente hizo oferta: $100
    Estado replicado en todos los nodos

FALLO:
    t=0s: Nodo 3 se cae

DETECCIÓN:
    t=15s: Nodo 2 detecta timeout de heartbeat
    t=15s: Nodo 2 inicia elección

NUEVA ELECCIÓN:
    Nodo 2 → No hay superiores
    Nodo 2 → Se convierte en COORDINADOR
    Nodo 2 → Solicita estado
    Nodo 2 → Recibe estado de Nodo 1 (tiene oferta de $100)
    Nodo 2 → Sincroniza estado local

CLIENTE:
    Cliente detecta desconexión
    Cliente → Intenta reconectar a cualquier nodo
    Cliente → Conecta a Nodo 2
    Nodo 2 (ahora COORDINADOR) → Acepta cliente
    Cliente puede continuar haciendo ofertas

RESULTADO:
    La subasta continúa sin pérdida de datos
    La oferta de $100 se mantiene
```

### Escenario 3: Nodo con ID Alto Se Recupera

```
ESTADO ACTUAL:
    Nodo 1 (PARTICIPANTE)
    Nodo 2 (COORDINADOR) ← Actual coordinador
    Nodo 3 (CAÍDO)

    Estado: {
        "192.168.1.5:52341" → $100,
        "192.168.1.5:52342" → $150
    }

RECUPERACIÓN:
    Nodo 3 se levanta
        ↓
    Nodo 3 → ELECTION → (soy el más alto)
        ↓
    Nodo 3 → COORDINATOR → Nodo 1, Nodo 2
        ↓
    Nodo 2 recibe COORDINATOR
        ↓
    Nodo 2 → Cambia a PARTICIPANTE
        ↓
    Nodo 2 → Cierra conexiones de clientes
        ↓
    Nodo 2 → Redirige futuros clientes a Nodo 3
        ↓
    Nodo 3 → REQUEST_ESTADO → Nodo 1, Nodo 2
        ↓
    Nodo 3 → Recibe estados y toma el más reciente
        ↓
    Nodo 3 ahora tiene todas las ofertas
        ↓
    Nodo 3 → LISTO para aceptar clientes

TRANSICIÓN SUAVE:
    Los clientes ya conectados reciben el resultado final del Nodo 2
    Nuevos clientes son redirigidos automáticamente al Nodo 3
```

---

## Protocolo de Mensajes

### Tipos de Mensajes (MensajeBully.java)

| Tipo | Propósito | Emisor → Receptor |
|------|-----------|-------------------|
| **ELECTION** | Iniciar proceso de elección | Nodo inferior → Nodos superiores |
| **OK** | Respuesta a ELECTION | Nodo superior → Nodo inferior |
| **COORDINATOR** | Anunciar nuevo coordinador | Nuevo coordinador → Todos |
| **HEARTBEAT** | Verificar que coordinador está vivo | Coordinador → Todos |
| **PING** | Solicitar estado de un nodo | Cualquiera → Cualquiera |
| **ESTADO** | Responder con información del nodo | Cualquiera → Solicitante |
| **REDIRECCION** | Redirigir cliente al coordinador | Participante → Cliente |
| **SYNC_ESTADO** | Enviar estado replicado | Cualquiera → Cualquiera |
| **REQUEST_ESTADO** | Solicitar estado de subasta | Nuevo coordinador → Todos |

### Ejemplos de Mensajes

**ELECTION:**
```java
MensajeBully msg = new MensajeBully(TipoMensaje.ELECTION, 2); // Nodo 2 inicia
// Enviado a: Nodo 3
```

**COORDINATOR:**
```java
MensajeBully msg = new MensajeBully(
    TipoMensaje.COORDINATOR,
    3,              // ID emisor
    3,              // ID coordinador
    "localhost",    // Host
    8082            // Puerto
);
// Enviado a: Todos los nodos
```

**SYNC_ESTADO:**
```java
EstadoSubastaReplicado estado = gestorEleccion.getEstadoSubasta();
MensajeBully msg = new MensajeBully(
    TipoMensaje.SYNC_ESTADO,
    3,
    estado  // Estado completo con ofertas
);
// Enviado a: Todos los nodos
```

---

## Flujo Completo de una Subasta con Failover

```
T=0s: Sistema inicia
    ├─ Nodo 1 (8080) → PARTICIPANTE
    ├─ Nodo 2 (8081) → PARTICIPANTE
    └─ Nodo 3 (8082) → COORDINADOR

T=10s: Cliente A conecta
    Cliente A → Nodo 3
    Cliente A → Oferta $100
    Estado: {"192.168.1.5:52341" → $100}
    Replicado en: Nodo 1, Nodo 2, Nodo 3

T=20s: Cliente B conecta
    Cliente B → Nodo 1
    Nodo 1 → REDIRECCION → Cliente B
    Cliente B → Nodo 3
    Cliente B → Oferta $150
    Estado: {
        "192.168.1.5:52341" → $100,
        "192.168.1.5:52342" → $150
    }
    Replicado en: Nodo 1, Nodo 2, Nodo 3

T=30s: ¡Nodo 3 se cae!
    Clientes A y B pierden conexión

T=45s: Nodo 2 detecta fallo
    Nodo 2 → Inicia elección
    Nodo 2 → Se convierte en COORDINADOR
    Nodo 2 → Ya tiene estado replicado (2 ofertas)

T=50s: Clientes reconectan
    Cliente A → Nodo 2 (nuevo coordinador)
    Cliente B → Nodo 1 → REDIRECCION → Nodo 2
    Estado preservado: {
        "192.168.1.5:52341" → $100,
        "192.168.1.5:52342" → $150
    }

T=60s: Cliente C conecta
    Cliente C → Nodo 2
    Cliente C → Oferta $200
    Estado: {
        "192.168.1.5:52341" → $100,
        "192.168.1.5:52342" → $150,
        "192.168.1.5:52343" → $200  ← NUEVA
    }
    Replicado en: Nodo 1, Nodo 2

T=120s: Subasta termina
    Ganador: Cliente C con $200
    Nodo 2 → Broadcast resultado a todos los clientes
```

---

## Ventajas del Sistema

1. **Alta Disponibilidad**: Si un nodo falla, otro toma su lugar automáticamente
2. **Sin Punto Único de Fallo**: Cualquier nodo puede ser coordinador
3. **Consistencia de Datos**: Todas las ofertas se replican en tiempo real
4. **Recuperación Automática**: Nodos caídos pueden volver y recuperar el estado
5. **Transparencia para el Cliente**: El cliente es redirigido automáticamente
6. **Identificación Única**: Múltiples clientes desde la misma IP son distinguibles por puerto

---

## Limitaciones y Mejoras Futuras

**Limitaciones:**
- Elección puede causar breve interrupción de servicio
- Requiere al menos 2 nodos activos para tolerancia a fallos
- Timestamp puede tener problemas si los relojes no están sincronizados

**Mejoras Futuras:**
- Implementar sincronización de relojes (NTP)
- Agregar persistencia en disco para recuperación completa
- Implementar quorum para mayor consistencia
- Agregar autenticación entre nodos
