package socket.conconexion.servidor;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Servidor de Subasta que acepta múltiples clientes, recibe sus propuestas,
 * espera 30 segundos y notifica a todos los clientes sobre el ganador.
 * @author Sistema de Subasta
 */
public class ServidorSubasta {
    private static final int TIEMPO_SUBASTA = 120000; // Dos minutos en milisegundos
    private static List<HiloClienteSubasta> clientes = new CopyOnWriteArrayList<>();
    private static volatile boolean subastaActiva = true;
    private static long tiempoInicio;

    // Propuesta más alta actual (thread-safe)
    private static volatile double propuestaMasAlta = 0.0;
    private static volatile String ipPropuestaMasAlta = "ninguno";
    private static final Object lock = new Object();

    public static void main(String[] args) {
        int puertoServidor = 8080; // puerto por defecto para subasta

        if (args.length == 1)
            puertoServidor = Integer.parseInt(args[0]);

        try {
            ServerSocket miSocketConexion = new ServerSocket(puertoServidor);
            System.out.println("===========================================");
            System.out.println("Servidor de Subasta iniciado en puerto " + puertoServidor);
            System.out.println("===========================================");

            // Iniciar el temporizador de la subasta
            tiempoInicio = System.currentTimeMillis();
            iniciarTemporizadorSubasta();

            // Aceptar clientes durante el período de subasta
            while (subastaActiva) {
                try {
                    System.out.println("\nEsperando participantes de la subasta...");
                    Socket socketCliente = miSocketConexion.accept();

                    // Verificar si aún hay tiempo
                    long tiempoTranscurrido = System.currentTimeMillis() - tiempoInicio;
                    if (tiempoTranscurrido >= TIEMPO_SUBASTA) {
                        System.out.println("Subasta cerrada. No se aceptan más participantes.");
                        socketCliente.close();
                        break;
                    }

                    System.out.println("Nuevo participante conectado: " +
                                     socketCliente.getInetAddress().getHostAddress());

                    // Crear hilo para manejar el cliente
                    HiloClienteSubasta hiloCliente = new HiloClienteSubasta(
                        new MiSocketStream(socketCliente),
                        socketCliente.getInetAddress().getHostAddress()
                    );
                    clientes.add(hiloCliente);

                    Thread thread = new Thread(hiloCliente);
                    thread.start();

                    System.out.println("Total de participantes: " + clientes.size());

                } catch (SocketException e) {
                    if (!subastaActiva) {
                        System.out.println("Servidor cerrando conexiones...");
                    }
                }
            }

            miSocketConexion.close();
            System.out.println("\nServidor de subasta finalizado.");

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Inicia un temporizador que finaliza la subasta después de 30 segundos
     */
    private static void iniciarTemporizadorSubasta() {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                finalizarSubasta();
            }
        }, TIEMPO_SUBASTA);

        System.out.println("Temporizador iniciado: La subasta finalizará en " +
                         (TIEMPO_SUBASTA/1000) + " segundos");

        // Iniciar broadcast periódico cada 5 segundos
        iniciarBroadcastPeriodico();
    }

    /**
     * Inicia un temporizador que envía la oferta ganadora cada 5 segundos
     */
    private static void iniciarBroadcastPeriodico() {
        Timer broadcastTimer = new Timer();
        broadcastTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!subastaActiva) {
                    broadcastTimer.cancel();
                    return;
                }
                enviarActualizacionPeriodica();
            }
        }, 5000, 5000); // Primer broadcast a los 5 segundos, luego cada 5 segundos

        System.out.println("Broadcast periódico iniciado: Actualizaciones cada 5 segundos");
    }

    /**
     * Envía la oferta ganadora actual a todos los clientes conectados
     */
    private static void enviarActualizacionPeriodica() {
        if (clientes.isEmpty()) {
            return;
        }

        String update = obtenerPropuestaMasAlta() + ":TIEMPO:" + getTiempoRestante();
        System.out.println("[BROADCAST] Enviando actualización a " + clientes.size() +
                         " clientes: Propuesta más alta $" + propuestaMasAlta);

        for (HiloClienteSubasta cliente : clientes) {
            try {
                cliente.enviarActualizacion(update);
            } catch (Exception e) {
                System.out.println("Error al enviar actualización a " +
                                 cliente.getIpCliente() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Finaliza la subasta, determina el ganador y notifica a todos los clientes
     */
    private static void finalizarSubasta() {
        subastaActiva = false;

        System.out.println("\n===========================================");
        System.out.println("SUBASTA FINALIZADA");
        System.out.println("===========================================");

        if (clientes.isEmpty()) {
            System.out.println("No hubo participantes en la subasta.");
            return;
        }

        // Determinar el ganador (propuesta más alta)
        HiloClienteSubasta ganador = null;
        double propuestaGanadora = -1;

        System.out.println("\nPropuestas recibidas:");
        for (HiloClienteSubasta cliente : clientes) {
            double propuesta = cliente.getPropuesta();
            String ip = cliente.getIpCliente();
            System.out.println("  - Cliente " + ip + ": $" + propuesta);

            if (propuesta > propuestaGanadora) {
                propuestaGanadora = propuesta;
                ganador = cliente;
            }
        }

        if (ganador != null) {
            System.out.println("\n*** GANADOR: " + ganador.getIpCliente() +
                             " con $" + propuestaGanadora + " ***\n");

            // Notificar a todos los clientes
            String mensaje = "GANADOR:" + ganador.getIpCliente() +
                           ":MONTO:" + propuestaGanadora;
            notificarTodosClientes(mensaje);
        }

        // Cerrar todas las conexiones
        for (HiloClienteSubasta cliente : clientes) {
            cliente.cerrarConexion();
        }

        System.out.println("Todas las conexiones han sido cerradas.");
    }

    /**
     * Envía un mensaje a todos los clientes conectados
     */
    private static void notificarTodosClientes(String mensaje) {
        System.out.println("Notificando a " + clientes.size() + " participantes...");
        for (HiloClienteSubasta cliente : clientes) {
            try {
                cliente.enviarResultado(mensaje);
            } catch (Exception e) {
                System.out.println("Error al notificar cliente " +
                                 cliente.getIpCliente() + ": " + e.getMessage());
            }
        }
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
                System.out.println(" NUEVA PROPUESTA MAS ALTA: $" + nuevaPropuesta +
                                 " de " + ip + " ");
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
        return subastaActiva;
    }

    /**
     * Obtiene el tiempo restante de la subasta en segundos
     */
    public static long getTiempoRestante() {
        long transcurrido = System.currentTimeMillis() - tiempoInicio;
        long restante = (TIEMPO_SUBASTA - transcurrido) / 1000;
        return Math.max(0, restante);
    }
}
