package socket.conconexion.servidor;

/**
 * Este módulo esta diseñado para usarse con un servidor Echo concurrente.
 * Su método run lleva a cabo la lógica de una sesión de cliente.
 * @author M. L. Liu
 */
class HiloServidorEcho implements Runnable {
	static final String mensajeFin = ".";
    MiSocketStream miSocketDatos;

    HiloServidorEcho (MiSocketStream miSocketDatos) {
    	this.miSocketDatos = miSocketDatos;
    } // fin de constructor

    public void run( ) {
    	boolean hecho = false;
    	String mensaje;
    	try {
    		while (!hecho) {
    			mensaje = miSocketDatos.recibeMensaje( );
    			/**/    System.out.println("mensaje recibido: "+ mensaje);
    			if ((mensaje.trim()).equals (mensajeFin)){
    				// se termina la sesión; cierra el socket de datos
    				/**/      System.out.println("Final de la sesión.");
    				miSocketDatos.close( );
    				hecho = true;
    			} //fin de if
    			else {
    				// Ahora manda el eco al solicitante
    				miSocketDatos.enviaMensaje(mensaje);
    			} // fin de else
    		} // fin de while !hecho
    	} // fin de try
    	catch (Exception ex) {
    		System.out.println("Excepción capturada en hilo: " + ex);
    	} // fin de catch
    } // fin de run
} // fin de class