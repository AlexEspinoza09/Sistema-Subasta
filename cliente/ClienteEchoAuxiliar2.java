package socket.conconexion.cliente;

import java.net.*;
import java.io.*;
import socket.conconexion.servidor.MiSocketStream;

/**
 * Esta clase es un módulo que proporciona la lógica de aplicación
 * para un cliente Echo utilizando un socket en modo stream.
 * @author M. L. Liu
 */

public class ClienteEchoAuxiliar2 {

	static final String mensajeFin = ".";
    private MiSocketStream miSocket;
    private InetAddress maquinaServidora;
    private int puertoServidor;

    ClienteEchoAuxiliar2(String nombreMaquina, String numPuerto) 
    		throws SocketException, UnknownHostException, IOException {

    	this.maquinaServidora = InetAddress.getByName(nombreMaquina);
    	this.puertoServidor = Integer.parseInt(numPuerto);
    	// instancia un socket en modo stream y espera por una conexión.
    	this.miSocket = new MiSocketStream(nombreMaquina, this.puertoServidor);
    	/**/System.out.println("Petición de conexión hecha");
    } // fin de constructor

    public String obtenerEco( String mensaje) throws SocketException, IOException {
    	String eco = "";
    	miSocket.enviaMensaje( mensaje);
    	// ahora recibe el eco 
    	eco = miSocket.recibeMensaje();
    	return eco;
    } //fin de obtenerEco

    public void hecho( ) throws SocketException, IOException{
    	miSocket.enviaMensaje(mensajeFin);
    	miSocket.close( );
    } // fin de hecho
} // fin de class