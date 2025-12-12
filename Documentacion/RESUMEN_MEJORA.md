# Resumen de Mejora: Sistema de Subasta con Protocolo Bully

## ✅ Implementación Completada

Se ha mejorado exitosamente el sistema de subastas con la implementación del **Protocolo Bully** para sistemas distribuidos con tolerancia a fallos.

## 📦 Componentes Creados

### Archivos Principales

1. **servidor/MensajeBully.java** - Mensajes del protocolo (ELECTION, OK, COORDINATOR, HEARTBEAT)
2. **servidor/NodoSubasta.java** - Representación de nodos en el sistema distribuido
3. **servidor/GestorEleccion.java** - Implementación completa del algoritmo Bully
4. **servidor/HiloClienteSubastaBully.java** - Manejo de clientes en el sistema distribuido
5. **servidor/ServidorSubastaBully.java** - Servidor principal con capacidades distribuidas

### Modificaciones

6. **servidor/MiSocketStream.java** - Agregado método `getSocket()` y `close()` override
7. **cliente/ClienteSubastaAuxiliar.java** - Soporte para redirección automática

### Archivos de Configuración

8. **nodos.conf** - Configuración de nodos del sistema
9. **compilar_bully.bat** - Script de compilación para Windows
10. **compilar_bully.sh** - Script de compilación para Linux/Mac

### Documentación

11. **README_BULLY.md** - Documentación completa del sistema
12. **CLAUDE.md** - Actualizado con información del protocolo Bully

## 🎯 Características Implementadas

### Protocolo Bully
- ✅ Elección automática de coordinador basada en ID
- ✅ Detección de fallos mediante heartbeats
- ✅ Re-elección automática cuando el coordinador falla
- ✅ Mensajes: ELECTION, OK, COORDINATOR, HEARTBEAT

### Sistema Distribuido
- ✅ Múltiples servidores coordinados
- ✅ Solo el coordinador acepta clientes y maneja subastas
- ✅ Servidores participantes redirigen automáticamente al coordinador
- ✅ Tolerancia a fallos: si un servidor cae, el sistema continúa

### Cliente Mejorado
- ✅ Redirección automática al coordinador
- ✅ Manejo de hasta 5 redirecciones consecutivas
- ✅ Detección de errores y timeouts
- ✅ Compatible con servidor original y distribuido

## 🚀 Cómo Usar

### 1. Compilar

**Windows:**
```bash
compilar_bully.bat
```

**Linux/Mac:**
```bash
chmod +x compilar_bully.sh
./compilar_bully.sh
```

### 2. Ejecutar Servidores

Abrir 3 terminales diferentes:

**Terminal 1 - Nodo 1 (prioridad baja):**
```bash
java socket.conconexion.servidor.ServidorSubastaBully 1 8080 9080 nodos.conf
```

**Terminal 2 - Nodo 2 (prioridad media):**
```bash
java socket.conconexion.servidor.ServidorSubastaBully 2 8081 9081 nodos.conf
```

**Terminal 3 - Nodo 3 (prioridad alta - coordinador):**
```bash
java socket.conconexion.servidor.ServidorSubastaBully 3 8082 9082 nodos.conf
```

### 3. Conectar Clientes

```bash
java socket.conconexion.cliente.ClienteSubasta
```

- El cliente puede conectarse a cualquier puerto (8080, 8081, 8082)
- Será redirigido automáticamente al coordinador
- Funciona exactamente igual que antes desde la perspectiva del usuario

## 🧪 Pruebas Sugeridas

### Prueba 1: Funcionamiento Normal
1. Iniciar los 3 servidores
2. Verificar que Nodo 3 es elegido coordinador
3. Conectar 2-3 clientes
4. Realizar ofertas y verificar que todos reciben actualizaciones

### Prueba 2: Tolerancia a Fallos
1. Con los 3 servidores activos
2. Detener el Nodo 3 (coordinador) con Ctrl+C
3. Observar que Nodo 2 detecta la falla e inicia elección
4. Nodo 2 se convierte en el nuevo coordinador
5. Conectar clientes - deben ser redirigidos al Nodo 2

