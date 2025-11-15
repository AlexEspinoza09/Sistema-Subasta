package socket.conconexion.cliente;

import socket.conconexion.servidor.MiSocketStream;
import java.net.*;
import java.io.*;

/**
 * Cliente de Subasta - Lógica de aplicación
 * Maneja la comunicación con el servidor de subasta permitiendo múltiples propuestas.
 * @author Sistema de Subasta
 */
public class ClienteSubastaAuxiliar {
    private MiSocketStream miSocket;
    private InetAddress maquinaServidora;
    private int puertoServidor;
    private double miUltimaPropuesta = 0.0;

    /**
     * Constructor que establece la conexión con el servidor
     */
    public ClienteSubastaAuxiliar(String nombreMaquina, String numPuerto)
            throws SocketException, UnknownHostException, IOException {

        this.maquinaServidora = InetAddress.getByName(nombreMaquina);
        this.puertoServidor = Integer.parseInt(numPuerto);

        // Conectar al servidor
        this.miSocket = new MiSocketStream(nombreMaquina, this.puertoServidor);
        System.out.println("\nConectado al servidor de subasta: " +
                         nombreMaquina + ":" + puertoServidor);
    }

    /**
     * Envía una propuesta y recibe información actualizada del estado de la subasta
     * @param propuesta Monto ofrecido en la subasta
     * @return Información del estado actual de la subasta
     */
    public EstadoSubasta enviarPropuesta(double propuesta)
            throws SocketException, IOException {

        miUltimaPropuesta = propuesta;

        // Enviar propuesta al servidor
        miSocket.enviaMensaje(String.valueOf(propuesta));

        // Recibir respuesta del servidor
        String respuesta = miSocket.recibeMensaje();

        // Parsear respuesta: "PROPUESTA_ALTA:ip:monto:TIEMPO:segundos:TU_PROPUESTA:estado"
        return parsearEstado(respuesta);
    }

    /**
     * Espera y recibe el resultado final de la subasta
     */
    public String esperarResultadoFinal() throws SocketException, IOException {
        System.out.println("\nEsperando resultado final de la subasta...");
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

            // Formato: PROPUESTA_ALTA:ip:monto:TIEMPO:segundos:TU_PROPUESTA:estado
            String[] partes = respuesta.split(":");

            String ipMasAlta = partes[1];
            double montoMasAlto = Double.parseDouble(partes[2]);
            long tiempoRestante = Long.parseLong(partes[4]);
            boolean estoyGanando = partes[6].equals("GANANDO");

            return new EstadoSubasta(true, "", ipMasAlta, montoMasAlto,
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
            // Formato: "GANADOR:IP:MONTO:cantidad"
            String[] partes = resultado.split(":");
            if (partes.length >= 4) {
                String ipGanador = partes[1];
                double montoGanador = Double.parseDouble(partes[3]);

                StringBuilder sb = new StringBuilder();
                sb.append("\n  Ganador: ").append(ipGanador).append("\n");
                sb.append("  Monto ganador: $").append(montoGanador).append("\n");
                sb.append("  Tu última propuesta: $").append(miUltimaPropuesta).append("\n\n");

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
     * Cierra la conexión con el servidor
     */
    public void cerrar() throws SocketException, IOException {
        miSocket.close();
        System.out.println("\nConexion cerrada con el servidor.");
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
