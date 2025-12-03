# Despliegue Docker del Sistema Bully

Esta guía explica cómo ejecutar el sistema de subasta distribuido con protocolo Bully usando Docker y Docker Compose.

## 📋 Requisitos Previos

- Docker instalado (versión 20.10+)
- Docker Compose instalado (versión 1.29+)
- Al menos 2GB de RAM disponible

## 🏗️ Arquitectura Docker

El sistema usa:
- **3 contenedores** (uno por cada nodo del sistema distribuido)
- **Red bridge personalizada** (`red-subasta-bully`) para comunicación entre nodos
- **Puertos expuestos**:
  - `8080-8082`: Puertos para clientes de subasta
  - `9080-9082`: Puertos para coordinación Bully (comunicación entre servidores)

## 🚀 Inicio Rápido

### 1. Construir las Imágenes

```bash
cd "c:\Users\javier\Documents\Proyecto Distribuidos"

# Construir imagen (primera vez o después de cambios en código)
docker-compose -f docker-compose-bully.yml build
```

### 2. Levantar los Servidores

```bash
# Iniciar todos los nodos (modo attached - ves logs en consola)
docker-compose -f docker-compose-bully.yml up

# O en modo detached (en background)
docker-compose -f docker-compose-bully.yml up -d
```

**Salida esperada:**
```
Creating network "red-subasta-bully" with driver "bridge"
Creating subasta-bully-nodo-1 ... done
Creating subasta-bully-nodo-2 ... done
Creating subasta-bully-nodo-3 ... done
```

### 3. Verificar que los Servidores están Activos

```bash
# Ver estado de contenedores
docker-compose -f docker-compose-bully.yml ps

# Ver logs de todos los nodos
docker-compose -f docker-compose-bully.yml logs -f

# Ver logs de un nodo específico
docker-compose -f docker-compose-bully.yml logs -f nodo-bully-3
```

### 4. Conectar Clientes

Desde tu máquina host (fuera de Docker):

```bash
# Compilar cliente localmente
javac -encoding UTF-8 -d . cliente/ClienteSubastaAuxiliar.java cliente/ClienteSubasta.java servidor/MiSocketStream.java

# Ejecutar cliente
java socket.conconexion.cliente.ClienteSubasta
```

Cuando se solicite:
- **Servidor**: `localhost`
- **Puerto**: `8080`, `8081` o `8082` (cualquiera funciona, serás redirigido automáticamente al coordinador)

## 📊 Comandos Útiles

### Ver Logs en Tiempo Real

```bash
# Todos los nodos
docker-compose -f docker-compose-bully.yml logs -f

# Solo nodo 3 (coordinador por defecto)
docker logs -f subasta-bully-nodo-3

# Últimas 50 líneas del nodo 2
docker logs --tail 50 subasta-bully-nodo-2
```

### Detener y Eliminar Contenedores

```bash
# Detener servicios
docker-compose -f docker-compose-bully.yml stop

# Detener y eliminar contenedores
docker-compose -f docker-compose-bully.yml down

# Eliminar contenedores, red Y volúmenes
docker-compose -f docker-compose-bully.yml down -v
```

### Reiniciar un Nodo Específico

```bash
# Reiniciar nodo 3 (coordinador)
docker-compose -f docker-compose-bully.yml restart nodo-bully-3

# Detener solo el nodo 3
docker-compose -f docker-compose-bully.yml stop nodo-bully-3

# Volver a levantar el nodo 3
docker-compose -f docker-compose-bully.yml start nodo-bully-3
```

### Rebuild (Reconstruir) Después de Cambios en Código

```bash
# Rebuild completo
docker-compose -f docker-compose-bully.yml build --no-cache

# Rebuild y reiniciar
docker-compose -f docker-compose-bully.yml up --build
```

## 🧪 Pruebas del Protocolo Bully con Docker

### Prueba 1: Verificar Elección Inicial

1. Levantar los 3 nodos:
```bash
docker-compose -f docker-compose-bully.yml up
```

2. Observar en los logs que **Nodo 3** (mayor ID) es elegido coordinador:
```
nodo-bully-3  | [BULLY] No hay nodos superiores. Asumiendo coordinación.
nodo-bully-3  | ===========================================
nodo-bully-3  |   NUEVO COORDINADOR: Nodo 3
```

### Prueba 2: Tolerancia a Fallos

1. Con los 3 nodos activos, detener el coordinador (Nodo 3):
```bash
docker-compose -f docker-compose-bully.yml stop nodo-bully-3
```

2. Observar en logs del Nodo 2 que detecta la falla e inicia elección:
```bash
docker logs -f subasta-bully-nodo-2
```

3. Verificar que Nodo 2 se convierte en coordinador

4. Conectar cliente al puerto 8081 - debe funcionar normalmente

### Prueba 3: Recuperación del Coordinador

1. Con Nodo 2 como coordinador, reiniciar Nodo 3:
```bash
docker-compose -f docker-compose-bully.yml start nodo-bully-3
```

2. Observar que Nodo 3:
   - Inicia elección
   - Recupera el rol de coordinador (tiene mayor ID)

3. Nuevos clientes son redirigidos al Nodo 3

### Prueba 4: Falla en Cascada

1. Detener Nodo 3:
```bash
docker-compose -f docker-compose-bully.yml stop nodo-bully-3
```

2. Nodo 2 se convierte en coordinador

3. Detener Nodo 2:
```bash
docker-compose -f docker-compose-bully.yml stop nodo-bully-2
```

4. Nodo 1 se convierte en coordinador (único nodo vivo)

5. Levantar Nodo 2 y 3:
```bash
docker-compose -f docker-compose-bully.yml start nodo-bully-2 nodo-bully-3
```

