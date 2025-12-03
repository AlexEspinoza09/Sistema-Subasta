package socket.conconexion.servidor;

import java.io.Serializable;

/**
 * Representa un nodo (servidor) en el sistema distribuido de subastas.
 * Cada nodo tiene un ID único y puede ser coordinador o participante.
 * @author Sistema de Subasta Distribuido
 */
public class NodoSubasta implements Serializable, Comparable<NodoSubasta> {
    private static final long serialVersionUID = 1L;

    private int id;                    // ID único del nodo (mayor ID = mayor prioridad)
    private String host;               // Dirección IP o hostname
    private int puertoClientes;        // Puerto para clientes de subasta
    private int puertoCoordinacion;    // Puerto para comunicación entre servidores
    private boolean activo;            // Si el nodo está activo
    private long ultimoLatido;         // Timestamp del último heartbeat

    /**
     * Constructor completo
     */
    public NodoSubasta(int id, String host, int puertoClientes, int puertoCoordinacion) {
        this.id = id;
        this.host = host;
        this.puertoClientes = puertoClientes;
        this.puertoCoordinacion = puertoCoordinacion;
        this.activo = true;
        this.ultimoLatido = System.currentTimeMillis();
    }

    /**
     * Actualiza el timestamp del último heartbeat
     */
    public void actualizarLatido() {
        this.ultimoLatido = System.currentTimeMillis();
    }

    /**
     * Verifica si el nodo está activo (heartbeat reciente)
     * @param timeout Tiempo máximo sin heartbeat (ms)
     */
    public boolean estaVivo(long timeout) {
        long ahora = System.currentTimeMillis();
        return activo && (ahora - ultimoLatido) < timeout;
    }

    // Getters y Setters
    public int getId() {
        return id;
    }

    public String getHost() {
        return host;
    }

    public int getPuertoClientes() {
        return puertoClientes;
    }

    public int getPuertoCoordinacion() {
        return puertoCoordinacion;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public long getUltimoLatido() {
        return ultimoLatido;
    }

    /**
     * Compara nodos por ID (para ordenamiento)
     */
    @Override
    public int compareTo(NodoSubasta otro) {
        return Integer.compare(this.id, otro.id);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        NodoSubasta nodo = (NodoSubasta) obj;
        return id == nodo.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return String.format("Nodo{id=%d, host=%s, puertoClientes=%d, puertoCoord=%d, activo=%s}",
                           id, host, puertoClientes, puertoCoordinacion, activo);
    }
}
