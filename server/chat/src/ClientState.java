import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * ClientState.java — estado por conexión, asociado a cada SocketChannel
 * mediante SelectionKey.attach().
 *
 * Análogo al objeto de "tabla" descrito en arquitectura-sockets-no-bloqueantes.md.
 * Agrupa todo lo que el servidor necesita recordar sobre un cliente individual:
 *
 *   1. Mini máquina de estados de framing para lectura (el header llega en partes).
 *   2. Nombre de usuario (null hasta que el cliente hace LOGIN con éxito).
 *   3. Cola de ByteBuffers pendientes de escritura hacia este cliente.
 *   4. Referencia al SelectionKey para poder activar OP_WRITE desde el worker.
 *
 * Diseño de concurrencia:
 *   - El hilo de I/O es el ÚNICO que lee/escribe los campos de framing (phase,
 *     headerBuf, payloadBuf). No necesitan sincronización.
 *   - El campo `username` lo escribe el worker (al procesar LOGIN) y lo lee el hilo
 *     de I/O solo en closeKey() para armar el mensaje sintético de desconexión.
 *     En la práctica ese acceso no genera carrera porque closeKey se llama cuando
 *     el canal ya está muerto, pero se marca volatile por claridad.
 *   - `writeQueue` y `key` son accedidos por ambos hilos: el worker encola ByteBuffers
 *     y activa OP_WRITE; el hilo de I/O drena la cola. Se sincronizan con
 *     synchronized(this) en los sitios de acceso (ver IoLoop.enqueueWrite y
 *     IoLoop.handleWrite).
 */
public class ClientState {

    // ----------------------------------------------------------------
    // 1. Máquina de estados de framing (solo toca el hilo de I/O)
    // ----------------------------------------------------------------

    /** Fases del protocolo de framing de 4 bytes big-endian + payload. */
    public enum Phase {
        /** Acumulando los 4 bytes del header que indica el largo del payload. */
        WAITING_HEADER,
        /** Header completo; acumulando los N bytes del payload JSON. */
        WAITING_PAYLOAD
    }

    /** Fase actual del framing para esta conexión. */
    public Phase phase = Phase.WAITING_HEADER;

    /**
     * Buffer de 4 bytes para el header.
     * Se reutiliza: clear() al transicionar de vuelta a WAITING_HEADER.
     */
    public final ByteBuffer headerBuf = ByteBuffer.allocate(4);

    /**
     * Buffer para el payload. Se crea con el tamaño exacto una vez que
     * el header está completo; se descarta (GC) tras cada mensaje completo.
     */
    public ByteBuffer payloadBuf = null;

    // ----------------------------------------------------------------
    // 2. Identidad del cliente
    // ----------------------------------------------------------------

    /**
     * Nombre de usuario registrado. null hasta que el cliente envía LOGIN
     * y el worker lo procesa exitosamente.
     * volatile: garantiza visibilidad entre hilos sin necesitar synchronized.
     */
    public volatile String username = null;

    // ----------------------------------------------------------------
    // 3. Cola de salida (compartida entre worker e hilo de I/O)
    // ----------------------------------------------------------------

    /**
     * Bytes pendientes de escritura hacia este cliente, en orden FIFO.
     * Cada ByteBuffer ya está en posición flip() listo para channel.write().
     *
     * Acceso concurrente: se sincroniza con synchronized(clientState) en
     * IoLoop.enqueueWrite() (escritura desde el worker) y
     * IoLoop.handleWrite() (lectura/drenado desde el hilo de I/O).
     */
    public final Deque<ByteBuffer> writeQueue = new ArrayDeque<>();

    // ----------------------------------------------------------------
    // 4. Referencia al SelectionKey
    // ----------------------------------------------------------------

    /**
     * Key de este canal en el Selector. Se asigna justo después de register().
     * Necesario para que el worker pueda activar OP_WRITE desde otro hilo.
     */
    public SelectionKey key = null;
}