6. Eventualmente Nodo 3 recupera coordinación

## 🔍 Inspección de Red

### Ver Red Creada

```bash
docker network inspect red-subasta-bully
```

### Ver IPs de los Contenedores

```bash
docker-compose -f docker-compose-bully.yml ps
docker inspect subasta-bully-nodo-1 | grep IPAddress
docker inspect subasta-bully-nodo-2 | grep IPAddress
docker inspect subasta-bully-nodo-3 | grep IPAddress
```

### Entrar a un Contenedor

```bash
# Abrir shell en el nodo 1
docker exec -it subasta-bully-nodo-1 /bin/bash

# Dentro del contenedor, ver archivos
ls -la
cat nodos-docker.conf

# Verificar procesos Java
ps aux | grep java

# Salir
exit
```

## 🐛 Solución de Problemas

### Error: "Port already in use"

```bash
# Ver qué proceso usa el puerto
netstat -ano | findstr :8080    # Windows
lsof -i :8080                   # Linux/Mac

# Detener servicios conflictivos
docker-compose -f docker-compose-bully.yml down

# O cambiar puertos en docker-compose-bully.yml
```

### Los Nodos No Se Comunican

1. Verificar que la red existe:
```bash
docker network ls | grep bully
```

2. Verificar conectividad entre contenedores:
```bash
docker exec subasta-bully-nodo-1 ping nodo-bully-3
```

3. Verificar archivo de configuración:
```bash
docker exec subasta-bully-nodo-1 cat nodos-docker.conf
```

### Logs No Aparecen

```bash
# Forzar recreación de contenedores
docker-compose -f docker-compose-bully.yml up --force-recreate

# Ver logs sin seguimiento
docker-compose -f docker-compose-bully.yml logs
```

### Error de Compilación en Build

```bash
# Rebuild sin caché
docker-compose -f docker-compose-bully.yml build --no-cache

# Ver salida completa de build
docker-compose -f docker-compose-bully.yml build --progress=plain
```

## 📈 Monitoreo

### Ver Recursos Usados

```bash
# Ver uso de CPU y memoria
docker stats

# Ver solo contenedores Bully
docker stats subasta-bully-nodo-1 subasta-bully-nodo-2 subasta-bully-nodo-3
```

### Exportar Logs

```bash
# Exportar logs a archivo
docker-compose -f docker-compose-bully.yml logs > logs-bully.txt

# Logs con timestamp
docker-compose -f docker-compose-bully.yml logs -t > logs-timestamped.txt
```

## 🌐 Despliegue en Servidor Remoto

### Opción 1: Docker Compose en VM

1. Copiar proyecto a servidor:
```bash
scp -r "Proyecto Distribuidos" user@servidor:/home/user/
```

2. En el servidor:
```bash
cd "Proyecto Distribuidos"
docker-compose -f docker-compose-bully.yml up -d
```

3. Abrir puertos en firewall:
```bash
# Ejemplo para Ubuntu/Debian
sudo ufw allow 8080:8082/tcp
sudo ufw allow 9080:9082/tcp
```

### Opción 2: Docker Swarm (Cluster)

```bash
# Inicializar swarm
docker swarm init

# Desplegar stack
docker stack deploy -c docker-compose-bully.yml bully-stack

# Ver servicios
docker service ls

# Escalar (opcional)
docker service scale bully-stack_nodo-bully-1=2
```

## 🔐 Seguridad

### Mejores Prácticas

1. **Red aislada**: Los nodos se comunican en red privada
2. **Solo exponer puertos necesarios**: Puertos de coordinación (908x) podrían no exponerse al host
3. **Usuarios no-root**: Agregar en Dockerfile:
```dockerfile
RUN addgroup --system javauser && adduser --system --group javauser
USER javauser
```

## 📝 Archivos de Configuración

### Estructura

```
Proyecto Distribuidos/
├── Dockerfile.bully              # Dockerfile para sistema Bully
├── docker-compose-bully.yml      # Orquestación de 3 nodos
├── nodos-docker.conf             # Config para Docker (usa hostnames)
├── nodos.conf                    # Config para localhost
└── servidor/
    └── *.java
```

### Diferencias entre nodos.conf y nodos-docker.conf

**nodos.conf (localhost):**
```
1,localhost,8080,9080
2,localhost,8081,9081
3,localhost,8082,9082
```

**nodos-docker.conf (Docker):**
```
1,nodo-bully-1,8080,9080
2,nodo-bully-2,8081,9081
3,nodo-bully-3,8082,9082
```

## ✅ Checklist de Despliegue

- [ ] Docker y Docker Compose instalados
- [ ] Código compilado localmente al menos una vez
- [ ] Puertos 8080-8082 y 9080-9082 disponibles
- [ ] Archivo `nodos-docker.conf` existe
- [ ] Build completado: `docker-compose -f docker-compose-bully.yml build`
- [ ] Servicios levantados: `docker-compose -f docker-compose-bully.yml up`
- [ ] Logs verificados: Nodo 3 es coordinador
- [ ] Cliente de prueba conectado y funcionando

## 🎯 Resumen de Comandos Clave

```bash
# Build
docker-compose -f docker-compose-bully.yml build

# Start
docker-compose -f docker-compose-bully.yml up -d

# Logs
docker-compose -f docker-compose-bully.yml logs -f

# Stop
docker-compose -f docker-compose-bully.yml down

# Restart un nodo
docker-compose -f docker-compose-bully.yml restart nodo-bully-3

# Rebuild
docker-compose -f docker-compose-bully.yml up --build
```

---

**¿Necesitas ayuda?** Revisa los logs con `docker-compose logs -f` y busca mensajes de error.
