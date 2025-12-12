package socket.conconexion.cliente;

import socket.conconexion.servidor.MiSocketStream;
import java.net.*;
import java.io.*;

/**
 * Cliente de Subasta - Lógica de aplicación
 * Maneja la comunicación con el servidor de subasta permitiendo multiples propuestas.
 * @author Sistema de Subasta
 */
public class ClienteSubastaAuxiliar {
    private MiSocketStream miSocket;
    private InetAddress maquinaServidora;
    private int puertoServidor;
    private double miUltimaPropuesta = 0.0;
    private Thread hiloEscucha;
    private volatile boolean escuchando = true;
    private volatile boolean subastaActiva = true;
    private volatile String ultimaActualizacion = "";
    private volatile String ultimaRespuestaPropuesta = null;
    private final Object lockRespuesta = new Object();
    private String hostActual;
    private int puertoActual;
    private static final int MAX_REINTENTOS_RECONEXION = 3;

    /**
     * Constructor que establece la conexión con el servidor
     */
    public ClienteSubastaAuxiliar(String nombreMaquina, String numPuerto)
            throws SocketException, UnknownHostException, IOException {

        this.maquinaServidora = InetAddress.getByName(nombreMaquina);
        this.puertoServidor = Integer.parseInt(numPuerto);
        this.hostActual = nombreMaquina;
        this.puertoActual = this.puertoServidor;

        // Conectar al servidor (con manejo de redirección)
        conectarConRedireccion(this.hostActual, this.puertoActual);

        // Iniciar hilo de escucha para recibir actualizaciones periódicas
        iniciarHiloEscucha();
    }

    /**
     * Conecta al servidor y maneja redirecciones automáticamente
     */
    private void conectarConRedireccion(String host, int puerto)
            throws SocketException, UnknownHostException, IOException {

        int intentos = 0;
        final int MAX_REDIRECCIONES = 5;

        while (intentos < MAX_REDIRECCIONES) {
            try {
                this.miSocket = new MiSocketStream(host, puerto);
                System.out.println("\nConectado al servidor: " + host + ":" + puerto);

                // Leer primer mensaje para verificar si es redirección
                this.miSocket.getSocket().setSoTimeout(3000);
                String primerMensaje = this.miSocket.recibeMensaje();

                if (primerMensaje != null && primerMensaje.startsWith("REDIRECCION:")) {
                    // Servidor nos redirige al coordinador
                    System.out.println("[INFO] Servidor redirigiendo al coordinador...");

                    String[] partes = primerMensaje.split(":");
                    if (partes.length >= 3) {
                        String nuevoHost = partes[1];
                        int nuevoPuerto = Integer.parseInt(partes[2]);

                        System.out.println("[REDIRECCION] Conectando a coordinador: " +
                                         nuevoHost + ":" + nuevoPuerto);

                        // Cerrar conexión actual
                        this.miSocket.close();

                        // Actualizar destino
                        host = nuevoHost;
                        puerto = nuevoPuerto;
                        intentos++;
                        continue;
                    } else {
                        throw new IOException("Formato de redirección inválido: " + primerMensaje);
                    }
                } else if (primerMensaje != null && primerMensaje.startsWith("ERROR:")) {
                    throw new IOException(primerMensaje.substring(6));
                } else {
                    // No es redirección, preparar para recibir mensajes normales
                    // Poner el mensaje de vuelta en la cola si es necesario
                    // (en este caso, el hilo de escucha lo manejará)
                    this.miSocket.getSocket().setSoTimeout(0);
                    this.hostActual = host;
                    this.puertoActual = puerto;
                    System.out.println("[CONECTADO] Servidor de subasta activo");
                    return;
                }

            } catch (SocketTimeoutException e) {
                // No hubo redirección, conexión exitosa
                this.miSocket.getSocket().setSoTimeout(0);
                this.hostActual = host;
                this.puertoActual = puerto;
                System.out.println("[CONECTADO] Servidor de subasta activo");
                return;
            }
        }

        throw new IOException("Máximo de redirecciones alcanzado (" + MAX_REDIRECCIONES + ")");
    }

    /**
     * Intenta reconectar al sistema después de perder conexión
     * @return true si la reconexión fue exitosa
     */
    private boolean intentarReconectar() {
        System.out.println("\n[RECONEXION] Intentando reconectar al sistema...");

        for (int intento = 1; intento <= MAX_REINTENTOS_RECONEXION; intento++) {
            try {
                System.out.println("[RECONEXION] Intento " + intento + "/" + MAX_REINTENTOS_RECONEXION);

                // Esperar un momento antes de reintentar
                Thread.sleep(2000 * intento); // Backoff exponencial

                // Intentar reconectar al último servidor conocido
                // Puede ser redirigido automáticamente al nuevo coordinador
                conectarConRedireccion(hostActual, puertoActual);

                System.out.println("[RECONEXION] Reconexión exitosa!");

                // Reenviar última propuesta si existe
                if (miUltimaPropuesta > 0) {
                    System.out.println("[RECONEXION] Reenviando última propuesta: $" + miUltimaPropuesta);
                    try {
                        miSocket.enviaMensaje(String.valueOf(miUltimaPropuesta));
                    } catch (IOException e) {
                        System.out.println("[RECONEXION] Error al reenviar propuesta: " + e.getMessage());
                    }
                }

                // Reiniciar hilo de escucha
                iniciarHiloEscucha();

                return true;

            } catch (Exception e) {
                System.out.println("[RECONEXION] Fallo intento " + intento + ": " + e.getMessage());
                if (intento < MAX_REINTENTOS_RECONEXION) {
                    System.out.println("[RECONEXION] Reintentando...");
                }
            }
        }

        System.out.println("[RECONEXION] No se pudo reconectar después de " +
                         MAX_REINTENTOS_RECONEXION + " intentos");
        return false;
    }