### Prueba 3: Recuperación
1. Con Nodo 2 como coordinador (Nodo 3 caído)
2. Reiniciar Nodo 3
3. Nodo 3 inicia elección y recupera coordinación (mayor ID)
4. Nuevos clientes se conectan al Nodo 3

### Prueba 4: Redirección
1. Conectar cliente al Nodo 1 (puerto 8080)
2. Observar redirección automática al coordinador
3. Cliente funciona normalmente sin saber que fue redirigido

## 📊 Comparativa

| Aspecto | Antes (ServidorSubasta) | Después (ServidorSubastaBully) |
|---------|------------------------|-------------------------------|
| **Arquitectura** | Centralizada | Distribuida |
| **Servidores** | 1 servidor único | N servidores coordinados |
| **Tolerancia a fallos** | ❌ Ninguna | ✅ Automática |
| **Punto único de falla** | ✅ Sí | ❌ No |
| **Elección de líder** | N/A | ✅ Algoritmo Bully |
| **Redirección** | N/A | ✅ Transparente |
| **Escalabilidad** | Limitada | Alta |
| **Complejidad** | Baja | Media |

## 🎓 Conceptos Demostrados

1. **Algoritmo Bully** - Elección de coordinador en sistemas distribuidos
2. **Tolerancia a Fallos** - El sistema continúa funcionando si un nodo falla
3. **Heartbeats** - Detección de fallos mediante latidos periódicos
4. **Redirección Transparente** - Clientes no necesitan saber quién es el coordinador
5. **Comunicación Inter-Proceso** - Servidores se comunican entre sí
6. **Serialización de Objetos** - Mensajes del protocolo usando ObjectOutputStream
7. **Sistemas Distribuidos** - Múltiples procesos coordinados

## 📁 Estructura de Archivos

```
Proyecto Distribuidos/
├── servidor/
│   ├── MiSocketStream.java (modificado)
│   ├── MensajeBully.java (nuevo)
│   ├── NodoSubasta.java (nuevo)
│   ├── GestorEleccion.java (nuevo)
│   ├── HiloClienteSubastaBully.java (nuevo)
│   ├── ServidorSubastaBully.java (nuevo)
│   ├── ServidorSubasta.java (original)
│   ├── HiloClienteSubasta.java (original)
│   └── ...
├── cliente/
│   ├── ClienteSubastaAuxiliar.java (modificado)
│   ├── ClienteSubasta.java (sin cambios)
│   └── ...
├── nodos.conf (nuevo)
├── compilar_bully.bat (nuevo)
├── compilar_bully.sh (nuevo)
├── README_BULLY.md (nuevo)
├── RESUMEN_MEJORA.md (este archivo)
└── CLAUDE.md (actualizado)
```

## 🔧 Configuración de Nodos

El archivo `nodos.conf` define la topología del sistema:

```
# Formato: id,host,puertoClientes,puertoCoordinacion
1,localhost,8080,9080
2,localhost,8081,9081
3,localhost,8082,9082
```

- **ID más alto = mayor prioridad** en elecciones
- Cada nodo usa 2 puertos:
  - Puerto de clientes: para conexiones de clientes de subasta
  - Puerto de coordinación: para comunicación entre servidores (Bully)

## 🐛 Solución de Problemas

### Error: "Address already in use"
```bash
# Windows
netstat -ano | findstr :8080
taskkill /PID <PID> /F

# Linux/Mac
lsof -i :8080
kill -9 <PID>
```

### Error: "No hay coordinador disponible"
- Verificar que al menos un servidor esté ejecutándose
- Revisar logs de elección en la consola de los servidores
- Reiniciar todos los servidores si es necesario

### Los clientes no se redirigen
- Asegurar que ClienteSubastaAuxiliar.java fue recompilado
- Verificar que existe un coordinador activo
- Revisar que nodos.conf tiene configuración correcta

## 📚 Documentación Adicional

