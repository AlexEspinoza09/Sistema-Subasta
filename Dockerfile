# Multi-stage build para reducir el tamaño de la imagen final
FROM eclipse-temurin:17-jdk AS builder

# Directorio de trabajo
WORKDIR /app

# Crear estructura de directorios para los paquetes
RUN mkdir -p socket/conconexion/servidor && \
    mkdir -p socket/conconexion/cliente

# Copiar archivos de servidor
COPY servidor/*.java socket/conconexion/servidor/

# Copiar archivos de cliente
COPY cliente/*.java socket/conconexion/cliente/

# Compilar primero el servidor (incluye MiSocketStream)
RUN javac socket/conconexion/servidor/*.java

# Luego compilar el cliente (puede usar las clases del servidor)
RUN javac -cp . socket/conconexion/cliente/*.java

# Imagen final más ligera
FROM eclipse-temurin:17-jre

# Directorio de trabajo
WORKDIR /app

# Copiar toda la estructura de paquetes compilada desde el builder
COPY --from=builder /app/socket ./socket

# Por defecto, ejecutar el servidor Echo concurrente
# Se puede sobrescribir con docker run o docker-compose
CMD ["java", "socket.conconexion.servidor.ServidorEcho3", "7"]
