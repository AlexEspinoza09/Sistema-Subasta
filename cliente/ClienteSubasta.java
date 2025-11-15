package socket.conconexion.cliente;

import java.io.*;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Cliente de Subasta - Lógica de presentación
 * Permite al usuario participar en una subasta con múltiples propuestas cada 10 segundos.
 * @author Sistema de Subasta
 */
public class ClienteSubasta {
    private static final int INTERVALO_PROPUESTA = 10; // segundos entre propuestas
    private static volatile boolean puedeOfertar = true;
    private static Timer temporizador;

    public static void main(String[] args) {
        InputStreamReader is = new InputStreamReader(System.in);
        BufferedReader br = new BufferedReader(is);
        ClienteSubastaAuxiliar auxiliar = null;

        try {
            imprimirBanner();

            // Solicitar servidor
            System.out.print("Nombre o IP del servidor de subasta: ");
            String nombreMaquina = br.readLine();
            if (nombreMaquina.length() == 0)
                nombreMaquina = "localhost";

            // Solicitar puerto
            System.out.print("Puerto del servidor (default 8080): ");
            String numPuerto = br.readLine();
            if (numPuerto.length() == 0)
                numPuerto = "8080";

            System.out.println("-------------------------------------------");
            System.out.println("Conectando al servidor de subasta...");
            System.out.println("-------------------------------------------");

            // Conectar al servidor
            auxiliar = new ClienteSubastaAuxiliar(nombreMaquina, numPuerto);

            // Solicitar primera propuesta
            double propuesta = solicitarPropuesta(br, true);
            if (propuesta <= 0) {
                System.out.println("Error: La propuesta debe ser un número positivo.");
                return;
            }

            // Enviar primera propuesta
            ClienteSubastaAuxiliar.EstadoSubasta estado = auxiliar.enviarPropuesta(propuesta);
            mostrarEstado(estado);

            // Loop de propuestas cada 10 segundos
            boolean continuar = true;
            while (continuar && estado.tiempoRestante > 0) {
                // Esperar 10 segundos antes de permitir otra propuesta
                puedeOfertar = false;
                iniciarCuentaRegresiva();

                // Solicitar nueva propuesta
                System.out.print(" Ingrese nueva propuesta (o 'x' para salir): $");
                String input = br.readLine();

                cancelarTemporizador();

                if (input.trim().equalsIgnoreCase("x")) {
                    System.out.println(" Saliendo de la subasta...");
                    continuar = false;
                    break;
                }

                try {
                    double nuevaPropuesta = Double.parseDouble(input.trim());
                    if (nuevaPropuesta <= 0) {
                        System.out.println(" La propuesta debe ser mayor que 0. Intente nuevamente.");
                        continue;
                    }

                    // Enviar nueva propuesta
                    estado = auxiliar.enviarPropuesta(nuevaPropuesta);
                    mostrarEstado(estado);

                } catch (NumberFormatException e) {
                    System.out.println(" Entrada inválida. Debe ingresar un número.");
                }
            }

            // Esperar resultado final
            if (continuar) {
                String resultado = auxiliar.esperarResultadoFinal();
                mostrarResultadoFinal(resultado);
            }

            auxiliar.cerrar();

        } catch (Exception ex) {
            System.out.println(" Error en el cliente de subasta:");
            ex.printStackTrace();
        } finally {
            cancelarTemporizador();
            if (auxiliar != null) {
                try {
                    auxiliar.cerrar();
                } catch (Exception e) {}
            }
        }
    }

    private static void imprimirBanner() {
        System.out.println("===========================================");
        System.out.println("   SISTEMA DE SUBASTA EN TIEMPO REAL");
        System.out.println("===========================================");
    }

    private static double solicitarPropuesta(BufferedReader br, boolean esPrimera)
            throws IOException {
        String mensaje = esPrimera ?
            " Ingrese su propuesta inicial (en dólares): $" :
            " Ingrese nueva propuesta: $";

        System.out.print(mensaje);
        String input = br.readLine();

        try {
            return Double.parseDouble(input.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void mostrarEstado(ClienteSubastaAuxiliar.EstadoSubasta estado) {
        System.out.println("\n-------------------------------------------");
        System.out.println("        ESTADO ACTUAL DE SUBASTA");
        System.out.println("-------------------------------------------");

        if (!estado.exito) {
            System.out.println(" " + estado.mensajeError);
            return;
        }

        System.out.println("   Propuesta más alta: $" + estado.montoPropuestaMasAlta);
        System.out.println("   IP líder: " + estado.ipPropuestaMasAlta);
        System.out.println("    Tiempo restante: " + estado.tiempoRestante + " segundos");

        if (estado.estoyGanando) {
            System.out.println("   ESTAS GANANDO LA SUBASTA!");
        } else {
            double diferencia = estado.montoPropuestaMasAlta - estado.montoPropuestaMasAlta;
            System.out.println("    Estas perdiendo. Ofrece mas de $" +
                             estado.montoPropuestaMasAlta);
        }
        System.out.println("-------------------------------------------");
    }

    private static void mostrarResultadoFinal(String resultado) {
        System.out.println("\n===========================================");
        System.out.println("       RESULTADO FINAL DE SUBASTA");
        System.out.println("===========================================");
        System.out.println(resultado);
        System.out.println("===========================================\n");
    }

    private static void iniciarCuentaRegresiva() {
        temporizador = new Timer();
        final int[] segundos = {INTERVALO_PROPUESTA};

        temporizador.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                segundos[0]--;
                if (segundos[0] > 0) {
                    System.out.print("\r Espera " + segundos[0] + " segundos para ofertar... ");
                } else {
                    System.out.print("\r Puedes hacer otra oferta!                    \n");
                    puedeOfertar = true;
                    temporizador.cancel();
                }
            }
        }, 1000, 1000);
    }

    private static void cancelarTemporizador() {
        if (temporizador != null) {
            temporizador.cancel();
            temporizador = null;
        }
    }
}