- **README_BULLY.md** - Documentación detallada con diagramas y ejemplos
- **CLAUDE.md** - Guía de desarrollo actualizada
- Comentarios en código fuente - Cada clase está documentada

## ✨ Próximos Pasos Sugeridos

1. **Testing Avanzado**: Crear suite de pruebas automatizadas
2. **Métricas**: Agregar logging de métricas de rendimiento
3. **Configuración Dinámica**: Permitir agregar/quitar nodos en caliente
4. **Persistencia**: Guardar estado de subastas en base de datos
5. **Seguridad**: Agregar autenticación entre servidores
6. **Docker**: Crear contenedores para despliegue fácil
7. **Monitoreo**: Dashboard web para visualizar estado del cluster

## 🎉 Conclusión

El sistema ahora implementa un **verdadero sistema distribuido** con:
- Tolerancia a fallos automática
- Elección democrática de coordinador
- Escalabilidad horizontal
- Redirección transparente

El código está listo para:
- ✅ Demostraciones académicas
- ✅ Pruebas de concepto
- ✅ Base para proyectos más complejos
- ✅ Aprendizaje de sistemas distribuidos

---

## 🔨 Corrección de Bug - Elección Incorrecta de Coordinador

### Problema Detectado

**Síntoma:** Cuando el nodo 3 (coordinador) fallaba, el nodo 1 se convertía en coordinador en lugar del nodo 2, a pesar de que el nodo 2 estaba activo. Además, las redirecciones de clientes no funcionaban después del cambio de coordinador.

**Causa raíz identificada:** El monitor de heartbeats en `GestorEleccion.java` se ejecutaba cada `TIMEOUT_HEARTBEAT / 2` (5 segundos) en lugar de cada `TIMEOUT_HEARTBEAT` (10 segundos), causando:
- Detección prematura e incorrecta de fallos
- Múltiples elecciones simultáneas en un bucle infinito
- El nodo 2 detectaba el fallo cada 200-400ms, impidiendo que la elección se completara correctamente

### Solución Aplicada

Se corrigió el método `iniciarMonitoreoCoordinador()` en `servidor/GestorEleccion.java` línea 330:

**Cambios realizados:**

1. **Intervalo de verificación corregido:**
   - Antes: `scheduleAtFixedRate(..., TIMEOUT_HEARTBEAT / 2, TIMEOUT_HEARTBEAT / 2, ...)` (5 seg)
   - Ahora: `scheduleAtFixedRate(..., TIMEOUT_HEARTBEAT, TIMEOUT_HEARTBEAT, ...)` (10 seg)

2. **Prevención de elecciones múltiples:**
   ```java
   synchronized(lockEleccion) {
       if (!enEleccion) {
           poolHilos.submit(() -> iniciarEleccion());
       }
   }
   ```

3. **Logging mejorado:** Ahora muestra ID del coordinador caído y tiempo exacto desde último heartbeat

4. **Manejo de errores:** Try-catch para prevenir que excepciones detengan el monitor

### Verificación de la Corrección

**Prueba realizada:** Detener nodo 3 con nodos 1 y 2 activos

**Resultados:**

✅ **Detección correcta del fallo:**
```
[BULLY] !!! COORDINADOR CAIDO !!!
[BULLY] Coordinador ID: 3
[BULLY] Ultimo heartbeat: 11675 ms atrás
```

✅ **Elección correcta:**
- Nodo 1 envió ELECTION a Nodo 2
- Nodo 2 respondió OK
- Nodo 2 se convirtió en coordinador (ID mayor)
- Nodo 1 reconoció a Nodo 2 como coordinador

✅ **Sin bucles infinitos:** Una sola elección por fallo detectado

✅ **Redirección funcional:** Los clientes son redirigidos correctamente al nuevo coordinador (Nodo 2)

✅ **Heartbeats funcionando:** El nuevo coordinador envía heartbeats cada 5 segundos

### Cómo Aplicar la Corrección

