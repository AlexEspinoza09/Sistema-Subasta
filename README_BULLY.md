# Sistema de Subasta Distribuido con Protocolo Bully

## Descripción General

Este proyecto implementa un sistema de subastas distribuido que utiliza el **algoritmo Bully** para la elección de coordinador. Múltiples servidores pueden ejecutarse simultáneamente, y automáticamente elegirán un coordinador que manejará las subastas. Si el coordinador falla, se realizará una nueva elección automáticamente.

## Arquitectura del Sistema

### Componentes Principales

1. **ServidorSubastaBully.java** - Servidor principal con capacidades distribuidas
2. **GestorEleccion.java** - Implementación del algoritmo Bully
3. **NodoSubasta.java** - Representación de un nodo en el sistema
4. **MensajeBully.java** - Mensajes del protocolo (ELECTION, OK, COORDINATOR, HEARTBEAT)
5. **HiloClienteSubastaBully.java** - Manejo de clientes conectados
6. **ClienteSubastaAuxiliar.java** - Cliente con soporte para redirección automática

### Protocolo Bully

El algoritmo Bully funciona de la siguiente manera:

1. **Elección Inicial**: Cuando un servidor arranca, inicia una elección
2. **Mensaje ELECTION**: Se envía a todos los nodos con ID mayor
3. **Respuesta OK**: Los nodos superiores responden "OK"
4. **Coordinador**: El nodo con mayor ID se convierte en coordinador
5. **Anuncio COORDINATOR**: El coordinador anuncia su rol a todos
6. **Heartbeats**: El coordinador envía latidos periódicos
7. **Detección de Fallas**: Si el coordinador falla, se inicia nueva elección

### Roles de Servidores

- **COORDINADOR**: Acepta clientes y maneja la subasta
- **PARTICIPANTE**: Redirige clientes al coordinador
- **EN_ELECCION**: Participando en proceso de elección
- **INICIALIZANDO**: Arrancando y descubriendo red

## Compilación

### Compilar todos los archivos

```bash
cd "c:\Users\javier\Documents\Proyecto Distribuidos"

# Compilar servidores Bully
javac -d . servidor/MensajeBully.java
javac -d . servidor/NodoSubasta.java
javac -d . servidor/MiSocketStream.java
javac -d . servidor/GestorEleccion.java
javac -d . servidor/HiloClienteSubastaBully.java
javac -d . servidor/ServidorSubastaBully.java

# Compilar clientes (ya soportan redirección)
javac -d . cliente/ClienteSubastaAuxiliar.java
javac -d . cliente/ClienteSubasta.java
```

### Script de compilación rápida

```bash
# Windows
javac -d . servidor/*.java cliente/*.java

# Linux/Mac
javac -d . servidor/*.java cliente/*.java
```

## Configuración

### Archivo nodos.conf

El archivo `nodos.conf` define todos los nodos del sistema:

```
# id,host,puertoClientes,puertoCoordinacion
1,localhost,8080,9080
2,localhost,8081,9081
3,localhost,8082,9082
```

**Importante**: El nodo con **mayor ID** tiene mayor prioridad y será elegido coordinador si está disponible.

## Ejecución

### Iniciar Servidores Distribuidos

Abrir **3 terminales diferentes** y ejecutar:

**Terminal 1 - Nodo 1 (prioridad baja)**
```bash
java socket.conconexion.servidor.ServidorSubastaBully 1 8080 9080 nodos.conf
```

**Terminal 2 - Nodo 2 (prioridad media)**
```bash
java socket.conconexion.servidor.ServidorSubastaBully 2 8081 9081 nodos.conf
```

**Terminal 3 - Nodo 3 (prioridad alta - será coordinador)**
```bash
java socket.conconexion.servidor.ServidorSubastaBully 3 8082 9082 nodos.conf
```

### Conectar Clientes

Los clientes pueden conectarse a **cualquier servidor**. Si no es el coordinador, serán redirigidos automáticamente.

```bash
java socket.conconexion.cliente.ClienteSubasta
```

Cuando se solicite el servidor, puedes usar:
- `localhost` y puerto `8080` (será redirigido al coordinador)
- `localhost` y puerto `8081` (será redirigido al coordinador)
- `localhost` y puerto `8082` (conecta directo al coordinador)

## Pruebas del Protocolo Bully

### Prueba 1: Elección Inicial

1. Iniciar los 3 nodos en orden: 1, 2, 3
2. Observar el proceso de elección
3. Verificar que el Nodo 3 (mayor ID) es elegido coordinador

