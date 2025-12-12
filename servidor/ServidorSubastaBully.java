package socket.conconexion.servidor;

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Servidor de Subasta Distribuido con Protocolo Bully
 *
 * Implementa un sistema de subastas distribuido donde múltiples servidores
 * coordinan usando el algoritmo Bully para elección de líder.
 *
 * Solo el coordinador acepta clientes y maneja la subasta.
 * Los demás servidores redirigen clientes al coordinador.
 *
 * @author Sistema de Subasta Distribuido
 */
public class ServidorSubastaBully {
    // Estados del servidor
    private enum RolServidor {
        INICIALIZANDO,  // Iniciando y descubriendo red
        PARTICIPANTE,   // Nodo normal, no coordinador
        COORDINADOR,    // Coordinador actual (maneja subasta)
        EN_ELECCION     // Participando en elección
    }

    // Estados de la subasta (solo para coordinador)
    private enum EstadoSubasta {
        ESPERANDO,      // Esperando clientes para iniciar
        ACTIVA,         // Subasta en curso
        FINALIZADA      // Subasta terminada, procesando ganador
    }

    // Configuración del nodo
    private NodoSubasta nodoLocal;
    private GestorEleccion gestorEleccion;
    private volatile RolServidor rolActual;

    // Servidor para clientes de subasta
    private ServerSocket serverSocketClientes;
    private Thread hiloAceptarClientes;

    // Servidor para comunicación entre nodos (protocolo Bully)
    private ServerSocket serverSocketCoordinacion;
    private Thread hiloCoordinacion;

    // Lista de todos los nodos del sistema (cargada desde config)
    private List<NodoSubasta> todosLosNodos;

    // Variables de subasta (solo si es coordinador)
    private static final int TIEMPO_SUBASTA = 120000; // Dos minutos
    private List<HiloClienteSubastaBully> clientes;
    private volatile EstadoSubasta estadoSubasta;
    private long tiempoInicio;
    private Timer timerSubasta;
    private Timer timerBroadcast;
    private volatile double propuestaMasAlta = 0.0;
    private volatile String ipPropuestaMasAlta = "ninguno";
    private final Object lockSubasta = new Object();
    private int numeroSubasta = 0;

    private volatile boolean activo = true;

    /**
     * Constructor
     */
    public ServidorSubastaBully(int miId, String miHost, int puertoClientes,
                                int puertoCoordinacion, String archivoConfig)
            throws IOException {

        // Crear nodo local
        this.nodoLocal = new NodoSubasta(miId, miHost, puertoClientes, puertoCoordinacion);
        this.rolActual = RolServidor.INICIALIZANDO;
        this.clientes = new CopyOnWriteArrayList<>();
        this.estadoSubasta = EstadoSubasta.ESPERANDO;

        System.out.println("===========================================");
        System.out.println("  SERVIDOR SUBASTA DISTRIBUIDO - BULLY    ");
        System.out.println("===========================================");
        System.out.println("ID del nodo: " + miId);
        System.out.println("Puerto clientes: " + puertoClientes);
        System.out.println("Puerto coordinación: " + puertoCoordinacion);

        // Cargar configuración de nodos
        cargarConfiguracionNodos(archivoConfig);

        // Crear sockets
        this.serverSocketClientes = new ServerSocket(puertoClientes);
        this.serverSocketCoordinacion = new ServerSocket(puertoCoordinacion);

        // Inicializar gestor de elección
        this.gestorEleccion = new GestorEleccion(nodoLocal, todosLosNodos);

        System.out.println("\n[INIT] Servidor inicializado correctamente");
    }

