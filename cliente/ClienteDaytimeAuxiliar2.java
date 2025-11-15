package socket.conconexion.cliente;

import socket.conconexion.servidor.MiSocketStream;

/**
 * Esta clase es un módulo que proporciona la lógica de aplicación
 * para un cliente Daytime que utiliza un socket en modo stream para IPC.
 * @author M. L. Liu
 */
public class ClienteDaytimeAuxiliar2 {

	public static String obtenerMarcaTiempo(String nombreMaquina,
										    String numPuerto) throws Exception {

		String marcaTiempo = "";

		int puertoServidor = Integer.parseInt(numPuerto);
		// instancia un socket en modo stream y espera a que se haga 
		// una conexión al puerto servidor
	    /**/System.out.println("Petición de conexión realizada");
	    MiSocketStream miSocket = new MiSocketStream(nombreMaquina, puertoServidor);
	    // ahora espera hasta recibir la marca de tiempo
	    marcaTiempo = miSocket.recibeMensaje();
	    miSocket.close( ); // implica la desconexión
	    return marcaTiempo;
    } // fin
} // fin de class