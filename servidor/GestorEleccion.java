package socket.conconexion.servidor;

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Implementa el algoritmo Bully para elección de coordinador
 * en un sistema distribuido de servidores de subasta.
 * @author Sistema de Subasta Distribuido
 */
public class GestorEleccion {
    private NodoSubasta nodoLocal;                    // Este nodo
    private Map<Integer, NodoSubasta> nodos;          // Todos los nodos conocidos
    private volatile Integer idCoordinadorActual;     // ID del coordinador actual
    private volatile boolean enEleccion;              // Si hay una elección en curso
    private final Object lockEleccion = new Object();

    private static final int TIMEOUT_RESPUESTA = 3000;  // 3 segundos para respuestas
    private static final int TIMEOUT_HEARTBEAT = 10000; // 10 segundos sin heartbeat = nodo muerto
    private static final int INTERVALO_HEARTBEAT = 5000; // Enviar heartbeat cada 5 segundos

    private ExecutorService poolHilos;
    private ScheduledExecutorService scheduledPool;
    private volatile boolean activo;

    /**
     * Constructor
     */
    public GestorEleccion(NodoSubasta nodoLocal, List<NodoSubasta> todosLosNodos) {
        this.nodoLocal = nodoLocal;
        this.nodos = new ConcurrentHashMap<>();
        this.enEleccion = false;
        this.activo = true;

        // Agregar todos los nodos
        for (NodoSubasta nodo : todosLosNodos) {
            nodos.put(nodo.getId(), nodo);
        }

        this.poolHilos = Executors.newCachedThreadPool();
        this.scheduledPool = Executors.newScheduledThreadPool(3);

        System.out.println("[BULLY] Gestor de elección iniciado para nodo " + nodoLocal.getId());

        // Iniciar monitoreo de heartbeats del coordinador
        iniciarMonitoreoCoordinador();
    }

