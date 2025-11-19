package socket.conconexion.servidor;

import java.net.*;

/**
 * Este módulo contiene la lógica de aplicación de un servidor Echo
 * que utiliza un socket en modo stream para comunicación entre procesos.
 * A diferencia de ServidorEcho2, los clientes se sirven concurrentemente.
 * Se requiere un argumento de línea de mandato para el puerto del servidor.
 * @author M. L. Liu
 */
public class ServidorEcho3 {
	public static void main(String[] args) {
		int puertoServidor = 7; // Puerto por defecto

		if (args.length == 1 )
			puertoServidor = Integer.parseInt(args[0]);
		try {
			// instancia un socket stream para aceptar
			// las conexiones
			ServerSocket miSocketConexion = new ServerSocket(puertoServidor);
			/**/  System.out.println("Servidor Echo listo.");
			while (true) { // bucle infinito
				// espera para aceptar una conexión
				/**/    System.out.println("Espera una conexión.");
				MiSocketStream miSocketDatos = new MiSocketStream(miSocketConexion.accept( ));
				/**/    System.out.println("conexión aceptada");
				// Arranca un hilo para manejar la sesión de cliente
				Thread elHilo = new Thread(new HiloServidorEcho(miSocketDatos));
				elHilo.start();
				// y continua con el siguiente cliente
			} // fin de while infinito
		} // fin de try
		catch (Exception ex) {
			ex.printStackTrace( );
		} // fin de catch
	} // fin de main
} // fin de class