# Solución al Error de Redirección en Docker

## Problema

Cuando un cliente se conecta desde el **host** (tu máquina) a un servidor Docker, y el servidor intenta redirigirlo al coordinador, se produce el error:

```
java.net.UnknownHostException: nodo-bully-3
```

## Causa

El servidor envía el hostname interno de Docker (`nodo-bully-3`) que solo existe dentro de la red Docker. El cliente corriendo en el host no puede resolver ese hostname.

## Solución Aplicada

Se cambió la configuración de Docker para usar `network_mode: host`, lo que hace que los contenedores compartan la red del host:

### Cambios Realizados

**1. nodos-docker.conf** - Usar `localhost` en lugar de hostnames Docker:
```
1,localhost,8080,9080
2,localhost,8081,9081
3,localhost,8082,9082
```

**2. docker-compose-bully.yml** - Agregar `network_mode: host`:
```yaml
nodo-bully-1:
  network_mode: host  # Compartir red del host
  # ...
```

## Cómo Probarlo

1. **Detener servicios actuales:**
```bash
docker-compose -f docker-compose-bully.yml down
```

2. **Recompilar el código Java (cambios en ServidorSubastaBully):**
```bash
# Windows
compilar_bully.bat

# Linux/Mac
./compilar_bully.sh
```

3. **Rebuild Docker (la configuración y código cambiaron):**
```bash
docker-compose -f docker-compose-bully.yml build --no-cache
```

4. **Levantar servicios con nueva configuración:**
```bash
docker-compose -f docker-compose-bully.yml up
```

5. **Conectar cliente:**
```bash
java socket.conconexion.cliente.ClienteSubasta
# Servidor: localhost
# Puerto: 8080 (o 8081, 8082)
```

## Resultado Esperado

```
Conectado al servidor: localhost:8081
[INFO] Servidor redirigiendo al coordinador...
[REDIRECCION] Conectando a coordinador: localhost:8082
[CONECTADO] Servidor de subasta activo

===========================================
     LA SUBASTA HA INICIADO!
===========================================
```

## Ventajas de network_mode: host

✅ Los clientes del host pueden conectarse normalmente
✅ Las redirecciones usan `localhost` (funciona desde el host)
✅ Los servidores se comunican entre sí sin problemas
✅ No hay traducción de puertos (más simple)

## Desventajas

⚠️ Solo funciona en Linux/Mac (limitado en Windows Docker Desktop)
⚠️ Los contenedores tienen acceso completo a la red del host
⚠️ No hay aislamiento de red entre contenedores

## Alternativa para Windows

Si `network_mode: host` no funciona en Windows, hay que usar una solución diferente.

### Opción: Ejecutar Cliente Dentro de Docker

Crear un contenedor para el cliente:

```yaml
cliente-subasta:
  build:
    context: .
    dockerfile: Dockerfile.bully
  container_name: cliente-subasta
  stdin_open: true
  tty: true
  command: ["java", "socket.conconexion.cliente.ClienteSubasta"]
  networks:
    - red-bully
```

Luego ejecutar:
```bash
docker-compose -f docker-compose-bully.yml run --rm cliente-subasta
```

En este caso, el cliente estará dentro de la red Docker y podrá resolver `nodo-bully-3`.

## Verificación

```bash
# Ver logs del coordinador
docker logs -f subasta-bully-nodo-3

# Deberías ver:
# [NUEVO CLIENTE] Conectado: <IP>
# [COORDINADOR] Cliente aceptado
```

## Notas Importantes

- Con `network_mode: host`, los contenedores usan directamente los puertos del host
- No hay mapeo de puertos (8080:8080 se ignora con host mode)
- Los puertos 8080-8082 y 9080-9082 deben estar libres en tu máquina
- Asegúrate de que ningún otro proceso use esos puertos

## Troubleshooting

### Error: "Cannot start service... port is already allocated"

```bash
# Ver qué usa el puerto
netstat -ano | findstr :8080

# Detener proceso o cambiar puerto en nodos-docker.conf
```

### Los nodos no se comunican entre sí

```bash
# Verificar que los 3 nodos están corriendo
docker ps

# Ver logs de elección
docker logs subasta-bully-nodo-3
```

### Cliente sigue sin conectarse

```bash
# Verificar compilación del cliente
javac -encoding UTF-8 -d . cliente/*.java servidor/MiSocketStream.java

# Verificar que el servidor está escuchando
netstat -ano | findstr :8082
```