```bash
# 1. Recompilar el código corregido
javac -encoding UTF-8 -d . servidor/GestorEleccion.java servidor/NodoSubasta.java servidor/MensajeBully.java

# 2. Si usas Docker, reconstruir las imágenes
docker-compose -f docker-compose-bully.yml down
docker-compose -f docker-compose-bully.yml build --no-cache
docker-compose -f docker-compose-bully.yml up

# 3. Probar el failover
# Iniciar los 3 nodos, esperar 15 segundos, luego:
docker stop subasta-bully-nodo-3

# 4. Verificar que Nodo 2 se convirtió en coordinador
docker logs subasta-bully-nodo-2 --tail 20
docker logs subasta-bully-nodo-1 --tail 20
```

### Archivo Modificado
- `servidor/GestorEleccion.java` (línea 330, método `iniciarMonitoreoCoordinador()`)

---

## 🔨 Corrección de Bug #2 - Nodo No Se Convierte en Coordinador al Reiniciar

### Problema Detectado

**Síntoma:** Cuando el Nodo 3 (coordinador) caía y luego se reiniciaba, intentaba convertirse en coordinador pero inmediatamente volvía a estado de participante.

**Logs observados:**
```
[BULLY] Nuevo coordinador: Nodo 3
[BULLY] Iniciando nueva elección...  (inmediatamente después)
```

**Causa raíz identificada:** Cuando un nodo se reiniciaba y anunciaba que era coordinador:
1. Los otros nodos recibían el mensaje COORDINATOR
2. Actualizaban `idCoordinadorActual = 3`
3. **PERO no marcaban al Nodo 3 como activo** en su mapa de nodos
4. El monitor de heartbeats verificaba si el coordinador estaba vivo usando `coordinador.estaVivo()`
5. Como `estaVivo()` requiere `activo == true AND heartbeat reciente`, fallaba
6. Disparaba inmediatamente una nueva elección

**Código problemático en `estaVivo()`:**
```java
public boolean estaVivo(long timeout) {
    long ahora = System.currentTimeMillis();
    return activo && (ahora - ultimoLatido) < timeout;  // activo era false
}
```

### Solución Aplicada

Se modificaron tres métodos en `servidor/GestorEleccion.java` para marcar nodos como activos cuando envían mensajes:

#### 1. `manejarCoordinator()` (línea 300)
```java
private void manejarCoordinator(MensajeBully mensaje) {
    int idNuevoCoord = mensaje.getIdEmisor();

    // Si el nuevo coordinador tiene ID menor que yo, rechazar e iniciar elección
    if (idNuevoCoord < nodoLocal.getId()) {
        System.out.println("[BULLY] Rechazado coordinador " + idNuevoCoord +
                         " (ID menor que " + nodoLocal.getId() + "). Iniciando elección...");
        poolHilos.submit(() -> iniciarEleccion());
        return;
    }

    System.out.println("[BULLY] Nuevo coordinador: Nodo " + idNuevoCoord);
    idCoordinadorActual = idNuevoCoord;

    // Marcar al nuevo coordinador como activo
    NodoSubasta nodoCoordinador = nodos.get(idNuevoCoord);
    if (nodoCoordinador != null) {
        nodoCoordinador.setActivo(true);  // ← NUEVO
        nodoCoordinador.actualizarLatido();
        System.out.println("[BULLY] Nodo " + idNuevoCoord + " marcado como activo");
    }

    synchronized(lockEleccion) {
        enEleccion = false;
    }
}
```

**Mejoras:**
- Marca al coordinador como activo cuando se recibe mensaje COORDINATOR
- Valida que el nuevo coordinador tiene ID mayor que el nodo local
- Rechaza coordinadores con ID menor e inicia elección defensiva

#### 2. `manejarHeartbeat()` (línea 317)
```java
private void manejarHeartbeat(MensajeBully mensaje) {
    int idEmisor = mensaje.getIdEmisor();
    NodoSubasta nodo = nodos.get(idEmisor);

    if (nodo != null) {
        // Marcar nodo como activo si envía heartbeat
        nodo.setActivo(true);  // ← NUEVO
        nodo.actualizarLatido();
    }

    if (idEmisor == idCoordinadorActual) {
        if (nodo != null) {
            nodo.setActivo(true);  // ← NUEVO
            nodo.actualizarLatido();
        }
    }
}
```

