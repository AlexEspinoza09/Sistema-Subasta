# Sistema de Comunicación Cliente-Servidor con Sockets TCP

Proyecto de sistemas distribuidos que implementa múltiples protocolos de comunicación cliente-servidor utilizando sockets TCP en Java. Incluye arquitecturas secuenciales, concurrentes y un sistema de subasta en tiempo real.

##  Descripción General

Este proyecto demuestra diferentes patrones de comunicación en sistemas distribuidos:

- **Servidor Daytime**: Devuelve la fecha y hora actual del servidor
- **Servidor Echo**: Repite los mensajes enviados por el cliente (versiones secuencial y concurrente)
- **Sistema de Subasta**: Sistema de subasta en tiempo real con múltiples oferentes

##  Arquitectura

### Estructura de Paquetes

```
socket.conconexion
├── servidor/          # Implementaciones de servidores
└── cliente/           # Implementaciones de clientes
```

### Componentes Principales

#### MiSocketStream
Clase wrapper que simplifica la comunicación por sockets:
- Encapsula BufferedReader y PrintWriter
- Métodos: `enviaMensaje(String)` y `recibeMensaje()`
- Utilizada por clientes y servidores

#### Servidores

1. **ServidorDaytime2** (Puerto 13)
   - Arquitectura: Secuencial
   - Funcionalidad: Envía timestamp al conectarse
   - Cierra conexión inmediatamente

2. **ServidorEcho2** (Puerto 7000)
   - Arquitectura: Secuencial
   - Funcionalidad: Echo de mensajes hasta recibir "."
   - Un cliente a la vez

3. **ServidorEcho3** (Puerto 7)
   - Arquitectura: Concurrente (multi-thread)
   - Funcionalidad: Echo de mensajes hasta recibir "."
   - Múltiples clientes simultáneos
   - Usa HiloServidorEcho para cada cliente

4. **ServidorSubasta** (Puerto 8080)
   - Arquitectura: Concurrente con broadcast
   - Funcionalidad: Sistema de subasta en tiempo real
   - Duración: 2 minutos (configurable)
   - Características:
     - Múltiples oferentes simultáneos
     - Actualizaciones en tiempo real
     - Broadcast del ganador a todos los participantes

#### Clientes

Cada cliente sigue el patrón de dos capas:
- **Capa de Presentación**: Interfaz de usuario y entrada/salida
- **Capa de Aplicación**: Lógica de comunicación con el servidor

Ejemplos:
- `ClienteDaytime2` + `ClienteDaytimeAuxiliar2`
- `ClienteEcho2` + `ClienteEchoAuxiliar2`
- `ClienteSubasta` + `ClienteSubastaAuxiliar`

##  Sistema de Subasta (Funcionalidad Avanzada)

### Características

- **Ofertas múltiples**: Cada cliente puede hacer múltiples ofertas durante la subasta
- **Intervalo de ofertas**: 10 segundos entre cada propuesta
- **Tiempo total**: 2 minutos (120 segundos)
- **Broadcast automático**: El servidor envía la oferta ganadora cada 5 segundos a todos los clientes
- **Feedback en tiempo real**:
  - Propuesta más alta actual
  - IP del líder
  - Tiempo restante
  - Estado personal (GANANDO/PERDIENDO)
  - Actualizaciones automáticas cada 5 segundos

### Flujo del Sistema

1. Cliente conecta y envía propuesta inicial
2. Servidor responde con estado actual
3. **Durante la subasta**:
   - Cliente puede ofertar cada 10 segundos
   - Servidor envía broadcast cada 5 segundos con la oferta ganadora actual
   - Todos los clientes reciben actualizaciones automáticas
4. Al finalizar los 2 minutos:
   - Servidor determina ganador (propuesta más alta)
   - Notifica a TODOS los clientes simultáneamente
   - Cierra todas las conexiones

### Thread Safety

- `CopyOnWriteArrayList` para lista de clientes
- `synchronized` para propuesta más alta
- `CountDownLatch` para sincronizar broadcast final

##  Compilación y Ejecución

### Requisitos

- Java JDK 17 o superior
- Docker y Docker Compose (opcional)

### Compilación Local

