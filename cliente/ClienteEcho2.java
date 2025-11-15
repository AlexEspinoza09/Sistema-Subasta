package socket.conconexion.cliente;

import java.io.*;

/**
 * Este módulo contiene la lógica de presentación de un cliente Echo.
 * @author M. L. Liu
 */
public class ClienteEcho2 {
	static final String mensajeFin = ".";
	public static void main(String[ ] args) {
		InputStreamReader is = new InputStreamReader(System.in);
		BufferedReader br = new BufferedReader(is);
		try {
			System.out.println("Bienvenido al cliente Echo.\n" +
							   "¿Cuál es el nombre de la máquina servidora?");
			String nombreMaquina = br.readLine( );
			if (nombreMaquina.length() == 0) // si el usuario no introdujo un nombre
				nombreMaquina = "localhost"; // utiliza nombre de máquina por defecto
			System.out.println("¿Cuál es el n° puerto de la máquina servidora?");
			String numPuerto = br.readLine();
			if (numPuerto.length() == 0)
				numPuerto = "7"; // n�mero de puerto por defecto
			ClienteEchoAuxiliar2 auxiliar = new ClienteEchoAuxiliar2(nombreMaquina, numPuerto);
			boolean hecho = false;
			String mensaje, eco;
			while (!hecho) {
				System.out.println("Introduzca una línea para recibir el eco "
									+ " del servidor, o un único punto para terminar.");
				mensaje = br.readLine( );
				if ((mensaje.trim( )).equals (".")){
					hecho = true;
					auxiliar.hecho( );
				}
				else {
					eco = auxiliar.obtenerEco(mensaje);
					System.out.println(eco);
				}
			} // fin de while
		} // fin de try
		catch (Exception ex) {
			ex.printStackTrace( );
		} // fin de catch
	} // fin de main
} // fin de class