    /**
     * Carga la configuración de nodos desde archivo
     */
    private void cargarConfiguracionNodos(String archivo) throws IOException {
        todosLosNodos = new ArrayList<>();

        File archivoConfig = new File(archivo);
        if (!archivoConfig.exists()) {
            System.out.println("[WARN] Archivo de configuración no existe: " + archivo);
            System.out.println("[WARN] Usando solo nodo local");
            todosLosNodos.add(nodoLocal);
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
            String linea;
            int lineaNum = 0;

            System.out.println("\n[CONFIG] Cargando nodos desde " + archivo);

            while ((linea = br.readLine()) != null) {
                lineaNum++;
                linea = linea.trim();

                // Ignorar comentarios y líneas vacías
                if (linea.isEmpty() || linea.startsWith("#")) {
                    continue;
                }

                try {
                    // Formato: id,host,puertoClientes,puertoCoordinacion
                    String[] partes = linea.split(",");
                    if (partes.length != 4) {
                        System.out.println("[WARN] Línea " + lineaNum + " inválida: " + linea);
                        continue;
                    }

                    int id = Integer.parseInt(partes[0].trim());
                    String host = partes[1].trim();
                    int pClientes = Integer.parseInt(partes[2].trim());
                    int pCoord = Integer.parseInt(partes[3].trim());

                    NodoSubasta nodo = new NodoSubasta(id, host, pClientes, pCoord);
                    todosLosNodos.add(nodo);

                    System.out.println("  [+] Nodo " + id + ": " + host +
                                     " (clientes:" + pClientes + ", coord:" + pCoord + ")");

                } catch (Exception e) {
                    System.out.println("[WARN] Error en línea " + lineaNum + ": " + e.getMessage());
                }
            }
        }

        if (todosLosNodos.isEmpty()) {
            todosLosNodos.add(nodoLocal);
        }

        System.out.println("[CONFIG] Total de nodos: " + todosLosNodos.size());
    }

    /**
     * Inicia el servidor
     */
    public void iniciar() {
        System.out.println("\n[START] Iniciando servidor...");

        // Iniciar hilo de coordinación (Bully)
        iniciarHiloCoordinacion();

        // Esperar un poco para estabilización
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Iniciar elección
        System.out.println("\n[BULLY] Iniciando proceso de elección inicial...");
        gestorEleccion.iniciarEleccion();

        // Esperar resultado de elección
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Determinar rol
        actualizarRol();

        // Iniciar aceptación de clientes
        iniciarHiloClientes();

        System.out.println("\n[READY] Servidor operativo");
    }

