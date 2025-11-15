# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Java distributed systems project demonstrating client-server communication using TCP sockets. The codebase implements two network protocols (Daytime and Echo) with both sequential and concurrent server architectures.

## Code Architecture

### Package Structure

All code uses the package namespace `socket.conconexion` with two sub-packages:
- `socket.conconexion.servidor` - Server implementations
- `socket.conconexion.cliente` - Client implementations

### Core Components

**MiSocketStream.java**
- Wrapper class around Java's Socket that simplifies message sending/receiving
- Provides `enviaMensaje(String)` and `recibeMensaje()` methods
- Handles BufferedReader and PrintWriter setup internally
- Used by both client and server implementations

**Server Architectures**
- `ServidorDaytime2.java` - Sequential server returning timestamp (port 13 default)
- `ServidorEcho2.java` - Sequential echo server (port 7 default)
- `ServidorEcho3.java` - **Concurrent** echo server using threads (port 7 default)
- `HiloServidorEcho.java` - Runnable worker thread for ServidorEcho3
- `ServidorSubasta.java` - **Auction server** with timer and broadcast (port 8080 default)
- `HiloClienteSubasta.java` - Worker thread that maintains client connection during auction

Key differences:
- ServidorEcho2 handles one client at a time in main thread
- ServidorEcho3 spawns a new thread per client connection
- ServidorSubasta accepts multiple clients, waits 30 seconds, then broadcasts winner to all

**Client Architectures**
- Each client has two classes: presentation layer and application logic layer
- `ClienteDaytime2` / `ClienteDaytimeAuxiliar2` - Daytime client pair
- `ClienteEcho2` / `ClienteEchoAuxiliar2` - Echo client pair
- `ClienteSubasta` / `ClienteSubastaAuxiliar` - Auction client pair
- Presentation classes handle user I/O, auxiliary classes handle socket operations

### Protocol Details

**Echo Protocol**
- Client sends messages, server echoes them back
- Session terminates when client sends "." (single period)
- Both client and server use `mensajeFin = "."` constant

**Daytime Protocol**
- Implicit request (no message sent from client)
- Server responds with timestamp and immediately closes connection

**Auction Protocol**
- Client connects and sends bid amount (double)
- Server confirms receipt: "PROPUESTA_RECIBIDA:$amount"
- Client connection stays open waiting for result
- After 30 seconds, server determines highest bid
- Server broadcasts to ALL connected clients: "GANADOR:IP:MONTO:amount"
- All connections close after result is sent
- Uses CopyOnWriteArrayList for thread-safe client management
- Uses CountDownLatch to synchronize result broadcasting

## Development Commands

### Compilation

Compile all files (with new directory structure):
```bash
javac -d . servidor/*.java cliente/*.java
```

Compile specific server:
```bash
javac -d . servidor/ServidorEcho3.java servidor/MiSocketStream.java
javac -d . servidor/ServidorDaytime2.java servidor/MiSocketStream.java
javac -d . servidor/ServidorSubasta.java servidor/HiloClienteSubasta.java servidor/MiSocketStream.java
```

Compile specific client:
```bash
javac -d . cliente/ClienteEcho2.java cliente/ClienteEchoAuxiliar2.java servidor/MiSocketStream.java
javac -d . cliente/ClienteDaytime2.java cliente/ClienteDaytimeAuxiliar2.java servidor/MiSocketStream.java
javac -d . cliente/ClienteSubasta.java cliente/ClienteSubastaAuxiliar.java servidor/MiSocketStream.java
```

### Running Servers

Start Daytime server (default port 13):
```bash
java socket.conconexion.servidor.ServidorDaytime2
```

Start Daytime server on custom port:
```bash
java socket.conconexion.servidor.ServidorDaytime2 8080
```

Start Echo server (sequential, default port 7):
```bash
java socket.conconexion.servidor.ServidorEcho2
```

Start Echo server (concurrent, custom port):
```bash
java socket.conconexion.servidor.ServidorEcho3 9000
```

Start Auction server (default port 8080):
```bash
java socket.conconexion.servidor.ServidorSubasta
```

Start Auction server on custom port:
```bash
java socket.conconexion.servidor.ServidorSubasta 9090
```

### Running Clients

Start Daytime client (interactive):
```bash
java socket.conconexion.cliente.ClienteDaytime2
```

Start Echo client (interactive):
```bash
java socket.conconexion.cliente.ClienteEcho2
```

Start Auction client (interactive):
```bash
java socket.conconexion.cliente.ClienteSubasta
```

Note: Clients prompt for hostname (default: localhost) and port number interactively. For auction client, you also need to enter your bid amount.

## Docker Deployment

### Building the Docker Image

Build the image:
```bash
docker build -t servidor-sockets .
```

### Running with Docker Compose

Start all servers:
```bash
docker-compose up -d
```

Start specific server:
```bash
docker-compose up -d servidor-echo-concurrente
docker-compose up -d servidor-daytime
docker-compose up -d servidor-echo-secuencial
docker-compose up -d servidor-subasta
```

View logs:
```bash
docker-compose logs -f servidor-echo-concurrente
```

Stop all services:
```bash
docker-compose down
```

### Running Individual Containers

Run Echo server (concurrent):
```bash
docker run -d -p 7:7 --name echo-server servidor-sockets java socket.conconexion.servidor.ServidorEcho3 7
```

Run Daytime server:
```bash
docker run -d -p 13:13 --name daytime-server servidor-sockets java socket.conconexion.servidor.ServidorDaytime2 13
```

Run Auction server:
```bash
docker run -d -p 8080:8080 --name auction-server servidor-sockets java socket.conconexion.servidor.ServidorSubasta 8080
```

### Testing Servers in Docker

Test Daytime server:
```bash
telnet localhost 13
```

Test Echo server:
```bash
telnet localhost 7
```

Or use netcat:
```bash
nc localhost 7
```

Test Auction server (requires compiled client):
```bash
# Compile client locally
javac -d . cliente/ClienteSubasta.java cliente/ClienteSubastaAuxiliar.java servidor/MiSocketStream.java

# Run multiple clients in different terminals to test auction
java socket.conconexion.cliente.ClienteSubasta
```

**Testing auction with multiple bidders:**
1. Start auction server: `docker-compose up servidor-subasta`
2. Open 3+ terminal windows
3. In each window, run the auction client with different bid amounts
4. All clients will wait and receive the same result after 30 seconds
5. View server logs: `docker-compose logs -f servidor-subasta`

### AWS Deployment

**Ports exposed by docker-compose:**
- Port 13: Servidor Daytime
- Port 7: Servidor Echo Concurrente
- Port 7000: Servidor Echo Secuencial
- Port 8080: Servidor Subasta

**For AWS ECS deployment:**
1. Push image to Amazon ECR
2. Create ECS task definition with port mappings
3. Configure security groups to allow inbound traffic on ports 7, 13, 7000, 8080

**For AWS EC2 deployment:**
1. Install Docker and Docker Compose on EC2 instance
2. Clone repository and run `docker-compose up -d`
3. Configure security group to allow inbound TCP on ports 7, 13, 7000, 8080

## Code Conventions

- All code comments and strings are in Spanish
- Debug print statements use `/**/` prefix for easy toggling
- Servers run in infinite loops waiting for connections
- All servers accept optional port number as first command-line argument
- Exception handling uses `ex.printStackTrace()` for debugging