**Mejoras:**
- Marca cualquier nodo que envíe heartbeat como activo
- Esto permite que nodos reiniciados sean reconocidos como vivos

#### 3. `manejarElection()` (línea 275)
```java
private void manejarElection(MensajeBully mensaje, Socket socketOrigen) throws IOException {
    System.out.println("[BULLY] Recibido ELECTION de nodo " + mensaje.getIdEmisor());

    // Marcar al nodo emisor como activo (si envía ELECTION, está vivo)
    NodoSubasta nodoEmisor = nodos.get(mensaje.getIdEmisor());
    if (nodoEmisor != null) {
        nodoEmisor.setActivo(true);  // ← NUEVO
        nodoEmisor.actualizarLatido();
    }

    // Responder con OK
    MensajeBully respuesta = new MensajeBully(MensajeBully.TipoMensaje.OK, nodoLocal.getId());
    ObjectOutputStream out = new ObjectOutputStream(socketOrigen.getOutputStream());
    out.writeObject(respuesta);
    out.flush();

    System.out.println("[BULLY] Enviado OK a nodo " + mensaje.getIdEmisor());

    // Iniciar mi propia elección (tengo mayor ID)
    poolHilos.submit(() -> iniciarEleccion());
}
```

**Mejoras:**
- Marca al nodo emisor como activo cuando recibe mensaje ELECTION
- Permite reconocer nodos reiniciados que inician elecciones

### Verificación de la Corrección

**Escenario de prueba completo:**

1. **Estado inicial:** Nodo 3 es coordinador
2. **Paso 1:** Detener Nodo 3
   - Resultado: Nodo 2 se convierte en coordinador ✓
3. **Paso 2:** Reiniciar Nodo 3
   - Nodo 3 inicia y anuncia que es coordinador
   - Nodos 1 y 2 reciben mensaje COORDINATOR
4. **Paso 3:** Verificar reconocimiento
   ```
   [BULLY] Nuevo coordinador: Nodo 3
   [BULLY] Nodo 3 marcado como activo  ← Nuevo log
   [BULLY] Mensaje recibido: MensajeBully{tipo=HEARTBEAT, idEmisor=3...}
   ```
5. **Paso 4:** Esperar 15+ segundos
   - Resultado: Nodo 3 permanece como coordinador ✓
   - No se disparan nuevas elecciones ✓

**Resultados:**

✅ **Reconocimiento exitoso:** Nodos marcan a Nodo 3 como activo
✅ **Validación de ID:** Rechaza coordinadores con ID menor
✅ **Persistencia:** Nodo 3 se mantiene como coordinador
✅ **Heartbeats:** Flujo normal de heartbeats entre nodos
✅ **Sin elecciones espurias:** No hay elecciones innecesarias después del reinicio

### Archivos Modificados
- `servidor/GestorEleccion.java`:
  - Método `manejarCoordinator()` (línea 300)
  - Método `manejarHeartbeat()` (línea 317)
  - Método `manejarElection()` (línea 275)

### Principio de Diseño

**Lección aprendida:** En sistemas distribuidos, cualquier nodo que envíe mensajes debe ser considerado activo. El flag `activo` debe actualizarse dinámicamente basándose en la comunicación recibida, no solo en timeouts.

**Regla implementada:** "Si un nodo se comunica, está vivo"
- Mensaje COORDINATOR → marcar emisor como activo
- Mensaje HEARTBEAT → marcar emisor como activo
- Mensaje ELECTION → marcar emisor como activo

---

**Autor**: Sistema de Subasta Distribuido
**Fecha Inicial**: 2025-12-02
**Última Actualización**: 2025-12-10
**Versión**: 1.1
**Licencia**: Proyecto Académico