**Salida esperada:**
```
[BULLY] Gestor de elección iniciado para nodo 3
[BULLY] Nodo 3 inicia elección
[BULLY] No hay nodos superiores. Asumiendo coordinación.
╔═══════════════════════════════════════════╗
║  NUEVO COORDINADOR: Nodo 3            ║
╚═══════════════════════════════════════════╝
```

### Prueba 2: Tolerancia a Fallos

1. Con los 3 nodos activos (Nodo 3 es coordinador)
2. Detener el Nodo 3 (Ctrl+C)
3. Observar que el Nodo 2 detecta la falla
4. El Nodo 2 inicia elección y se convierte en coordinador
5. Conectar clientes - ahora van al Nodo 2

**Salida esperada en Nodo 2:**
```
[BULLY] Coordinador no responde a heartbeat
[BULLY] Nodo 2 inicia elección
[BULLY] No hay nodos superiores activos. Asumiendo coordinación.
╔═══════════════════════════════════════════╗
║  NUEVO COORDINADOR: Nodo 2            ║
╚═══════════════════════════════════════════╝
```

### Prueba 3: Recuperación del Coordinador

1. Con Nodo 2 como coordinador (Nodo 3 caído)
2. Reiniciar Nodo 3
3. Nodo 3 inicia elección
4. Nodo 3 toma el rol de coordinador (tiene mayor ID)
5. Los clientes conectados al Nodo 2 son redirigidos al Nodo 3

### Prueba 4: Redirección de Clientes

1. Iniciar 3 servidores (Nodo 3 es coordinador)
2. Conectar cliente al Nodo 1 (puerto 8080)
3. Observar redirección automática al Nodo 3 (puerto 8082)

**Salida en cliente:**
```
Conectado al servidor: localhost:8080
[INFO] Servidor redirigiendo al coordinador...
[REDIRECCION] Conectando a coordinador: localhost:8082
[CONECTADO] Servidor de subasta activo
```

### Prueba 5: Subasta con Múltiples Clientes

1. Asegurar que hay un coordinador activo
2. Conectar 3+ clientes simultáneamente
3. Cada cliente hace ofertas
4. Verificar que todos reciben actualizaciones
5. Al finalizar, todos reciben el ganador

## Comparación: Servidor Original vs Distribuido

| Característica | ServidorSubasta.java | ServidorSubastaBully.java |
|---------------|---------------------|--------------------------|
| **Arquitectura** | Centralizado | Distribuido |
| **Tolerancia a fallos** | ❌ No | ✅ Sí |
| **Elección de líder** | ❌ No aplica | ✅ Algoritmo Bully |
| **Redirección** | ❌ No | ✅ Automática |
| **Escalabilidad** | Limitada | Alta |
| **Punto único de falla** | ✅ Sí | ❌ No |

## Ventajas del Sistema Distribuido

1. **Alta Disponibilidad**: Si un servidor falla, otro toma su lugar
2. **Balanceo Implícito**: Los clientes se distribuyen entre servidores
3. **Elección Automática**: No requiere configuración manual del líder
4. **Redirección Transparente**: Los clientes no necesitan saber quién es el coordinador
5. **Detección de Fallos**: Heartbeats detectan servidores caídos
6. **Recuperación Automática**: Cuando un servidor vuelve, participa en elecciones

## Desventajas y Limitaciones

1. **Mayor Complejidad**: Más código y lógica de coordinación
2. **Overhead de Red**: Mensajes ELECTION, OK, COORDINATOR, HEARTBEAT
3. **Tiempo de Elección**: Breve período sin coordinador durante elecciones
4. **Configuración**: Requiere archivo nodos.conf actualizado
5. **Puertos Adicionales**: Cada nodo usa 2 puertos (clientes + coordinación)

## Solución de Problemas

### Error: "Address already in use"

**Problema**: Un puerto ya está en uso.

**Solución**:
```bash
# Windows
netstat -ano | findstr :8080
taskkill /PID <PID> /F

# Linux/Mac
lsof -i :8080
kill -9 <PID>
```

### Error: "No hay coordinador disponible"

**Problema**: Ningún servidor está activo o la elección falló.

**Solución**:
1. Verificar que al menos un servidor esté ejecutándose
2. Revisar logs de elección
3. Reiniciar todos los servidores

### Error: "Máximo de redirecciones alcanzado"

**Problema**: Ciclo de redirecciones o configuración incorrecta.

**Solución**:
1. Verificar `nodos.conf` tiene IDs únicos
2. Asegurar que haya un coordinador activo
3. Revisar logs del servidor para errores de elección

### Los clientes no se redirigen

**Problema**: ClienteSubastaAuxiliar.java no actualizado.

**Solución**:
- Recompilar el cliente con el código actualizado que incluye `conectarConRedireccion()`

## Arquitectura de Red

