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

    // Mapa thread-safe de ofertas: ID del cliente (IP:Puerto) -> monto ofertado
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
     * @param clienteId ID único del cliente (IP:Puerto)
     * @param monto Monto de la oferta
     * @return true si la oferta fue aceptada, false si fue rechazada por ser menor o igual a la anterior
     */
    public synchronized boolean agregarOferta(String clienteId, double monto) {
        // Log estado antes de agregar
        System.out.println("[ESTADO] ANTES de agregar - Total ofertas: " + ofertas.size());
        if (!ofertas.isEmpty()) {
            System.out.println("[ESTADO] Ofertas existentes:");
            for (Map.Entry<String, Double> entry : ofertas.entrySet()) {
                System.out.println("[ESTADO]   - " + entry.getKey() + " -> $" + entry.getValue());
            }
        }

        // Verificar si el cliente ya tiene una oferta
        Double ofertaAnterior = ofertas.get(clienteId);
        if (ofertaAnterior != null && monto <= ofertaAnterior) {
            System.out.println("[ESTADO] ✗ Oferta RECHAZADA: " + clienteId + " -> $" + monto +
                             " (oferta anterior: $" + ofertaAnterior + ")");
            return false;
        }

        ofertas.put(clienteId, monto);
        timestampUltimaActualizacion = System.currentTimeMillis();

        System.out.println("[ESTADO] ✓ Oferta ACEPTADA: " + clienteId + " -> $" + monto);
        if (ofertaAnterior != null) {
            System.out.println("[ESTADO]   (mejoró su oferta anterior de $" + ofertaAnterior + ")");
        }
        System.out.println("[ESTADO] DESPUÉS de agregar - Total de ofertas: " + ofertas.size());

        // Mostrar ganador actual
        Map.Entry<String, Double> ganador = getOfertaGanadora();
        if (ganador != null) {
            System.out.println("[ESTADO] Ganador actual: " + ganador.getKey() + " -> $" + ganador.getValue());
        }

        return true;
    }

    /**
     * Obtiene la oferta de un cliente específico
     * @param clienteId ID único del cliente (IP:Puerto)
     */
    public Double getOferta(String clienteId) {
        return ofertas.get(clienteId);
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
     * Solo actualiza si el estado recibido es más reciente O si el estado local está vacío
     */
    public synchronized void sincronizarCon(EstadoSubastaReplicado otroEstado) {
        if (otroEstado == null) return;

        System.out.println("[ESTADO] Intentando sincronizar:");
        System.out.println("[ESTADO]   - Estado local: " + ofertas.size() + " ofertas, timestamp: " + this.timestampUltimaActualizacion);
        System.out.println("[ESTADO]   - Estado remoto: " + otroEstado.ofertas.size() + " ofertas, timestamp: " + otroEstado.timestampUltimaActualizacion);

        // Si mi estado está vacío, tomar el estado del otro nodo sin importar timestamp
        // Si mi estado tiene ofertas, solo sincronizar si el otro es más reciente
        if (this.ofertas.isEmpty() && !otroEstado.ofertas.isEmpty()) {
            System.out.println("[ESTADO] Estado local vacío - aceptando estado remoto");
            this.ofertas = new ConcurrentHashMap<>(otroEstado.ofertas);
            this.timestampUltimaActualizacion = otroEstado.timestampUltimaActualizacion;
            System.out.println("[ESTADO] ✓ Sincronizado. Ofertas: " + ofertas.size());
        } else if (otroEstado.timestampUltimaActualizacion > this.timestampUltimaActualizacion) {
            System.out.println("[ESTADO] Estado remoto más reciente - sincronizando");
            this.ofertas = new ConcurrentHashMap<>(otroEstado.ofertas);
            this.timestampUltimaActualizacion = otroEstado.timestampUltimaActualizacion;
            System.out.println("[ESTADO] ✓ Sincronizado. Ofertas: " + ofertas.size());
        } else {
            System.out.println("[ESTADO] Estado local es más reciente o igual - no se sincroniza");
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