```bash
# Compilar todos los archivos
javac -encoding UTF-8 -d . servidor/*.java cliente/*.java

# Compilar servidor específico
javac -d . servidor/ServidorSubasta.java servidor/HiloClienteSubasta.java servidor/MiSocketStream.java

# Compilar cliente específico
javac -d . cliente/ClienteSubasta.java cliente/ClienteSubastaAuxiliar.java servidor/MiSocketStream.java
```

### Ejecución de Servidores

```bash
# Servidor Daytime (puerto 13)
java socket.conconexion.servidor.ServidorDaytime2

# Servidor Echo Secuencial (puerto 7000)
java socket.conconexion.servidor.ServidorEcho2 7000

# Servidor Echo Concurrente (puerto 7)
java socket.conconexion.servidor.ServidorEcho3

# Servidor de Subasta (puerto 8080)
java socket.conconexion.servidor.ServidorSubasta
```

### Ejecución de Clientes

```bash
# Cliente Daytime
java socket.conconexion.cliente.ClienteDaytime2

# Cliente Echo
java socket.conconexion.cliente.ClienteEcho2

# Cliente Subasta
java socket.conconexion.cliente.ClienteSubasta
```

##  Despliegue con Docker 🐳

### Construcción de Imágenes

```bash
# Construir todas las imágenes
docker-compose build

# Construir sin caché
docker-compose build --no-cache
```

### Ejecución con Docker Compose

```bash
# Iniciar todos los servicios
docker-compose up -d

# Iniciar servicio específico
docker-compose up -d servidor-subasta

# Ver logs
docker-compose logs -f servidor-subasta

# Detener servicios
docker-compose down
```

### Puertos Expuestos

| Servicio | Puerto | Descripción |
|----------|--------|-------------|
| servidor-daytime | 13 | Servidor Daytime |
| servidor-echo-concurrente | 7 | Servidor Echo Concurrente |
| servidor-echo-secuencial | 7000 | Servidor Echo Secuencial |
| servidor-subasta | 8080 | Servidor de Subasta |

### Pruebas con Telnet/Netcat

```bash
# Probar Daytime
telnet localhost 13

# Probar Echo
telnet localhost 7

# Probar con netcat
nc localhost 7
```

##  Ejemplos de Uso

### Ejemplo 1: Sistema de Subasta con Múltiples Clientes

**Terminal 1 - Servidor:**
```bash
docker-compose up servidor-subasta
```

**Terminal 2 - Cliente 1:**
```bash
java socket.conconexion.cliente.ClienteSubasta
# Host: localhost
# Puerto: 8080
# Propuesta inicial: 100
# Después de 10 seg: 150
```

**Terminal 3 - Cliente 2:**
```bash
java socket.conconexion.cliente.ClienteSubasta
# Host: localhost
# Puerto: 8080
# Propuesta inicial: 120
# Después de 10 seg: 180
```

**Terminal 4 - Cliente 3:**
```bash
java socket.conconexion.cliente.ClienteSubasta
# Host: localhost
# Puerto: 8080
# Propuesta inicial: 200
# Estado: ESTAS GANANDO LA SUBASTA!
```

Después de 2 minutos, todos los clientes reciben el resultado simultáneamente.

### Ejemplo 2: Echo Concurrente

```bash
# Terminal 1
java socket.conconexion.cliente.ClienteEcho2
# Enviar mensajes, el servidor responde con eco

# Terminal 2 (simultáneo)
java socket.conconexion.cliente.ClienteEcho2
# Ambos clientes funcionan al mismo tiempo
```

##  Despliegue en AWS

### Opción 1: Amazon ECS

1. Construir y subir imagen a Amazon ECR:
```bash
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <account-id>.dkr.ecr.us-east-1.amazonaws.com
docker tag servidor-sockets:latest <account-id>.dkr.ecr.us-east-1.amazonaws.com/servidor-sockets:latest
docker push <account-id>.dkr.ecr.us-east-1.amazonaws.com/servidor-sockets:latest
```

2. Crear Task Definition con mapeo de puertos
3. Configurar Security Group con puertos 7, 13, 7000, 8080

### Opción 2: Amazon EC2

