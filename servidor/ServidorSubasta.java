package socket.conconexion.servidor;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Servidor de Subasta que acepta multiples clientes, recibe sus propuestas,
 * espera 2 minutos y notifica a todos los clientes sobre el ganador.
 * Después de cada subasta, se reinicia automáticamente para una nueva sesión.
 * @author Sistema de Subasta
 */
public class ServidorSubasta {
    // Estados de la subasta
    private enum EstadoSubasta {
        ESPERANDO,   // Esperando clientes para iniciar
        ACTIVA,      // Subasta en curso
        FINALIZADA   // Subasta terminada, procesando ganador
    }

    private static final int TIEMPO_SUBASTA = 120000; // Dos minutos en milisegundos
    private static List<HiloClienteSubasta> clientes = new CopyOnWriteArrayList<>();
    private static volatile EstadoSubasta estadoActual = EstadoSubasta.ESPERANDO;
    private static long tiempoInicio;
    private static Timer timerSubasta;
    private static Timer timerBroadcast;

    // Propuesta más alta actual (thread-safe)
    private static volatile double propuestaMasAlta = 0.0;
    private static volatile String ipPropuestaMasAlta = "ninguno";
    private static final Object lock = new Object();
    private static int numeroSubasta = 0;

    public static void main(String[] args) {
        int puertoServidor = 8080; // puerto por defecto para subasta

        if (args.length == 1)
            puertoServidor = Integer.parseInt(args[0]);

        try {
            ServerSocket miSocketConexion = new ServerSocket(puertoServidor);
            System.out.println("===========================================");
            System.out.println("Servidor de Subasta iniciado en puerto " + puertoServidor);
            System.out.println("Modo: CONTINUO (multiples sesiones)");
            System.out.println("===========================================");

            // Loop infinito para manejar multiples sesiones de subasta
            while (true) {
                esperarYProcesarSubasta(miSocketConexion);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Espera clientes, procesa una sesión de subasta y resetea
     */
    private static void esperarYProcesarSubasta(ServerSocket socketConexion) {
        try {
            numeroSubasta++;
            System.out.println("\n╔═══════════════════════════════════════════╗");
            System.out.println("║  SESION #" + numeroSubasta + " - ESPERANDO PARTICIPANTES   ║");
            System.out.println("╚═══════════════════════════════════════════╝");

            estadoActual = EstadoSubasta.ESPERANDO;

            // Aceptar clientes
            while (estadoActual != EstadoSubasta.FINALIZADA) {
                try {
                    System.out.println("[ESPERANDO] Aguardando conexiones...");
                    Socket socketCliente = socketConexion.accept();

                    String ipCliente = socketCliente.getInetAddress().getHostAddress();
                    System.out.println("[NUEVO CLIENTE] Conectado: " + ipCliente);

                    // Si es el primer cliente y estamos esperando, iniciar subasta
                    if (estadoActual == EstadoSubasta.ESPERANDO && clientes.isEmpty()) {
                        iniciarNuevaSubasta();
                    }

                    // Verificar si aun hay tiempo
                    if (estadoActual == EstadoSubasta.ACTIVA) {
                        long tiempoTranscurrido = System.currentTimeMillis() - tiempoInicio;
                        if (tiempoTranscurrido >= TIEMPO_SUBASTA) {
                            System.out.println("[RECHAZADO] Subasta cerrada");
                            MiSocketStream tempSocket = new MiSocketStream(socketCliente);
                            tempSocket.enviaMensaje("ERROR:Subasta cerrada");
                            tempSocket.close();
                            continue;
                        }
                    }

                    // Crear hilo para manejar el cliente
                    HiloClienteSubasta hiloCliente = new HiloClienteSubasta(
                        new MiSocketStream(socketCliente),
                        ipCliente
                    );
                    clientes.add(hiloCliente);

                    Thread thread = new Thread(hiloCliente);
                    thread.start();

                    // Notificar al cliente que la subasta ha iniciado
                    if (estadoActual == EstadoSubasta.ACTIVA) {
                        hiloCliente.notificarInicioSubasta(getTiempoRestante());
                    }

                    System.out.println("[INFO] Total participantes: " + clientes.size());

                } catch (SocketException e) {
                    if (estadoActual == EstadoSubasta.FINALIZADA) {
                        System.out.println("[INFO] Finalizando sesion...");
                    }
                }
            }

            // Esperar un poco para asegurar que todos los mensajes se enviaron
            Thread.sleep(2000);

            // Resetear para la siguiente sesión
            resetearEstado();

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Inicia una nueva sesión de subasta
     */
    private static void iniciarNuevaSubasta() {
        estadoActual = EstadoSubasta.ACTIVA;
        tiempoInicio = System.currentTimeMillis();

        System.out.println("\n*** SUBASTA #" + numeroSubasta + " INICIADA ***");
        System.out.println("Duracion: " + (TIEMPO_SUBASTA/1000) + " segundos");

        // Cancelar timers anteriores si existen
        if (timerSubasta != null) {
            timerSubasta.cancel();
        }
        if (timerBroadcast != null) {
            timerBroadcast.cancel();
        }

        // Iniciar temporizador de finalización
        timerSubasta = new Timer();
        timerSubasta.schedule(new TimerTask() {
            @Override
            public void run() {
                finalizarSubasta();
            }
        }, TIEMPO_SUBASTA);

        // Iniciar broadcast periódico
        iniciarBroadcastPeriodico();
    }

    /**
     * Inicia un temporizador que envía la oferta ganadora cada 5 segundos
     */
    private static void iniciarBroadcastPeriodico() {
        timerBroadcast = new Timer();
        timerBroadcast.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (estadoActual != EstadoSubasta.ACTIVA) {
                    timerBroadcast.cancel();
                    return;
                }
                enviarActualizacionPeriodica();
            }
        }, 5000, 5000);

        System.out.println("[BROADCAST] Actualizaciones cada 5 segundos");
    }

    /**
     * Envía la oferta ganadora actual a todos los clientes conectados
     */
    private static void enviarActualizacionPeriodica() {
        if (clientes.isEmpty()) {
            return;
        }

        String update = obtenerPropuestaMasAlta() + ":TIEMPO:" + getTiempoRestante();
        System.out.println("[BROADCAST] Propuesta mas alta: $" + propuestaMasAlta +
                         " (" + clientes.size() + " clientes)");

        for (HiloClienteSubasta cliente : clientes) {
            try {
                cliente.enviarActualizacion(update);
            } catch (Exception e) {
                System.out.println("[ERROR] Al enviar actualizacion a " +
                                 cliente.getIpCliente());
            }
        }
    }

    /**
     * Finaliza la subasta, determina el ganador y notifica a todos los clientes
     */
    private static void finalizarSubasta() {
        estadoActual = EstadoSubasta.FINALIZADA;

        System.out.println("\n╔═══════════════════════════════════════════╗");
        System.out.println("║     SUBASTA #" + numeroSubasta + " FINALIZADA            ║");
        System.out.println("╚═══════════════════════════════════════════╝");

        if (clientes.isEmpty()) {
            System.out.println("[INFO] No hubo participantes");
            return;
        }

        // Determinar el ganador (propuesta más alta)
        HiloClienteSubasta ganador = null;
        double propuestaGanadora = -1;

        System.out.println("\nPropuestas recibidas:");
        for (HiloClienteSubasta cliente : clientes) {
            double propuesta = cliente.getPropuesta();
            String ip = cliente.getIpCliente();
            System.out.println("  - " + ip + ": $" + propuesta);

            if (propuesta > propuestaGanadora) {
                propuestaGanadora = propuesta;
                ganador = cliente;
            }
        }

        if (ganador != null) {
            System.out.println("\n*** GANADOR: " + ganador.getIpCliente() +
                             " con $" + propuestaGanadora + " ***");

            // Notificar a todos los clientes
            String mensaje = "GANADOR:" + ganador.getIpCliente() +
                           ":MONTO:" + propuestaGanadora;
            notificarTodosClientes(mensaje);
        }

        // Cerrar todas las conexiones
        for (HiloClienteSubasta cliente : clientes) {
            cliente.cerrarConexion();
        }

        System.out.println("[INFO] Conexiones cerradas");
    }

    /**
     * Envía un mensaje a todos los clientes conectados
     */
    private static void notificarTodosClientes(String mensaje) {
        for (HiloClienteSubasta cliente : clientes) {
            try {
                cliente.enviarResultado(mensaje);
            } catch (Exception e) {
                System.out.println("[ERROR] Notificando a " + cliente.getIpCliente());
            }
        }
    }

    /**
     * Resetea el estado del servidor para la siguiente subasta
     */
    private static void resetearEstado() {
        System.out.println("\n[RESET] Preparando para nueva sesion...");

        // Cancelar timers
        if (timerSubasta != null) {
            timerSubasta.cancel();
            timerSubasta = null;
        }
        if (timerBroadcast != null) {
            timerBroadcast.cancel();
            timerBroadcast = null;
        }

        // Limpiar clientes y propuestas
        clientes.clear();

        synchronized(lock) {
            propuestaMasAlta = 0.0;
            ipPropuestaMasAlta = "ninguno";
        }

        // Volver a estado de espera
        estadoActual = EstadoSubasta.ESPERANDO;

        System.out.println("[LISTO] Servidor esperando nueva sesion\n");
    }

    /**
     * Actualiza la propuesta más alta si la nueva propuesta es mayor
     * @return true si se actualizó, false si no
     */
    public static boolean actualizarPropuestaMasAlta(double nuevaPropuesta, String ip) {
        synchronized(lock) {
            if (nuevaPropuesta > propuestaMasAlta) {
                propuestaMasAlta = nuevaPropuesta;
                ipPropuestaMasAlta = ip;
                System.out.println("[NUEVA ALTA] $" + nuevaPropuesta + " de " + ip);
                return true;
            }
            return false;
        }
    }

    /**
     * Obtiene la información de la propuesta más alta actual
     */
    public static String obtenerPropuestaMasAlta() {
        synchronized(lock) {
            if (propuestaMasAlta == 0.0) {
                return "PROPUESTA_ALTA:ninguno:0.0";
            }
            return "PROPUESTA_ALTA:" + ipPropuestaMasAlta + ":" + propuestaMasAlta;
        }
    }

    /**
     * Verifica si la subasta sigue activa
     */
    public static boolean estaActiva() {
        return estadoActual == EstadoSubasta.ACTIVA;
    }

    /**
     * Obtiene el tiempo restante de la subasta en segundos
     */
    public static long getTiempoRestante() {
        if (estadoActual != EstadoSubasta.ACTIVA) {
            return 0;
        }
        long transcurrido = System.currentTimeMillis() - tiempoInicio;
        long restante = (TIEMPO_SUBASTA - transcurrido) / 1000;
        return Math.max(0, restante);
    }
}
