package socket.conconexion.servidor;

import java.io.Serializable;

/**
 * Clase que representa los mensajes del protocolo Bully
 * para elección de coordinador en sistemas distribuidos.
 * @author Sistema de Subasta Distribuido
 */
public class MensajeBully implements Serializable {
    private static final long serialVersionUID = 1L;

    // Tipos de mensajes del protocolo Bully
    public enum TipoMensaje {
        ELECTION,      // Inicio de elección
        OK,            // Respuesta a ELECTION (estoy vivo y tengo mayor ID)
        COORDINATOR,   // Anuncio del nuevo coordinador
        HEARTBEAT,     // Latido del coordinador para verificar que está vivo
        PING,          // Solicitud de estado
        ESTADO,        // Respuesta con información del nodo
        REDIRECCION    // Redirigir cliente al coordinador
    }

    private TipoMensaje tipo;
    private int idEmisor;           // ID del nodo que envía el mensaje
    private int idCoordinador;      // ID del coordinador (para COORDINATOR)
    private String hostCoordinador; // Host del coordinador (para redirección)
    private int puertoCoordinador;  // Puerto del coordinador (para redirección)
    private String datos;           // Datos adicionales
    private long timestamp;         // Timestamp del mensaje

    /**
     * Constructor para mensajes básicos
     */
    public MensajeBully(TipoMensaje tipo, int idEmisor) {
        this.tipo = tipo;
        this.idEmisor = idEmisor;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Constructor para mensaje COORDINATOR
     */
    public MensajeBully(TipoMensaje tipo, int idEmisor, int idCoordinador,
                       String host, int puerto) {
        this(tipo, idEmisor);
        this.idCoordinador = idCoordinador;
        this.hostCoordinador = host;
        this.puertoCoordinador = puerto;
    }

    /**
     * Constructor completo
     */
    public MensajeBully(TipoMensaje tipo, int idEmisor, String datos) {
        this(tipo, idEmisor);
        this.datos = datos;
    }

    // Getters y Setters
    public TipoMensaje getTipo() {
        return tipo;
    }

    public int getIdEmisor() {
        return idEmisor;
    }

    public int getIdCoordinador() {
        return idCoordinador;
    }

    public void setIdCoordinador(int idCoordinador) {
        this.idCoordinador = idCoordinador;
    }

    public String getHostCoordinador() {
        return hostCoordinador;
    }

    public void setHostCoordinador(String hostCoordinador) {
        this.hostCoordinador = hostCoordinador;
    }

    public int getPuertoCoordinador() {
        return puertoCoordinador;
    }

    public void setPuertoCoordinador(int puertoCoordinador) {
        this.puertoCoordinador = puertoCoordinador;
    }

    public String getDatos() {
        return datos;
    }

    public void setDatos(String datos) {
        this.datos = datos;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("MensajeBully{tipo=%s, idEmisor=%d, idCoord=%d, host=%s, puerto=%d}",
                           tipo, idEmisor, idCoordinador, hostCoordinador, puertoCoordinador);
    }
}