1. Instalar Docker y Docker Compose en instancia EC2
2. Clonar repositorio
3. Ejecutar `docker-compose up -d`
4. Configurar Security Group para permitir tráfico TCP en puertos necesarios

##  Estructura del Proyecto

```
.
├── servidor/
│   ├── MiSocketStream.java          # Wrapper de Socket
│   ├── ServidorDaytime2.java        # Servidor Daytime
│   ├── ServidorEcho2.java           # Servidor Echo Secuencial
│   ├── ServidorEcho3.java           # Servidor Echo Concurrente
│   ├── HiloServidorEcho.java        # Worker thread para Echo
│   ├── ServidorSubasta.java         # Servidor de Subasta
│   └── HiloClienteSubasta.java      # Worker thread para Subasta
├── cliente/
│   ├── ClienteDaytime2.java
│   ├── ClienteDaytimeAuxiliar2.java
│   ├── ClienteEcho2.java
│   ├── ClienteEchoAuxiliar2.java
│   ├── ClienteSubasta.java
│   └── ClienteSubastaAuxiliar.java
├── Dockerfile
├── docker-compose.yml
└── README.md
```

##  Configuración

### Tiempos Configurables

En `ServidorSubasta.java`:
```java
private static final int TIEMPO_SUBASTA = 120000; // 2 minutos
```

En `ClienteSubasta.java`:
```java
private static final int INTERVALO_PROPUESTA = 10; // 10 segundos
```

**Intervalo de broadcast del servidor** (en `ServidorSubasta.java`, método `iniciarBroadcastPeriodico`):
```java
broadcastTimer.scheduleAtFixedRate(..., 5000, 5000); // 5 segundos
```

### Puertos por Defecto

Modificables en cada archivo de servidor:
- Daytime: 13
- Echo Secuencial: 7000
- Echo Concurrente: 7
- Subasta: 8080

##  Protocolos Implementados

### Protocolo Daytime
- Petición implícita (solo conexión)
- Respuesta: timestamp del servidor
- Cierre inmediato de conexión

### Protocolo Echo
- Cliente envía mensaje
- Servidor responde con el mismo mensaje
- Termina cuando cliente envía "."

### Protocolo de Subasta
```
Cliente -> Servidor: <monto_propuesta>
Servidor -> Cliente: PROPUESTA_ALTA:<ip>:<monto>:TIEMPO:<seg>:TU_PROPUESTA:<GANANDO|PERDIENDO>

[Cada 5 segundos - Broadcast automático]
Servidor -> Todos: UPDATE:PROPUESTA_ALTA:<ip>:<monto>:TIEMPO:<segundos_restantes>

[Al finalizar la subasta]
Servidor -> Todos: GANADOR:<ip>:MONTO:<cantidad>
```

##  Tecnologías

- **Lenguaje**: Java 17
- **Sockets**: java.net.Socket, ServerSocket
- **Concurrencia**: Thread, Runnable, CopyOnWriteArrayList, CountDownLatch
- **Contenedores**: Docker, Docker Compose


##  Autor

Proyecto desarrollado para la materia de Sistemas Distribuidos por mi Alex Espinoza utilizando de base una plantilla realizada por el Ing.Galo Cornejo Profesor de la Facultad de Ingenieria.

##  Licencia

Este proyecto es de código abierto y está disponible para fines educativos.

##  Contribuciones

Las contribuciones son bienvenidas. Por favor:
1. Fork el proyecto
2. Crea una rama para tu feature (`git checkout -b feature/AmazingFeature`)
3. Commit tus cambios (`git commit -m 'Add some AmazingFeature'`)
4. Push a la rama (`git push origin feature/AmazingFeature`)
5. Abre un Pull Request

##  Soporte

Para preguntas o problemas, por favor abre un issue en GitHub.

---

**Notas Importantes**:
- El tiempo de subasta está configurado en 2 minutos (120 segundos) para facilitar las pruebas. Puede ajustarse modificando la constante `TIEMPO_SUBASTA` en `ServidorSubasta.java`.
- El servidor envía automáticamente la oferta ganadora cada 5 segundos a todos los clientes conectados, permitiendo que todos vean en tiempo real quién está ganando.
- Los clientes pueden hacer ofertas cada 10 segundos, dándoles tiempo para reaccionar a las actualizaciones del servidor.
