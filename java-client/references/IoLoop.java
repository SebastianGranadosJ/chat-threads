import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

/**
 * IoLoop.java — hilo de I/O: el bucle Selector no bloqueante.
 *
 * ═══════════════════════════════════════════════════════════════
 *  CAPA DE I/O (análoga a server.py + connection.py del ATM)
 * ═══════════════════════════════════════════════════════════════
 *
 * Responsabilidades EXCLUSIVAS de este hilo:
 *   • Aceptar nuevas conexiones TCP (OP_ACCEPT).
 *   • Leer bytes y ensamblar mensajes completos con el framing de 4 bytes
 *     (máquina de estados WAITING_HEADER → WAITING_PAYLOAD, en ClientState).
 *   • Escribir bytes pendientes hacia los clientes (OP_WRITE).
 *   • Poner mensajes completos en la MessageQueue para el worker.
 *   • Detectar y limpiar desconexiones abruptas.
 *
 * Lo que este hilo NUNCA hace:
 *   • Leer el mapa de usuarios conectados.
 *   • Decidir a quién reenviar un mensaje.
 *   • Interpretar el `type` de un mensaje (excepto para armar el sobre de error
 *     cuando el JSON es malformado — respuesta inmediata al remitente).
 *
 * Modelo: un solo hilo atiende N sockets simultáneamente usando java.nio.Selector.
 * selector.select() bloquea eficientemente hasta que AL MENOS UN canal esté listo,
 * sin desperdiciar un hilo del SO por cliente.
 *
 * Interacción con WorkerThread:
 *   El worker llama a enqueueWrite(state, buf) para encolar bytes de salida.
 *   enqueueWrite activa OP_WRITE en el key y llama selector.wakeup() para que
 *   el selector salga de select() y procese la escritura en el próximo ciclo.
 */
public class IoLoop implements Runnable {

    private static final int PORT        = 5000;
    private static final int MAX_PAYLOAD = 1_000_000; // 1 MB — límite de sanidad

    private final MessageQueue queue;

    // El selector se inicializa en run() para que viva en el hilo de I/O.
    // volatile para que enqueueWrite() (llamado desde el worker) lo vea.
    private volatile Selector selector;

    private volatile boolean running = true;

    public IoLoop(MessageQueue queue) {
        this.queue = queue;
    }

    // ----------------------------------------------------------------
    // API pública para el WorkerThread
    // ----------------------------------------------------------------

    /**
     * Encola un ByteBuffer de salida para un cliente y activa OP_WRITE.
     * Llamado DESDE EL WORKER — debe ser thread-safe.
     *
     * @param state estado de la conexión destino
     * @param buf   ByteBuffer ya en flip(), listo para channel.write()
     */
    public void enqueueWrite(ClientState state, ByteBuffer buf) {
        synchronized (state) {
            state.writeQueue.addLast(buf);
            // Activar OP_WRITE. Si el key ya fue cancelado (canal cerrado),
            // CancelledKeyException indica que no hay nada que hacer.
            try {
                state.key.interestOps(state.key.interestOps() | SelectionKey.OP_WRITE);
            } catch (CancelledKeyException ignored) {
                return; // canal ya cerrado, el mensaje de salida se descarta
            }
        }
        // Despertar el selector para que vea el nuevo OP_WRITE en el próximo ciclo.
        // Sin wakeup(), el selector podría quedarse bloqueado en select() indefinidamente.
        if (selector != null) selector.wakeup();
    }

    // ----------------------------------------------------------------
    // Bucle principal
    // ----------------------------------------------------------------

