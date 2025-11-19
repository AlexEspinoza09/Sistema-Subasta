package socket.conconexion.cliente;

import java.io.*;


/**
 * Este módulo contiene la lógica de presentación de un ClienteDaytime.
 * @author M. L. Liu
 */
public class ClienteDaytime2 {
	public static void main(String[] args) {
		InputStreamReader is = new InputStreamReader(System.in);
        	BufferedReader br = new BufferedReader(is);
        try {
        	System.out.println("Bienvenido al cliente Daytime.\n" +
        			           "¿Cual es el nombre de la maquina servidora?");
        	String nombreMaquina = br.readLine( );
        	if (nombreMaquina.length() == 0) // si el usuario no introduce un nombre
        		nombreMaquina = "localhost"; // usa el nombre de maquina por defecto
        	System.out.println("¿Cual es el n° de puerto de la maquina servidora?");
        	String numPuerto = br.readLine();
        	if (numPuerto.length () == 0)
        		numPuerto = "13"; // numero de puerto por defecto
        	System.out.println("Aquí esta la marca de tiempo recibida del servidor: "
        						+ ClienteDaytimeAuxiliar2.obtenerMarcaTiempo(nombreMaquina,numPuerto));
        } // fin de try
        catch (Exception ex) {
        	ex.printStackTrace( );
        } // fin de catch
	} // fin de main
} // fin de class