    /**
     * Inicia el hilo que acepta conexiones de clientes
     */
    private void iniciarHiloClientes() {
        hiloAceptarClientes = new Thread(() -> {
            System.out.println("[CLIENTES] Hilo de clientes iniciado");

            while (activo) {
                try {
                    Socket socketCliente = serverSocketClientes.accept();
                    String ipCliente = socketCliente.getInetAddress().getHostAddress();

                    System.out.println("\n[CLIENTE] Nueva conexión desde: " + ipCliente);

                    // Verificar si soy coordinador
                    if (gestorEleccion.esCoordinador()) {
                        manejarCliente(socketCliente, ipCliente);
                    } else {
                        redirigirAlCoordinador(socketCliente);
                    }

                } catch (SocketException e) {
                    if (activo) {
                        System.out.println("[ERROR] Error en socket de clientes: " + e.getMessage());
                    }
                } catch (Exception e) {
                    System.out.println("[ERROR] Error aceptando cliente: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });

        hiloAceptarClientes.start();
    }

    /**
     * Inicia el hilo que maneja comunicación entre servidores (Bully)
     */
    private void iniciarHiloCoordinacion() {
        hiloCoordinacion = new Thread(() -> {
            System.out.println("[COORDINACION] Hilo de coordinación iniciado en puerto " +
                             nodoLocal.getPuertoCoordinacion());

            while (activo) {
                try {
                    Socket socket = serverSocketCoordinacion.accept();

                    // Procesar mensaje Bully en thread separado
                    new Thread(() -> {
                        try {
                            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                            MensajeBully mensaje = (MensajeBully) in.readObject();

                            gestorEleccion.procesarMensaje(mensaje, socket);

                            // Actualizar rol después de procesar mensaje
                            actualizarRol();

                        } catch (Exception e) {
                            System.out.println("[ERROR] Procesando mensaje Bully: " + e.getMessage());
                        } finally {
                            try {
                                socket.close();
                            } catch (IOException e) {
                                // Ignorar
                            }
                        }
                    }).start();

                } catch (SocketException e) {
                    if (activo) {
                        System.out.println("[ERROR] Socket de coordinación cerrado");
                    }
                } catch (Exception e) {
                    System.out.println("[ERROR] En hilo de coordinación: " + e.getMessage());
                }
            }
        });

        hiloCoordinacion.start();
    }

    /**
     * Actualiza el rol del servidor basado en el estado de elección
     */
    private void actualizarRol() {
        RolServidor rolAnterior = rolActual;

        if (gestorEleccion.esCoordinador()) {
            rolActual = RolServidor.COORDINADOR;
        } else {
            rolActual = RolServidor.PARTICIPANTE;
        }

        if (rolAnterior != rolActual) {
            System.out.println("\n===========================================");
            System.out.println("  CAMBIO DE ROL: " + rolActual + "           ");
            System.out.println("===========================================");

            if (rolActual == RolServidor.COORDINADOR) {
                iniciarModoCoordinador();
            } else {
                iniciarModoParticipante();
            }
        }
    }

    /**
     * Configura el servidor como coordinador
     */
    private void iniciarModoCoordinador() {
        System.out.println("[COORDINADOR] Este servidor manejará las subastas");
        estadoSubasta = EstadoSubasta.ESPERANDO;
    }

    /**
     * Configura el servidor como participante
     */
    private void iniciarModoParticipante() {
        System.out.println("[PARTICIPANTE] Redirigiendo clientes al coordinador");

        // Cancelar subasta si había una activa
        if (timerSubasta != null) {
            timerSubasta.cancel();
        }
        if (timerBroadcast != null) {
            timerBroadcast.cancel();
        }

        // Cerrar clientes existentes
        for (HiloClienteSubastaBully cliente : clientes) {
            cliente.cerrarConexion();
        }
        clientes.clear();
    }

    /**
     * Redirige un cliente al coordinador actual
     */
    private void redirigirAlCoordinador(Socket socketCliente) {
        try {
            NodoSubasta coordinador = gestorEleccion.getCoordinador();

            if (coordinador == null) {
                MiSocketStream tempSocket = new MiSocketStream(socketCliente);
                tempSocket.enviaMensaje("ERROR:No hay coordinador disponible. Intente más tarde.");
                tempSocket.close();
                System.out.println("[REDIRECCION] No hay coordinador, cliente rechazado");
                return;
            }

            System.out.println("[REDIRECCION] Redirigiendo cliente al nodo " + coordinador.getId());

            // Para clientes externos (fuera de Docker), usar localhost en lugar del hostname interno
            String hostRedireccion = coordinador.getHost();
            String externalHost = System.getenv("EXTERNAL_HOST");

            // Si estamos en Docker y hay EXTERNAL_HOST configurado, usarlo para redirecciones
            if (externalHost != null && !externalHost.isEmpty()) {
                hostRedireccion = externalHost;
                System.out.println("[REDIRECCION] Usando host externo: " + externalHost);
            }

            MiSocketStream tempSocket = new MiSocketStream(socketCliente);
            String mensajeRedireccion = "REDIRECCION:" + hostRedireccion +
                                       ":" + coordinador.getPuertoClientes();
            tempSocket.enviaMensaje(mensajeRedireccion);
            tempSocket.close();

        } catch (Exception e) {
            System.out.println("[ERROR] Al redirigir cliente: " + e.getMessage());
        }
    }

    /**
     * Maneja un cliente de subasta (solo si es coordinador)
     */
    private void manejarCliente(Socket socketCliente, String ipCliente) {
        try {
            // Si es el primer cliente, iniciar subasta
            if (estadoSubasta == EstadoSubasta.ESPERANDO && clientes.isEmpty()) {
                iniciarNuevaSubasta();
            }

            // Verificar si aún hay tiempo
            if (estadoSubasta == EstadoSubasta.ACTIVA) {
                long tiempoTranscurrido = System.currentTimeMillis() - tiempoInicio;
                if (tiempoTranscurrido >= TIEMPO_SUBASTA) {
                    System.out.println("[RECHAZADO] Subasta cerrada");
                    MiSocketStream tempSocket = new MiSocketStream(socketCliente);
                    tempSocket.enviaMensaje("ERROR:Subasta cerrada");
                    tempSocket.close();
                    return;
                }
            }

            // Crear hilo para manejar el cliente
            HiloClienteSubastaBully hiloCliente = new HiloClienteSubastaBully(
                new MiSocketStream(socketCliente),
                ipCliente,
                this
            );
            clientes.add(hiloCliente);

            Thread thread = new Thread(hiloCliente);
            thread.start();

            if (estadoSubasta == EstadoSubasta.ACTIVA) {
                hiloCliente.notificarInicioSubasta(getTiempoRestante());
            }

            System.out.println("[COORDINADOR] Cliente aceptado. Total: " + clientes.size());

        } catch (Exception e) {
            System.out.println("[ERROR] Manejando cliente: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Inicia una nueva sesión de subasta (lógica del servidor original)
     */
    private void iniciarNuevaSubasta() {
        numeroSubasta++;
        estadoSubasta = EstadoSubasta.ACTIVA;
        tiempoInicio = System.currentTimeMillis();

        System.out.println("\n*** SUBASTA #" + numeroSubasta + " INICIADA ***");
        System.out.println("Duración: " + (TIEMPO_SUBASTA/1000) + " segundos");

        if (timerSubasta != null) {
            timerSubasta.cancel();
        }
        if (timerBroadcast != null) {
            timerBroadcast.cancel();
        }

        timerSubasta = new Timer();
        timerSubasta.schedule(new TimerTask() {
            @Override
            public void run() {
                finalizarSubasta();
            }
        }, TIEMPO_SUBASTA);

        iniciarBroadcastPeriodico();
    }

    private void iniciarBroadcastPeriodico() {
        timerBroadcast = new Timer();
        timerBroadcast.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (estadoSubasta != EstadoSubasta.ACTIVA) {
                    timerBroadcast.cancel();
                    return;
                }
                enviarActualizacionPeriodica();
            }
        }, 5000, 5000);
    }

    private void enviarActualizacionPeriodica() {
        if (clientes.isEmpty()) return;

        // Obtener del estado replicado
        EstadoSubastaReplicado estado = gestorEleccion.getEstadoSubasta();
        Map.Entry<String, Double> ganador = estado.getOfertaGanadora();
        double montoActual = (ganador != null) ? ganador.getValue() : 0.0;

        String update = obtenerPropuestaMasAlta() + ":TIEMPO:" + getTiempoRestante();
        System.out.println("[BROADCAST] Propuesta más alta: $" + montoActual +
                         " (Estado replicado: " + estado.getCantidadOfertas() + " ofertas)");

        for (HiloClienteSubastaBully cliente : clientes) {
            try {
                cliente.enviarActualizacion(update);
            } catch (Exception e) {
                // Ignorar
            }
        }
    }

    private void finalizarSubasta() {
        estadoSubasta = EstadoSubasta.FINALIZADA;

        System.out.println("\n===========================================");
        System.out.println("     SUBASTA #" + numeroSubasta + " FINALIZADA            ");
        System.out.println("===========================================");

        if (clientes.isEmpty()) {
            resetearEstado();
            return;
        }

        // Obtener ganador del estado replicado
        EstadoSubastaReplicado estado = gestorEleccion.getEstadoSubasta();
        Map.Entry<String, Double> entradaGanadora = estado.getOfertaGanadora();

        if (entradaGanadora != null) {
            String ipGanador = entradaGanadora.getKey();
            double propuestaGanadora = entradaGanadora.getValue();
            String mensaje = "GANADOR:" + ipGanador + ":MONTO:" + propuestaGanadora;

            System.out.println("[GANADOR] " + ipGanador + " con $" + propuestaGanadora);

            for (HiloClienteSubastaBully cliente : clientes) {
                cliente.enviarResultado(mensaje);
                cliente.cerrarConexion();
            }
        }

        new Thread(() -> {
            try {
                Thread.sleep(2000);
                resetearEstado();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private void resetearEstado() {
        clientes.clear();
        synchronized(lockSubasta) {
            propuestaMasAlta = 0.0;
            ipPropuestaMasAlta = "ninguno";
        }

        // Limpiar estado replicado si soy coordinador
        if (gestorEleccion.esCoordinador()) {
            gestorEleccion.getEstadoSubasta().limpiar();
            gestorEleccion.replicarEstado(); // Propagar limpieza a otros nodos
        }

        estadoSubasta = EstadoSubasta.ESPERANDO;
        System.out.println("[RESET] Listo para nueva subasta\n");
    }

    public boolean actualizarPropuestaMasAlta(double nuevaPropuesta, String ip) {
        synchronized(lockSubasta) {
            // Obtener ganador ANTES de agregar la nueva oferta
            EstadoSubastaReplicado estado = gestorEleccion.getEstadoSubasta();
            Map.Entry<String, Double> ganadorAnterior = estado.getOfertaGanadora();
            double montoAnterior = (ganadorAnterior != null) ? ganadorAnterior.getValue() : 0.0;

            System.out.println("[VERIFICACION] Oferta nueva: $" + nuevaPropuesta + " de " + ip);
            System.out.println("[VERIFICACION] Ganador anterior: " +
                             (ganadorAnterior != null ? ganadorAnterior.getKey() + " -> $" + ganadorAnterior.getValue() : "ninguno"));

            // Registrar oferta en el estado replicado
            // El gestor de elección se encargará de replicar a todos los nodos si es aceptada
            boolean ofertaAceptada = false;
            if (gestorEleccion.esCoordinador()) {
                ofertaAceptada = gestorEleccion.registrarOferta(ip, nuevaPropuesta);
            }

            // Si la oferta fue rechazada, no continuar
            if (!ofertaAceptada) {
                System.out.println("[OFERTA RECHAZADA] Cliente intentó bajar su oferta");
                return false;
            }

            // Obtener la oferta ganadora DESPUES de agregar
            Map.Entry<String, Double> ganadorActual = estado.getOfertaGanadora();
            System.out.println("[VERIFICACION] Ganador actual: " +
                             (ganadorActual != null ? ganadorActual.getKey() + " -> $" + ganadorActual.getValue() : "ninguno"));

            // Verificar si la nueva propuesta es la más alta
            if (ganadorActual != null && ganadorActual.getKey().equals(ip)) {
                System.out.println("[NUEVA ALTA] $" + nuevaPropuesta + " de " + ip + " es la oferta ganadora");
                return true;
            } else {
                System.out.println("[NO ES ALTA] $" + nuevaPropuesta + " de " + ip + " (perdiendo contra $" +
                                 (ganadorActual != null ? ganadorActual.getValue() : 0) + ")");
                return false;
            }
        }
    }

    public String obtenerPropuestaMasAlta() {
        synchronized(lockSubasta) {
            // Obtener del estado replicado
            EstadoSubastaReplicado estado = gestorEleccion.getEstadoSubasta();
            Map.Entry<String, Double> ganador = estado.getOfertaGanadora();

            if (ganador == null) {
                return "PROPUESTA_ALTA:ninguno:0.0";
            }
            return "PROPUESTA_ALTA:" + ganador.getKey() + ":" + ganador.getValue();
        }
    }

    public Double obtenerOfertaCliente(String clienteId) {
        synchronized(lockSubasta) {
            EstadoSubastaReplicado estado = gestorEleccion.getEstadoSubasta();
            return estado.getOferta(clienteId);
        }
    }

    public boolean estaActiva() {
        return estadoSubasta == EstadoSubasta.ACTIVA;
    }

    public long getTiempoRestante() {
        if (estadoSubasta != EstadoSubasta.ACTIVA) return 0;
        long transcurrido = System.currentTimeMillis() - tiempoInicio;
        long restante = (TIEMPO_SUBASTA - transcurrido) / 1000;
        return Math.max(0, restante);
    }

    /**
     * Detiene el servidor
     */
    public void detener() {
        System.out.println("\n[SHUTDOWN] Deteniendo servidor...");
        activo = false;

        try {
            serverSocketClientes.close();
            serverSocketCoordinacion.close();
            gestorEleccion.detener();
        } catch (IOException e) {
            // Ignorar
        }

        System.out.println("[SHUTDOWN] Servidor detenido");
    }

    /**
     * Punto de entrada principal
     */
    public static void main(String[] args) {
        try {
            if (args.length < 4) {
                System.out.println("Uso: java ServidorSubastaBully <id> <puerto_clientes> <puerto_coord> <archivo_config> [host]");
                System.out.println();
                System.out.println("Ejemplo:");
                System.out.println("  java socket.conconexion.servidor.ServidorSubastaBully 1 8080 9080 nodos.conf");
                System.out.println("  java socket.conconexion.servidor.ServidorSubastaBully 2 8081 9081 nodos.conf");
                System.out.println("  java socket.conconexion.servidor.ServidorSubastaBully 3 8082 9082 nodos.conf");
                System.out.println();
                System.out.println("Para Docker (usar localhost):");
                System.out.println("  java socket.conconexion.servidor.ServidorSubastaBully 1 8080 9080 nodos.conf localhost");
                return;
            }

            int id = Integer.parseInt(args[0]);
            int puertoClientes = Integer.parseInt(args[1]);
            int puertoCoord = Integer.parseInt(args[2]);
            String archivoConfig = args[3];

            // Si se proporciona host como argumento, usarlo; sino detectar automaticamente
            String miHost;
            if (args.length >= 5) {
                miHost = args[4];
                System.out.println("[CONFIG] Usando host especificado: " + miHost);
            } else {
                try {
                    miHost = InetAddress.getLocalHost().getHostAddress();
                    System.out.println("[CONFIG] Host detectado automaticamente: " + miHost);
                } catch (Exception e) {
                    miHost = "localhost";
                    System.out.println("[CONFIG] No se pudo detectar host, usando: localhost");
                }
            }

            ServidorSubastaBully servidor = new ServidorSubastaBully(
                id, miHost, puertoClientes, puertoCoord, archivoConfig
            );

            servidor.iniciar();

            // Mantener servidor vivo
            Thread.currentThread().join();

        } catch (Exception e) {
            System.out.println("Error fatal: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