    /**
     * Envía una propuesta y recibe información actualizada del estado de la subasta
     * @param propuesta Monto ofrecido en la subasta
     * @return Información del estado actual de la subasta
     */
    public EstadoSubasta enviarPropuesta(double propuesta)
            throws SocketException, IOException {

        miUltimaPropuesta = propuesta;

        synchronized(lockRespuesta) {
            ultimaRespuestaPropuesta = null;

            // Enviar propuesta al servidor
            miSocket.enviaMensaje(String.valueOf(propuesta));

            // Esperar respuesta del hilo de escucha (maximo 10 segundos)
            try {
                lockRespuesta.wait(10000);
            } catch (InterruptedException e) {
                return new EstadoSubasta(false, "Timeout esperando respuesta", "", 0.0, 0, false);
            }

            if (ultimaRespuestaPropuesta == null) {
                return new EstadoSubasta(false, "No se recibio respuesta del servidor", "", 0.0, 0, false);
            }

            // Parsear respuesta
            return parsearEstado(ultimaRespuestaPropuesta);
        }
    }

    /**
     * Espera y recibe el resultado final de la subasta
     */
    public String esperarResultadoFinal() throws SocketException, IOException {
        System.out.println("\nEsperando resultado final de la subasta...");

        // Esperar a que el hilo de escucha reciba el resultado final
        try {
            if (hiloEscucha != null) {
                hiloEscucha.join(30000); // Esperar maximo 30 segundos
            }
        } catch (InterruptedException e) {
            System.out.println("Espera interrumpida: " + e.getMessage());
        }

        // Si el hilo de escucha ya recibió el resultado, usarlo
        if (ultimaActualizacion != null && ultimaActualizacion.startsWith("GANADOR:")) {
            return formatearResultadoFinal(ultimaActualizacion);
        }

        // Si no, intentar leer directamente
        String resultado = miSocket.recibeMensaje();
        return formatearResultadoFinal(resultado);
    }

    /**
     * Parsea la respuesta del servidor sobre el estado actual
     */
    private EstadoSubasta parsearEstado(String respuesta) {
        try {
            if (respuesta.startsWith("ERROR")) {
                return new EstadoSubasta(false, respuesta, "", 0.0, 0, false);
            }

            // Remover prefijo RESPUESTA: si existe
            if (respuesta.startsWith("RESPUESTA:")) {
                respuesta = respuesta.substring(10); // Remover "RESPUESTA:"
            }

            // Formato: PROPUESTA_ALTA:ip:port:monto:TIEMPO:segundos:TU_PROPUESTA:estado
            String[] partes = respuesta.split(":");

            // Cliente ID ahora incluye IP:Puerto
            String clienteId = partes[1] + ":" + partes[2];
            double montoMasAlto = Double.parseDouble(partes[3]);
            long tiempoRestante = Long.parseLong(partes[5]);
            boolean estoyGanando = partes[7].equals("GANANDO");

            return new EstadoSubasta(true, "", clienteId, montoMasAlto,
                                    tiempoRestante, estoyGanando);

        } catch (Exception e) {
            return new EstadoSubasta(false, "Error al parsear respuesta: " + respuesta,
                                    "", 0.0, 0, false);
        }
    }

    /**
     * Formatea el resultado final de la subasta
     */
    private String formatearResultadoFinal(String resultado) {
        try {
            // Formato: "GANADOR:IP:PORT:MONTO:cantidad"
            String[] partes = resultado.split(":");
            if (partes.length >= 5) {
                String clienteIdGanador = partes[1] + ":" + partes[2];
                double montoGanador = Double.parseDouble(partes[4]);

                StringBuilder sb = new StringBuilder();
                sb.append("\n  Ganador: ").append(clienteIdGanador).append("\n");
                sb.append("  Monto ganador: $").append(montoGanador).append("\n");
                sb.append("  Tu ultima propuesta: $").append(miUltimaPropuesta).append("\n\n");

                // Determinar si ganó o perdió
                if (miUltimaPropuesta == montoGanador) {
                    sb.append("  *** FELICIDADES! HAS GANADO LA SUBASTA! ***");
                } else {
                    double diferencia = montoGanador - miUltimaPropuesta;
                    sb.append("  Lo siento, no ganaste esta vez.");
                    sb.append("\n  Te faltaron $").append(diferencia);
                    sb.append(" para ganar.");
                }

                return sb.toString();
            }
        } catch (Exception e) {
            System.out.println("Error al parsear resultado: " + e.getMessage());
        }

        return "Resultado: " + resultado;
    }