```
┌─────────────────────────────────────────────────────┐
│                    CLIENTES                          │
│  Cliente1    Cliente2    Cliente3    Cliente4        │
└────┬──────────┬──────────┬──────────┬───────────────┘
     │          │          │          │
     │          │          │          │
     └──────────┴──────────┴──────────┘
                │
                │ (Redirección automática)
                │
     ┌──────────▼──────────────────────┐
     │  COORDINADOR (Nodo 3)           │
     │  Puerto Clientes: 8082          │
     │  Puerto Coord: 9082             │
     │  - Maneja subasta               │
     │  - Envía heartbeats             │
     └─────────┬───────────────────────┘
               │
        ┌──────┴──────┐
        │             │
┌───────▼──────┐ ┌───▼──────────┐
│ PARTICIPANTE │ │ PARTICIPANTE │
│  (Nodo 1)    │ │  (Nodo 2)    │
│  8080/9080   │ │  8081/9081   │
│ - Redirige   │ │ - Redirige   │
│ - Monitorea  │ │ - Monitorea  │
└──────────────┘ └──────────────┘
```

## Mensajes del Protocolo Bully

### ELECTION
Enviado por un nodo para iniciar elección.
```
Nodo 2 → Nodo 3: ELECTION(idEmisor=2)
```

### OK
Respuesta de un nodo superior que está vivo.
```
Nodo 3 → Nodo 2: OK(idEmisor=3)
```

### COORDINATOR
Anuncio del nuevo coordinador.
```
Nodo 3 → Todos: COORDINATOR(idEmisor=3, idCoord=3, host, puerto)
```

### HEARTBEAT
Latido periódico del coordinador.
```
Nodo 3 → Todos: HEARTBEAT(idEmisor=3) [cada 5 segundos]
```

### REDIRECCION (cliente)
Redirección de cliente al coordinador.
```
Servidor → Cliente: "REDIRECCION:localhost:8082"
```

## Testing Automatizado (Opcional)

Para pruebas más rigurosas, puedes crear scripts:

**test_bully.sh** (Linux/Mac):
```bash
#!/bin/bash

echo "Iniciando Nodo 1..."
java socket.conconexion.servidor.ServidorSubastaBully 1 8080 9080 nodos.conf &
PID1=$!
sleep 2

echo "Iniciando Nodo 2..."
java socket.conconexion.servidor.ServidorSubastaBully 2 8081 9081 nodos.conf &
PID2=$!
sleep 2

echo "Iniciando Nodo 3..."
java socket.conconexion.servidor.ServidorSubastaBully 3 8082 9082 nodos.conf &
PID3=$!
sleep 5

echo "Todos los nodos iniciados. Nodo 3 debe ser coordinador."
echo "Presiona Ctrl+C para detener todos los servidores."

wait
```

## Despliegue con Docker

El sistema incluye configuración Docker para despliegue fácil. Ver documentación completa en [DOCKER_BULLY.md](DOCKER_BULLY.md).

### Inicio Rápido con Docker

**1. Construir imagen:**
```bash
docker-compose -f docker-compose-bully.yml build
```

**2. Levantar los 3 nodos:**
```bash
docker-compose -f docker-compose-bully.yml up
```

**3. Conectar clientes desde el host:**
```bash
java socket.conconexion.cliente.ClienteSubasta
# Servidor: localhost
# Puerto: 8080, 8081 o 8082
```

**Comandos útiles:**
```bash
# Ver logs
docker-compose -f docker-compose-bully.yml logs -f

# Detener nodo 3 (simular falla)
docker-compose -f docker-compose-bully.yml stop nodo-bully-3

# Reiniciar nodo 3
docker-compose -f docker-compose-bully.yml start nodo-bully-3

# Detener todo
docker-compose -f docker-compose-bully.yml down
```

### Archivos Docker

- `Dockerfile.bully` - Imagen para servidores Bully
- `docker-compose-bully.yml` - Orquestación de 3 nodos
- `nodos-docker.conf` - Configuración para Docker (usa hostnames de contenedores)
- `DOCKER_BULLY.md` - Documentación completa de Docker

## Conclusión

Este sistema demuestra:
- ✅ Implementación correcta del algoritmo Bully
- ✅ Tolerancia a fallos en sistemas distribuidos
- ✅ Elección automática de coordinador
- ✅ Redirección transparente de clientes
- ✅ Detección de fallos con heartbeats
- ✅ Recuperación automática de servicios
- ✅ Despliegue con Docker/Docker Compose

El sistema está listo para:
- Demostraciones académicas
- Pruebas de concepto
- Base para sistemas distribuidos más complejos
- Aprendizaje de algoritmos de consenso
- Despliegue en contenedores