    /**
     * Inicia una elección usando el algoritmo Bully
     */
    public void iniciarEleccion() {
        synchronized(lockEleccion) {
            if (enEleccion) {
                System.out.println("[BULLY] Elección ya en curso, ignorando...");
                return;
            }
            enEleccion = true;
        }

        System.out.println("\n===========================================");
        System.out.println("  INICIANDO ELECCION - Algoritmo Bully");
        System.out.println("===========================================");
        System.out.println("[BULLY] Nodo " + nodoLocal.getId() + " inicia eleccion");

        try {
            // Enviar mensaje ELECTION a todos los nodos con ID mayor
            List<NodoSubasta> nodosSuperiores = obtenerNodosSuperiores();

            if (nodosSuperiores.isEmpty()) {
                // Soy el nodo con mayor ID, me convierto en coordinador
                System.out.println("[BULLY] No hay nodos superiores. Asumiendo coordinación.");
                convertirseEnCoordinador();
                return;
            }

            System.out.println("[BULLY] Enviando ELECTION a " + nodosSuperiores.size() + " nodos superiores");

            // Enviar mensajes ELECTION y esperar respuestas OK
            CountDownLatch latch = new CountDownLatch(nodosSuperiores.size());
            List<Boolean> respuestas = new CopyOnWriteArrayList<>();

            for (NodoSubasta nodo : nodosSuperiores) {
                poolHilos.submit(() -> {
                    try {
                        boolean respuesta = enviarMensajeElection(nodo);
                        respuestas.add(respuesta);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Esperar respuestas con timeout
            boolean todasRespuestas = latch.await(TIMEOUT_RESPUESTA, TimeUnit.MILLISECONDS);

            // Contar cuántos respondieron OK
            long respuestasOK = respuestas.stream().filter(r -> r).count();

            System.out.println("[BULLY] Respuestas OK recibidas: " + respuestasOK + "/" + nodosSuperiores.size());

            if (respuestasOK == 0) {
                // Ningún nodo superior respondió, me convierto en coordinador
                System.out.println("[BULLY] Ningún nodo superior respondió. Asumiendo coordinación.");
                convertirseEnCoordinador();
            } else {
                // Al menos un nodo superior está vivo, esperar mensaje COORDINATOR
                System.out.println("[BULLY] Esperando mensaje COORDINATOR de un nodo superior...");
                esperarMensajeCoordinador();
            }

        } catch (Exception e) {
            System.out.println("[BULLY] Error durante elección: " + e.getMessage());
            e.printStackTrace();
        } finally {
            synchronized(lockEleccion) {
                enEleccion = false;
            }
        }
    }

    /**
     * Envía mensaje ELECTION a un nodo y espera respuesta OK
     */
    private boolean enviarMensajeElection(NodoSubasta nodoDestino) {
        try {
            System.out.println("[BULLY] -> Enviando ELECTION a nodo " + nodoDestino.getId());

            MensajeBully mensaje = new MensajeBully(MensajeBully.TipoMensaje.ELECTION, nodoLocal.getId());
            MensajeBully respuesta = enviarYEsperarRespuesta(nodoDestino, mensaje, TIMEOUT_RESPUESTA);

            if (respuesta != null && respuesta.getTipo() == MensajeBully.TipoMensaje.OK) {
                System.out.println("[BULLY] <- Recibido OK de nodo " + nodoDestino.getId());
                return true;
            }

        } catch (Exception e) {
            System.out.println("[BULLY] Nodo " + nodoDestino.getId() + " no respondió: " + e.getMessage());
        }

        return false;
    }

    /**
     * Convierte este nodo en el coordinador
     */
    private void convertirseEnCoordinador() {
        idCoordinadorActual = nodoLocal.getId();

        System.out.println("\n===========================================");
        System.out.println("  NUEVO COORDINADOR: Nodo " + nodoLocal.getId());
        System.out.println("===========================================");

        // Anunciar a todos los demas nodos
        anunciarCoordinador();

        // Iniciar envío periódico de heartbeats
        iniciarHeartbeats();
    }

    /**
     * Anuncia que este nodo es el nuevo coordinador
     */
    private void anunciarCoordinador() {
        System.out.println("[BULLY] Anunciando coordinación a todos los nodos...");

        MensajeBully mensaje = new MensajeBully(
            MensajeBully.TipoMensaje.COORDINATOR,
            nodoLocal.getId(),
            nodoLocal.getId(),
            nodoLocal.getHost(),
            nodoLocal.getPuertoClientes()
        );

        for (NodoSubasta nodo : nodos.values()) {
            if (nodo.getId() != nodoLocal.getId()) {
                poolHilos.submit(() -> {
                    try {
                        enviarMensaje(nodo, mensaje);
                        System.out.println("[BULLY] Coordinador anunciado a nodo " + nodo.getId());
                    } catch (Exception e) {
                        System.out.println("[BULLY] Error al anunciar a nodo " + nodo.getId());
                    }
                });
            }
        }
    }

    /**
     * Espera recibir mensaje COORDINATOR de un nodo superior
     */
    private void esperarMensajeCoordinador() {
        // Este método sería llamado por el hilo que escucha mensajes entrantes
        // Por simplicidad, establecemos un timeout
        try {
            Thread.sleep(TIMEOUT_RESPUESTA * 2);

            if (idCoordinadorActual == null) {
                System.out.println("[BULLY] No se recibió COORDINATOR. Reintentando elección...");
                iniciarEleccion();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Inicia el envío periódico de heartbeats (solo si es coordinador)
     */
    private void iniciarHeartbeats() {
        scheduledPool.scheduleAtFixedRate(() -> {
            if (idCoordinadorActual != null && idCoordinadorActual.equals(nodoLocal.getId())) {
                enviarHeartbeats();
            }
        }, INTERVALO_HEARTBEAT, INTERVALO_HEARTBEAT, TimeUnit.MILLISECONDS);

        System.out.println("[BULLY] Heartbeats iniciados (cada " + (INTERVALO_HEARTBEAT/1000) + " segundos)");
    }

    /**
     * Envía heartbeats a todos los nodos
     */
    private void enviarHeartbeats() {
        MensajeBully heartbeat = new MensajeBully(MensajeBully.TipoMensaje.HEARTBEAT, nodoLocal.getId());

        for (NodoSubasta nodo : nodos.values()) {
            if (nodo.getId() != nodoLocal.getId()) {
                try {
                    enviarMensaje(nodo, heartbeat);
                } catch (Exception e) {
                    // Ignorar errores en heartbeat
                }
            }
        }
    }

    /**
     * Procesa un mensaje recibido del protocolo Bully
     */
    public void procesarMensaje(MensajeBully mensaje, Socket socketOrigen) {
        try {
            System.out.println("[BULLY] Mensaje recibido: " + mensaje);

            switch (mensaje.getTipo()) {
                case ELECTION:
                    manejarElection(mensaje, socketOrigen);
                    break;

                case OK:
                    // Ya procesado en enviarYEsperarRespuesta
                    break;

                case COORDINATOR:
                    manejarCoordinator(mensaje);
                    break;

                case HEARTBEAT:
                    manejarHeartbeat(mensaje);
                    break;

                default:
                    System.out.println("[BULLY] Tipo de mensaje desconocido: " + mensaje.getTipo());
            }
        } catch (Exception e) {
            System.out.println("[BULLY] Error al procesar mensaje: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Maneja mensaje ELECTION
     */
    private void manejarElection(MensajeBully mensaje, Socket socketOrigen) throws IOException {
        System.out.println("[BULLY] Recibido ELECTION de nodo " + mensaje.getIdEmisor());

        // Responder con OK
        MensajeBully respuesta = new MensajeBully(MensajeBully.TipoMensaje.OK, nodoLocal.getId());
        ObjectOutputStream out = new ObjectOutputStream(socketOrigen.getOutputStream());
        out.writeObject(respuesta);
        out.flush();

        System.out.println("[BULLY] Enviado OK a nodo " + mensaje.getIdEmisor());

        // Iniciar mi propia elección (tengo mayor ID)
        poolHilos.submit(() -> iniciarEleccion());
    }

    /**
     * Maneja mensaje COORDINATOR
     */
    private void manejarCoordinator(MensajeBully mensaje) {
        System.out.println("\n===========================================");
        System.out.println("  COORDINADOR ACTUALIZADO");
        System.out.println("===========================================");
        System.out.println("[BULLY] Nuevo coordinador: Nodo " + mensaje.getIdEmisor());

        idCoordinadorActual = mensaje.getIdEmisor();

        synchronized(lockEleccion) {
            enEleccion = false;
        }
    }

    /**
     * Maneja mensaje HEARTBEAT
     */
    private void manejarHeartbeat(MensajeBully mensaje) {
        int idEmisor = mensaje.getIdEmisor();
        NodoSubasta nodo = nodos.get(idEmisor);

        if (nodo != null) {
            nodo.actualizarLatido();
        }

        // Si el heartbeat es del coordinador, actualizar timestamp
        if (idEmisor == idCoordinadorActual) {
            // Coordinador está vivo, actualizar su nodo
            if (nodo != null) {
                nodo.actualizarLatido();
            }
        }
    }

    /**
     * Inicia el monitoreo de heartbeats del coordinador
     * Si el coordinador no envía heartbeat por TIMEOUT_HEARTBEAT, inicia elección
     */
    public void iniciarMonitoreoCoordinador() {
        scheduledPool.scheduleAtFixedRate(() -> {
            try {
                if (!activo) return;

                // Si no soy coordinador, verificar que el coordinador esté vivo
                if (idCoordinadorActual != null && !idCoordinadorActual.equals(nodoLocal.getId())) {
                    NodoSubasta coordinador = nodos.get(idCoordinadorActual);

                    if (coordinador != null && !coordinador.estaVivo(TIMEOUT_HEARTBEAT)) {
                        long tiempoSinHeartbeat = System.currentTimeMillis() - coordinador.getUltimoLatido();

                        System.out.println("\n[BULLY] !!! COORDINADOR CAIDO !!!");
                        System.out.println("[BULLY] Coordinador ID: " + idCoordinadorActual);
                        System.out.println("[BULLY] Ultimo heartbeat: " + tiempoSinHeartbeat + " ms atrás");
                        System.out.println("[BULLY] Iniciando nueva elección...");

                        // Marcar SOLO el coordinador caído como inactivo
                        coordinador.setActivo(false);

                        // Limpiar ID del coordinador
                        Integer coordinadorCaido = idCoordinadorActual;
                        idCoordinadorActual = null;

                        // Iniciar elección UNA SOLA VEZ
                        // Usar un flag para evitar múltiples elecciones simultáneas
                        synchronized(lockEleccion) {
                            if (!enEleccion) {
                                poolHilos.submit(() -> iniciarEleccion());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("[BULLY] Error en monitor de heartbeats: " + e.getMessage());
            }
        }, TIMEOUT_HEARTBEAT, TIMEOUT_HEARTBEAT, TimeUnit.MILLISECONDS);

        System.out.println("[BULLY] Monitor de heartbeats iniciado (verificación cada " +
                         (TIMEOUT_HEARTBEAT/1000) + " segundos)");
    }

    /**
     * Obtiene lista de nodos con ID superior al local
     */
    private List<NodoSubasta> obtenerNodosSuperiores() {
        List<NodoSubasta> superiores = new ArrayList<>();

        for (NodoSubasta nodo : nodos.values()) {
            if (nodo.getId() > nodoLocal.getId() && nodo.isActivo()) {
                superiores.add(nodo);
            }
        }

        superiores.sort(Comparator.reverseOrder()); // Mayor a menor
        return superiores;
    }

    /**
     * Envía un mensaje a un nodo y espera respuesta
     */
    private MensajeBully enviarYEsperarRespuesta(NodoSubasta nodo, MensajeBully mensaje, int timeout)
            throws IOException, ClassNotFoundException {

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(nodo.getHost(), nodo.getPuertoCoordinacion()), timeout);
            socket.setSoTimeout(timeout);

            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.writeObject(mensaje);
            out.flush();

            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            return (MensajeBully) in.readObject();

        } catch (SocketTimeoutException e) {
            System.out.println("[BULLY] Timeout al comunicar con nodo " + nodo.getId());
            nodo.setActivo(false);
            return null;
        }
    }

    /**
     * Envía un mensaje sin esperar respuesta
     */
    private void enviarMensaje(NodoSubasta nodo, MensajeBully mensaje) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(nodo.getHost(), nodo.getPuertoCoordinacion()), 2000);

            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.writeObject(mensaje);
            out.flush();

        } catch (IOException e) {
            nodo.setActivo(false);
            throw e;
        }
    }

    // Getters
    public boolean esCoordinador() {
        return idCoordinadorActual != null && idCoordinadorActual.equals(nodoLocal.getId());
    }

    public Integer getIdCoordinadorActual() {
        return idCoordinadorActual;
    }

    public NodoSubasta getCoordinador() {
        if (idCoordinadorActual == null) return null;
        return nodos.get(idCoordinadorActual);
    }

    public void detener() {
        activo = false;
        poolHilos.shutdown();
        scheduledPool.shutdown();
    }
}
