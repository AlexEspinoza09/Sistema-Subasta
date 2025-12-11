package socket.conconexion.servidor;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Almacena el estado replicado de la subasta en todos los nodos.
 * Contiene todas las ofertas recibidas y calcula la oferta ganadora.
 * Esta clase es thread-safe y serializable para transmitir entre nodos.
 */
public class EstadoSubastaReplicado implements Serializable {
    private static final long serialVersionUID = 1L;

    // Mapa thread-safe de ofertas: IP del cliente -> monto ofertado
    private ConcurrentHashMap<String, Double> ofertas;

    // Timestamp de última actualización (para sincronización)
    private long timestampUltimaActualizacion;

    /**
     * Constructor
     */
    public EstadoSubastaReplicado() {
        this.ofertas = new ConcurrentHashMap<>();
        this.timestampUltimaActualizacion = System.currentTimeMillis();
    }

    /**
     * Agrega o actualiza una oferta
     */
    public synchronized void agregarOferta(String ip, double monto) {
        ofertas.put(ip, monto);
        timestampUltimaActualizacion = System.currentTimeMillis();

        System.out.println("[ESTADO] Oferta registrada: " + ip + " -> $" + monto);
        System.out.println("[ESTADO] Total de ofertas: " + ofertas.size());
    }

    /**
     * Obtiene la oferta de un cliente específico
     */
    public Double getOferta(String ip) {
        return ofertas.get(ip);
    }

    /**
     * Obtiene todas las ofertas
     */
    public Map<String, Double> getOfertas() {
        return new HashMap<>(ofertas);
    }

    /**
     * Calcula y retorna la oferta ganadora
     * @return Map.Entry con IP del ganador y monto, o null si no hay ofertas
     */
    public synchronized Map.Entry<String, Double> getOfertaGanadora() {
        if (ofertas.isEmpty()) {
            return null;
        }

        // Buscar la oferta máxima
        Map.Entry<String, Double> ganador = null;
        double montoMaximo = 0;

        for (Map.Entry<String, Double> entry : ofertas.entrySet()) {
            if (entry.getValue() > montoMaximo) {
                montoMaximo = entry.getValue();
                ganador = entry;
            }
        }

        return ganador;
    }

    /**
     * Retorna el número de ofertas registradas
     */
    public int getCantidadOfertas() {
        return ofertas.size();
    }

    /**
     * Verifica si hay alguna oferta registrada
     */
    public boolean tieneOfertas() {
        return !ofertas.isEmpty();
    }

    /**
     * Limpia todas las ofertas (para nueva subasta)
     */
    public synchronized void limpiar() {
        ofertas.clear();
        timestampUltimaActualizacion = System.currentTimeMillis();
        System.out.println("[ESTADO] Estado limpiado");
    }

    /**
     * Sincroniza este estado con otro estado recibido
     * Solo actualiza si el estado recibido es más reciente
     */
    public synchronized void sincronizarCon(EstadoSubastaReplicado otroEstado) {
        if (otroEstado == null) return;

        // Solo sincronizar si el otro estado es más reciente
        if (otroEstado.timestampUltimaActualizacion > this.timestampUltimaActualizacion) {
            this.ofertas = new ConcurrentHashMap<>(otroEstado.ofertas);
            this.timestampUltimaActualizacion = otroEstado.timestampUltimaActualizacion;
            System.out.println("[ESTADO] Sincronizado con estado más reciente. Ofertas: " + ofertas.size());
        }
    }

    /**
     * Merge de estados: combina ofertas de ambos estados
     */
    public synchronized void mergeCon(EstadoSubastaReplicado otroEstado) {
        if (otroEstado == null) return;

        for (Map.Entry<String, Double> entry : otroEstado.ofertas.entrySet()) {
            // Agregar ofertas que no tenemos
            ofertas.putIfAbsent(entry.getKey(), entry.getValue());
        }

        // Actualizar timestamp al más reciente
        this.timestampUltimaActualizacion = Math.max(
            this.timestampUltimaActualizacion,
            otroEstado.timestampUltimaActualizacion
        );

        System.out.println("[ESTADO] Merge completado. Total ofertas: " + ofertas.size());
    }

    /**
     * Crea una copia profunda del estado
     */
    public synchronized EstadoSubastaReplicado clonar() {
        EstadoSubastaReplicado copia = new EstadoSubastaReplicado();
        copia.ofertas = new ConcurrentHashMap<>(this.ofertas);
        copia.timestampUltimaActualizacion = this.timestampUltimaActualizacion;
        return copia;
    }

    public long getTimestampUltimaActualizacion() {
        return timestampUltimaActualizacion;
    }

    @Override
    public String toString() {
        Map.Entry<String, Double> ganador = getOfertaGanadora();
        return "EstadoSubasta{" +
               "ofertas=" + ofertas.size() +
               ", ganador=" + (ganador != null ? ganador.getKey() + ":$" + ganador.getValue() : "ninguno") +
               ", timestamp=" + timestampUltimaActualizacion +
               '}';
    }
}