    @Override
    public void run() {
        try {
            selector = Selector.open();

            // Canal de escucha no bloqueante, bind a 0.0.0.0:5000
            ServerSocketChannel serverCh = ServerSocketChannel.open();
            serverCh.configureBlocking(false);
            serverCh.bind(new InetSocketAddress("0.0.0.0", PORT));
            serverCh.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("[IO] Servidor escuchando en 0.0.0.0:" + PORT);

            while (running) {
                // Bloquea hasta que algún canal esté listo.
                // No es lo mismo que bloquear un hilo por cliente: un solo hilo
                // espera por TODOS los canales registrados simultáneamente.
                selector.select();

                if (!running) break;

                Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove(); // siempre remover antes de procesar

                    if (!key.isValid()) continue;

                    try {
                        if (key.isAcceptable()) {
                            handleAccept(serverCh);
                        } else if (key.isReadable()) {
                            handleRead(key);
                        } else if (key.isWritable()) {
                            handleWrite(key);
                        }
                    } catch (IOException e) {
                        // Error de I/O en este canal — cerrar limpiamente sin tumbar el servidor
                        System.err.println("[IO] Error en canal, cerrando: " + e.getMessage());
                        closeKey(key);
                    }
                }
            }

            selector.close();
        } catch (IOException e) {
            System.err.println("[IO] Error fatal en el Selector: " + e.getMessage());
        }
    }

    // ----------------------------------------------------------------
    // Handlers del Selector
    // ----------------------------------------------------------------

    /** OP_ACCEPT: acepta la conexión entrante, crea ClientState, registra OP_READ. */
    private void handleAccept(ServerSocketChannel serverCh) throws IOException {
        SocketChannel client = serverCh.accept();
        if (client == null) return; // no debería pasar, pero se defiende

        client.configureBlocking(false);

        ClientState state = new ClientState();
        SelectionKey key  = client.register(selector, SelectionKey.OP_READ, state);
        state.key = key;

        System.out.println("[IO] Nueva conexión desde " + client.getRemoteAddress());
    }

    /**
     * OP_READ: lee los bytes disponibles y los procesa en la máquina de estados
     * de framing del ClientState.
     *
     * Un mensaje puede llegar fragmentado en múltiples lecturas (TCP no garantiza
     * límites de mensaje). La máquina de estados acumula hasta que el mensaje está
     * completo y solo entonces lo mete en la cola.
     */
    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel ch    = (SocketChannel) key.channel();
        ClientState state   = (ClientState) key.attachment();

        ByteBuffer tmp = ByteBuffer.allocate(4096);
        int bytesRead  = ch.read(tmp);

        if (bytesRead == -1) {
            // El cliente cerró la conexión en su extremo (EOF)
            System.out.println("[IO] Desconexión detectada: " + ch.getRemoteAddress());
            closeKey(key);
            return;
        }

        if (bytesRead == 0) return; // nada nuevo que procesar

        tmp.flip();
        processIncoming(ch, state, tmp, key);
    }

    /**
     * Máquina de estados de framing (análoga a _recv_exact de connection.py).
     *
     * Fases:
     *   WAITING_HEADER  → acumular 4 bytes → extraer longitud → ir a WAITING_PAYLOAD
     *   WAITING_PAYLOAD → acumular N bytes → armar JSON → meter en queue → volver a WAITING_HEADER
     *
     * Se llama en un loop sobre los bytes disponibles porque un chunk de red puede
     * contener más de un mensaje completo.
     */
    private void processIncoming(SocketChannel ch, ClientState state,
                                 ByteBuffer data, SelectionKey key) {
        while (data.hasRemaining()) {

            if (state.phase == ClientState.Phase.WAITING_HEADER) {
                // Copiar lo que haga falta para completar los 4 bytes del header
                transferBytes(data, state.headerBuf);

                if (!state.headerBuf.hasRemaining()) {
                    // Header completo: leer la longitud del payload
                    state.headerBuf.flip();
                    int payloadLen = state.headerBuf.getInt();
                    state.headerBuf.clear(); // reusar en el próximo mensaje

                    if (payloadLen <= 0 || payloadLen > MAX_PAYLOAD) {
                        System.err.println("[IO] Longitud de payload inválida ("
                            + payloadLen + "), cerrando: " + ch);
                        closeKey(key);
                        return;
                    }

                    state.payloadBuf = ByteBuffer.allocate(payloadLen);
                    state.phase      = ClientState.Phase.WAITING_PAYLOAD;
                }

            } else { // WAITING_PAYLOAD
                transferBytes(data, state.payloadBuf);

                if (!state.payloadBuf.hasRemaining()) {
                    // Payload completo: armar el JSON
                    state.payloadBuf.flip();
                    byte[] raw  = new byte[state.payloadBuf.limit()];
                    state.payloadBuf.get(raw);
                    String json = new String(raw, StandardCharsets.UTF_8);

                    Message msg = JsonHelper.parse(ch, state, json);

                    if (msg != null) {
                        queue.put(msg); // entregar al worker vía la MessageQueue
                    } else {
                        // JSON malformado: responder ERROR al remitente, no afectar al resto
                        System.err.println("[IO] JSON malformado de " + ch + ": " + json);
                        String errJson = JsonHelper.toJson(
                            "ERROR", null, null,
                            "Mensaje malformado: JSON invalido",
                            null
                        );
                        enqueueWrite(state, JsonHelper.encode(errJson));
                    }

                    // Reiniciar para el próximo mensaje
                    state.payloadBuf = null;
                    state.phase      = ClientState.Phase.WAITING_HEADER;
                }
            }
        }
    }

    /**
     * Copia bytes desde `src` hacia `dst` sin exceder el espacio disponible en dst.
     * Equivalente al loop de _recv_exact, pero sin bloquear.
     */
    private static void transferBytes(ByteBuffer src, ByteBuffer dst) {
        int toCopy = Math.min(src.remaining(), dst.remaining());
        // Slice temporal para limitar cuánto se lee de src
        int oldLimit = src.limit();
        src.limit(src.position() + toCopy);
        dst.put(src);
        src.limit(oldLimit);
    }

    /**
     * OP_WRITE: drena la writeQueue hacia el canal sin bloquear.
     *
     * Si channel.write() no puede enviar todos los bytes (buffer del SO lleno),
     * el ByteBuffer queda parcialmente consumido y la próxima vez se retoma.
     * Cuando la cola queda vacía se desactiva OP_WRITE para no desperdiciar ciclos.
     */
    private void handleWrite(SelectionKey key) throws IOException {
        SocketChannel ch  = (SocketChannel) key.channel();
        ClientState state = (ClientState) key.attachment();

        synchronized (state) {
            while (!state.writeQueue.isEmpty()) {
                ByteBuffer buf = state.writeQueue.peekFirst();
                ch.write(buf); // no bloqueante: escribe lo que el SO pueda

                if (buf.hasRemaining()) {
                    // El canal no admitió todos los bytes — volver en el próximo ciclo
                    break;
                }
                state.writeQueue.pollFirst(); // buffer agotado, sacarlo
            }

            if (state.writeQueue.isEmpty()) {
                // Ya no hay nada pendiente: desactivar OP_WRITE para no girar en vacío
                key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
            }
        }
    }

    // ----------------------------------------------------------------
    // Cierre de conexión
    // ----------------------------------------------------------------

    /**
     * Cierra la conexión limpiamente y notifica al worker mediante un mensaje
     * sintético USER_DISCONNECTED, para que actualice el mapa de usuarios.
     *
     * Se llama tanto por desconexión normal (EOF) como por error de I/O.
     * Es idempotente: si el key ya fue cancelado, la segunda llamada es no-op.
     */
    public void closeKey(SelectionKey key) {
        if (!key.isValid()) return; // ya fue cancelado, no re-procesar

        SocketChannel ch  = (SocketChannel) key.channel();
        ClientState state = (ClientState) key.attachment();

        key.cancel();
        try { ch.close(); } catch (IOException ignored) {}

        if (state != null) {
            // Mensaje sintético para que el worker sepa que este cliente se fue.
            // El `sender` es el username (puede ser null si nunca hizo LOGIN).
            Message disconnectMsg = new Message(
                "USER_DISCONNECTED",
                state.username, // puede ser null — el worker lo comprueba
                null, null, null,
                ch, state
            );
            queue.put(disconnectMsg);
        }
    }

    // ----------------------------------------------------------------
    // Control de ciclo de vida
    // ----------------------------------------------------------------

    /** Señala al hilo de I/O que debe terminar en el próximo ciclo. */
    public void stop() {
        running = false;
        if (selector != null) selector.wakeup();
    }
}
