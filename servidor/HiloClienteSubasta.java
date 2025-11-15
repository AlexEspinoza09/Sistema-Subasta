package socket.conconexion.servidor;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

/**
 * Hilo que maneja la conexión con un cliente de subasta individual.
 * Permite múltiples propuestas del mismo cliente hasta que la subasta finalice.
 * @author Sistema de Subasta
 */
public class HiloClienteSubasta implements Runnable {
    private MiSocketStream miSocket;
    private String ipCliente;
    private double propuesta;
    private volatile boolean resultadoEnviado = false;
    private CountDownLatch latch = new CountDownLatch(1);

    public HiloClienteSubasta(MiSocketStream socket, String ip) {
        this.miSocket = socket;
        this.ipCliente = ip;
        this.propuesta = 0.0;
    }

    @Override
    public void run() {
        try {
            // Loop para recibir múltiples propuestas del mismo cliente
            while (ServidorSubasta.estaActiva()) {
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

                    // Actualizar la propuesta del cliente
                    propuesta = nuevaPropuesta;
                    System.out.println("Cliente " + ipCliente + " ofrece: $" + propuesta);

                    // Actualizar la propuesta más alta del servidor
                    boolean esLaMasAlta = ServidorSubasta.actualizarPropuestaMasAlta(
                        nuevaPropuesta, ipCliente);

                    // Enviar respuesta con la propuesta más alta actual y tiempo restante
                    String respuesta = ServidorSubasta.obtenerPropuestaMasAlta() +
                                     ":TIEMPO:" + ServidorSubasta.getTiempoRestante() +
                                     ":TU_PROPUESTA:" + (esLaMasAlta ? "GANANDO" : "PERDIENDO");

                    miSocket.enviaMensaje(respuesta);

                } catch (NumberFormatException e) {
                    System.out.println("Error: Propuesta inválida de " + ipCliente);
                    miSocket.enviaMensaje("ERROR:Propuesta inválida. Debe ser un número.");
                }
            }

            // Esperar el resultado final de la subasta
            System.out.println("Cliente " + ipCliente + " esperando resultado final...");
            latch.await(); // Espera hasta que se llame enviarResultado()

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
                latch.countDown(); // Libera el await()
                System.out.println("Resultado enviado a " + ipCliente);
            }
        } catch (IOException e) {
            System.out.println("Error al enviar resultado a " + ipCliente + ": " + e.getMessage());
        }
    }

    /**
     * Cierra la conexión con el cliente
     */
    public void cerrarConexion() {
        try {
            if (!resultadoEnviado) {
                latch.countDown(); // Libera el await() si aún no se envió resultado
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
}
