package socket.conconexion.servidor;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

/**
 * Hilo que maneja la conexión con un cliente de subasta individual
 * en el sistema distribuido con protocolo Bully.
 * Permite multiples propuestas del mismo cliente hasta que la subasta finalice.
 * @author Sistema de Subasta Distribuido
 */
public class HiloClienteSubastaBully implements Runnable {
    private MiSocketStream miSocket;
    private String ipCliente;
    private String clienteId; // ID único: IP:Puerto
    private double propuesta;
    private volatile boolean resultadoEnviado = false;
    private CountDownLatch latch = new CountDownLatch(1);
    private ServidorSubastaBully servidor;

    public HiloClienteSubastaBully(MiSocketStream socket, String ip, ServidorSubastaBully servidor) {
        this.miSocket = socket;
        this.ipCliente = ip;
        // Generar ID único usando IP:Puerto del socket remoto
        int puertoRemoto = socket.getSocket().getPort();
        this.clienteId = ip + ":" + puertoRemoto;
        this.propuesta = 0.0;
        this.servidor = servidor;
        System.out.println("[CLIENTE] ID único generado: " + this.clienteId);
    }

    @Override
    public void run() {
        try {
            // Loop para recibir multiples propuestas del mismo cliente
            while (servidor.estaActiva()) {
                String mensajeRecibido = miSocket.recibeMensaje();

                if (mensajeRecibido == null) {
                    System.out.println("Cliente " + ipCliente + " desconectado.");
                    break;
                }

                System.out.println("Mensaje de " + ipCliente + ": " + mensajeRecibido);

                // Comando para terminar conexión
                if (mensajeRecibido.trim().equals("FIN")) {
                    System.out.println("Cliente " + ipCliente + " se retiró de la subasta.");
                    break;
                }

                try {
                    double nuevaPropuesta = Double.parseDouble(mensajeRecibido.trim());

                    if (nuevaPropuesta <= 0) {
                        miSocket.enviaMensaje("ERROR:La propuesta debe ser mayor que 0");
                        continue;
                    }

                    // Verificar si el cliente ya tiene una oferta anterior
                    Double ofertaAnterior = servidor.obtenerOfertaCliente(clienteId);

                    // Actualizar la propuesta del cliente
                    System.out.println("Cliente " + clienteId + " ofrece: $" + nuevaPropuesta);

                    // Actualizar la propuesta mas alta del servidor
                    boolean esLaMasAlta = servidor.actualizarPropuestaMasAlta(
                        nuevaPropuesta, clienteId);

                    // Verificar si la oferta fue rechazada por ser menor o igual a la anterior
                    Double ofertaActual = servidor.obtenerOfertaCliente(clienteId);
                    if (ofertaAnterior != null && ofertaActual != null &&
                        ofertaActual.equals(ofertaAnterior) && nuevaPropuesta <= ofertaAnterior) {
                        // La oferta fue rechazada
                        miSocket.enviaMensaje("ERROR:Tu nueva oferta ($" + nuevaPropuesta +
                                            ") debe ser mayor que tu oferta anterior ($" + ofertaAnterior + ")");
                        continue;
                    }

                    // Actualizar propuesta local solo si fue aceptada
                    propuesta = nuevaPropuesta;

                    // Enviar respuesta con la propuesta mas alta actual y tiempo restante
                    String respuesta = "RESPUESTA:" + servidor.obtenerPropuestaMasAlta() +
                                     ":TIEMPO:" + servidor.getTiempoRestante() +
                                     ":TU_PROPUESTA:" + (esLaMasAlta ? "GANANDO" : "PERDIENDO");

                    miSocket.enviaMensaje(respuesta);

                } catch (NumberFormatException e) {
                    System.out.println("Error: Propuesta invalida de " + ipCliente);
                    miSocket.enviaMensaje("ERROR:Propuesta invalida. Debe ser un numero.");
                }
            }

            // Esperar el resultado final de la subasta
            System.out.println("Cliente " + ipCliente + " esperando resultado final...");
            latch.await();

        } catch (InterruptedException e) {
            System.out.println("Cliente " + ipCliente + " interrumpido.");
        } catch (IOException e) {
            System.out.println("Error de I/O con cliente " + ipCliente + ": " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error con cliente " + ipCliente + ": " + e.getMessage());
        }
    }

    /**
     * Envía el resultado final de la subasta al cliente
     */
    public void enviarResultado(String mensaje) {
        try {
            if (!resultadoEnviado) {
                miSocket.enviaMensaje(mensaje);
                resultadoEnviado = true;
                latch.countDown();
                System.out.println("Resultado enviado a " + ipCliente);
            }
        } catch (IOException e) {
            System.out.println("Error al enviar resultado a " + ipCliente + ": " + e.getMessage());
        }
    }

    /**
     * Envía una actualización periódica de la oferta ganadora al cliente
     */
    public void enviarActualizacion(String mensaje) {
        try {
            miSocket.enviaMensaje("UPDATE:" + mensaje);
        } catch (IOException e) {
            System.out.println("Error al enviar actualización a " + ipCliente + ": " + e.getMessage());
        }
    }

    /**
     * Notifica al cliente que la subasta ha iniciado
     */
    public void notificarInicioSubasta(long tiempoRestante) {
        try {
            miSocket.enviaMensaje("SUBASTA_INICIADA:TIEMPO:" + tiempoRestante);
            System.out.println("Cliente " + ipCliente + " notificado: subasta iniciada");
        } catch (IOException e) {
            System.out.println("Error al notificar inicio a " + ipCliente + ": " + e.getMessage());
        }
    }

    /**
     * Cierra la conexión con el cliente
     */
    public void cerrarConexion() {
        try {
            if (!resultadoEnviado) {
                latch.countDown();
            }
            miSocket.close();
            System.out.println("Conexión cerrada con " + ipCliente);
        } catch (IOException e) {
            System.out.println("Error al cerrar conexión con " + ipCliente);
        }
    }

    // Getters
    public double getPropuesta() {
        return propuesta;
    }

    public String getIpCliente() {
        return ipCliente;
    }

    public String getClienteId() {
        return clienteId;
    }
}