    /**
     * Verifica si la subasta sigue activa
     */
    public boolean estaSubastaActiva() {
        return subastaActiva;
    }

    /**
     * Cierra la conexión con el servidor
     */
    public void cerrar() throws SocketException, IOException {
        escuchando = false;
        subastaActiva = false;
        if (hiloEscucha != null) {
            hiloEscucha.interrupt();
        }
        miSocket.close();
        System.out.println("\nConexion cerrada con el servidor.");
    }

    /**
     * Inicia un hilo para escuchar actualizaciones periódicas del servidor
     */
    private void iniciarHiloEscucha() {
        hiloEscucha = new Thread(() -> {
            try {
                while (escuchando) {
                    String mensaje = miSocket.recibeMensaje();

                    if (mensaje == null) {
                        System.out.println("\n[DESCONEXION] Conexión cerrada por el servidor");

                        // Intentar reconectar automáticamente
                        if (subastaActiva && escuchando) {
                            if (!intentarReconectar()) {
                                // Si no se pudo reconectar, terminar
                                subastaActiva = false;
                                escuchando = false;
                                System.out.println("[ERROR] No se pudo restablecer la conexión. Cliente desconectado.");
                            }
                            // Si la reconexión fue exitosa, el nuevo hilo de escucha continuará
                            return;
                        }
                        break;
                    }

                    // Manejar diferentes tipos de mensajes
                    if (mensaje.startsWith("SUBASTA_INICIADA:")) {
                        // La subasta ha comenzado
                        procesarInicioSubasta(mensaje);
                    } else if (mensaje.startsWith("UPDATE:")) {
                        // Actualización periódica (cada 5 segundos)
                        procesarActualizacion(mensaje.substring(7));
                    } else if (mensaje.startsWith("GANADOR:")) {
                        // El resultado final
                        ultimaActualizacion = mensaje;
                        subastaActiva = false;
                        escuchando = false;
                        break;
                    } else if (mensaje.startsWith("RESPUESTA:") || mensaje.startsWith("ERROR:")) {
                        // Respuesta a una propuesta del cliente
                        synchronized(lockRespuesta) {
                            ultimaRespuestaPropuesta = mensaje;
                            lockRespuesta.notifyAll();
                        }
                    }
                }
            } catch (IOException e) {
                if (escuchando) {
                    System.out.println("\nError en hilo de escucha: " + e.getMessage());
                }
            }
        });
        hiloEscucha.setDaemon(false); // No daemon para que no se cierre prematuramente
        hiloEscucha.start();
    }

    /**
     * Procesa el mensaje de inicio de subasta
     */
    private void procesarInicioSubasta(String mensaje) {
        try {
            // Formato: SUBASTA_INICIADA:TIEMPO:segundos
            String[] partes = mensaje.split(":");
            if (partes.length >= 3) {
                long tiempoRestante = Long.parseLong(partes[2]);

                System.out.println("\n===========================================");
                System.out.println("     LA SUBASTA HA INICIADO!");
                System.out.println("===========================================");
                System.out.println("  Tiempo de subasta: " + tiempoRestante + " segundos");
                System.out.println("  Puedes hacer ofertas cada 10 segundos");
                System.out.println("===========================================\n");
            }
        } catch (Exception e) {
            System.out.println("Error al procesar inicio de subasta: " + e.getMessage());
        }
    }

    /**
     * Procesa y muestra una actualización periódica del servidor
     */
    private void procesarActualizacion(String update) {
        try {
            // Formato: PROPUESTA_ALTA:ip:port:monto:TIEMPO:segundos
            String[] partes = update.split(":");
            if (partes.length >= 6) {
                String clienteId = partes[1] + ":" + partes[2];
                double montoLider = Double.parseDouble(partes[3]);
                long tiempoRestante = Long.parseLong(partes[5]);

                System.out.println("\n[ACTUALIZACION DEL SERVIDOR]");
                System.out.println("  Oferta ganadora: $" + montoLider + " (Cliente: " + clienteId + ")");
                System.out.println("  Tiempo restante: " + tiempoRestante + " segundos");
                System.out.println("-------------------------------------------");
            }
        } catch (Exception e) {
            System.out.println("Error al procesar actualización: " + e.getMessage());
        }
    }

    /**
     * Clase interna para representar el estado de la subasta
     */
    public static class EstadoSubasta {
        public final boolean exito;
        public final String mensajeError;
        public final String ipPropuestaMasAlta;
        public final double montoPropuestaMasAlta;
        public final long tiempoRestante;
        public final boolean estoyGanando;

        public EstadoSubasta(boolean exito, String error, String ip, double monto,
                           long tiempo, boolean ganando) {
            this.exito = exito;
            this.mensajeError = error;
            this.ipPropuestaMasAlta = ip;
            this.montoPropuestaMasAlta = monto;
            this.tiempoRestante = tiempo;
            this.estoyGanando = ganando;
        }
    }
}
