import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WorkerThread.java — hilo de procesamiento: la capa de dominio/negocio.
 *
 * ═══════════════════════════════════════════════════════════════
 *  CAPA DE DOMINIO (análoga a atm_server.py del ATM)
 * ═══════════════════════════════════════════════════════════════
 *
 * Responsabilidades EXCLUSIVAS de este hilo:
 *   • Consumir mensajes de la MessageQueue (bloqueándose con wait() si está vacía).
 *   • Interpretar el campo `type` y ejecutar la lógica correspondiente.
 *   • Mantener el mapa de usuarios conectados (username → SocketChannel).
 *   • Decidir a quién reenviar cada mensaje y solicitar la escritura al IoLoop.
 *
 * Lo que este hilo NUNCA hace:
 *   • Leer o escribir directamente bytes en un SocketChannel.
 *   • Llamar a selector.select() o cualquier API de java.nio.channels directamente.
 *
 * Diseño de concurrencia:
 *   `userMap` y `stateMap` son accedidos SOLO por este hilo (el hilo de I/O
 *   nunca los toca), por lo que no necesitan sincronización propia.
 *   La única sincronización real de este sistema es la MessageQueue.
 *
 * Tipos de mensaje del protocolo (protocolo-mensajes-chat.md):
 *   LOGIN            cliente → servidor  registrar usuario
 *   LOGIN_OK         servidor → cliente  confirmación exitosa
 *   LOGIN_ERROR      servidor → cliente  rechazo (nombre en uso)
 *   GROUP_MESSAGE    cliente → servidor  reenviar a todos
 *   PRIVATE_MESSAGE  cliente → servidor  reenviar solo al destinatario
 *   USER_CONNECTED   servidor → todos    aviso automático al entrar
 *   USER_DISCONNECTED servidor → todos   aviso automático al salir
 *   USER_LIST        servidor → cliente  lista al hacer login
 *   ERROR            servidor → cliente  mensaje privado a usuario inexistente, etc.
 */
public class WorkerThread implements Runnable {

    private final MessageQueue queue;
    private final IoLoop ioLoop;

    // Mapa de usuarios activos. Solo este hilo lo modifica → sin sincronización.
    // username → SocketChannel (para buscar a quién enviar por nombre)
    private final Map<String, SocketChannel> userMap  = new HashMap<>();

    // SocketChannel → ClientState (para poder encolar bytes de salida hacia ese canal)
    private final Map<SocketChannel, ClientState> stateMap = new HashMap<>();

    public WorkerThread(MessageQueue queue, IoLoop ioLoop) {
        this.queue  = queue;
        this.ioLoop = ioLoop;
    }

    // ----------------------------------------------------------------
    // Bucle principal
    // ----------------------------------------------------------------

    @Override
    public void run() {
        System.out.println("[WORKER] Hilo de procesamiento iniciado.");
        try {
            while (true) {
                // take() bloquea con wait() si la cola está vacía (ver MessageQueue.java)
                Message msg = queue.take();
                dispatch(msg);
            }
        } catch (InterruptedException e) {
            System.out.println("[WORKER] Hilo interrumpido, terminando.");
            Thread.currentThread().interrupt();
        }
    }

    // ----------------------------------------------------------------
    // Dispatcher: enruta según el type del mensaje
    // ----------------------------------------------------------------

    private void dispatch(Message msg) {
        switch (msg.type) {
            case "LOGIN":
                handleLogin(msg);
                break;
            case "GROUP_MESSAGE":
                handleGroupMessage(msg);
                break;
            case "PRIVATE_MESSAGE":
                handlePrivateMessage(msg);
                break;
            case "USER_DISCONNECTED":
                handleDisconnect(msg);
                break;
            default:
                // Tipo desconocido — responder ERROR al remitente
                System.err.println("[WORKER] Tipo de mensaje desconocido: " + msg.type);
                if (msg.sourceState != null) {
                    send(msg.sourceState,
                         JsonHelper.toJson("ERROR", null, msg.sender,
                                           "Tipo de mensaje desconocido: " + msg.type));
                }
                break;
        }
    }

    // ----------------------------------------------------------------
    // Handlers de lógica de negocio
    // ----------------------------------------------------------------

    /**
     * LOGIN: registra el nombre de usuario.
     *
     * Flujo exitoso:
     *   1. Verificar que el nombre no esté ya en uso.
     *   2. Registrar en userMap y stateMap.
     *   3. Asignar username al ClientState.
     *   4. Enviar LOGIN_OK al nuevo cliente.
     *   5. Enviar USER_LIST al nuevo cliente (lista de conectados).
     *   6. Broadcast USER_CONNECTED a todos los demás.
     */
    private void handleLogin(Message msg) {
        String username = msg.sender; // el cliente manda su nombre en `sender`

        if (username == null || username.trim().isEmpty()) {
            send(msg.sourceState,
                 JsonHelper.toJson("LOGIN_ERROR", null, null, "Nombre de usuario vacio"));
            return;
        }

        username = username.trim();

        if (userMap.containsKey(username)) {
            // Nombre ya en uso
            send(msg.sourceState,
                 JsonHelper.toJson("LOGIN_ERROR", null, null,
                                   "El nombre '" + username + "' ya esta en uso"));
            return;
        }

        // Registrar al nuevo usuario
        userMap.put(username, msg.source);
        stateMap.put(msg.source, msg.sourceState);
        msg.sourceState.username = username; // visible para IoLoop.closeKey()

        System.out.println("[WORKER] Usuario conectado: " + username
                           + " (total: " + userMap.size() + ")");

        // 4. LOGIN_OK
        send(msg.sourceState,
             JsonHelper.toJson("LOGIN_OK", null, username, "Bienvenido, " + username));

        // 5. USER_LIST — lista de usuarios ya conectados (excluyendo al recién llegado)
        String userList = buildUserList(username);
        send(msg.sourceState,
             JsonHelper.toJson("USER_LIST", null, username, userList));

        // 6. Broadcast USER_CONNECTED a todos los demás
        String connJson = JsonHelper.toJson("USER_CONNECTED", username, null, username);
        broadcastExcluding(connJson, msg.source);
    }

    /**
     * GROUP_MESSAGE: reenviar a todos los usuarios conectados (incluyendo el remitente,
     * para que vea eco de su propio mensaje). El servidor agrega el timestamp.
     */
    private void handleGroupMessage(Message msg) {
        if (msg.sourceState.username == null) {
            // Intentó mandar un mensaje sin haberse identificado
            send(msg.sourceState,
                 JsonHelper.toJson("ERROR", null, null, "Debe hacer LOGIN primero"));
            return;
        }

        System.out.println("[WORKER] GROUP de " + msg.sourceState.username
                           + ": " + msg.content);

        // El servidor reemplaza el sender con el username autenticado (no confiar en el cliente)
        // y agrega el timestamp actual.
        String outJson = JsonHelper.toJson(
            "GROUP_MESSAGE",
            msg.sourceState.username,
            null,
            msg.content
            // timestamp generado dentro de toJson()
        );

        broadcastAll(outJson);
    }

    /**
     * PRIVATE_MESSAGE: reenviar solo al usuario indicado en `recipient`.
     * Si el destinatario no existe, responder ERROR al remitente.
     */
    private void handlePrivateMessage(Message msg) {
        if (msg.sourceState.username == null) {
            send(msg.sourceState,
                 JsonHelper.toJson("ERROR", null, null, "Debe hacer LOGIN primero"));
            return;
        }

        String recipient = msg.recipient;
        if (recipient == null || recipient.trim().isEmpty()) {
            send(msg.sourceState,
                 JsonHelper.toJson("ERROR", null, msg.sourceState.username,
                                   "PRIVATE_MESSAGE requiere destinatario"));
            return;
        }

        recipient = recipient.trim();
        SocketChannel targetCh = userMap.get(recipient);

        if (targetCh == null) {
            send(msg.sourceState,
                 JsonHelper.toJson("ERROR", null, msg.sourceState.username,
                                   "Usuario '" + recipient + "' no esta conectado"));
            return;
        }

        System.out.println("[WORKER] PRIVADO de " + msg.sourceState.username
                           + " → " + recipient);

        // Reenviar al destinatario con timestamp del servidor
        String outJson = JsonHelper.toJson(
            "PRIVATE_MESSAGE",
            msg.sourceState.username,
            recipient,
            msg.content
        );

        ClientState targetState = stateMap.get(targetCh);
        if (targetState != null) {
            send(targetState, outJson);
        }

        // Eco al remitente (para que vea su mensaje enviado con timestamp del servidor)
        send(msg.sourceState, outJson);
    }

    /**
     * USER_DISCONNECTED: mensaje sintético generado por IoLoop.closeKey().
     *
     * Limpia el mapa de usuarios y notifica a los demás.
     * El `sender` puede ser null si el cliente se desconectó antes de hacer LOGIN.
     */
    private void handleDisconnect(Message msg) {
        // Siempre limpiar stateMap por channel, independientemente del username
        stateMap.remove(msg.source);

        String username = msg.sender; // puede ser null
        if (username != null) {
            userMap.remove(username);
            System.out.println("[WORKER] Usuario desconectado: " + username
                               + " (total: " + userMap.size() + ")");

            // Notificar a todos los que quedan
            String discJson = JsonHelper.toJson(
                "USER_DISCONNECTED", username, null, username
            );
            broadcastAll(discJson);
        } else {
            System.out.println("[WORKER] Conexión sin LOGIN cerrada (total: "
                               + userMap.size() + ")");
        }
    }

    // ----------------------------------------------------------------
    // Helpers de envío
    // ----------------------------------------------------------------

    /** Encola un JSON de salida hacia un cliente concreto. */
    private void send(ClientState state, String json) {
        ByteBuffer buf = JsonHelper.encode(json);
        ioLoop.enqueueWrite(state, buf);
    }

    /** Envía a todos los usuarios conectados. */
    private void broadcastAll(String json) {
        ByteBuffer template = JsonHelper.encode(json);
        for (ClientState state : stateMap.values()) {
            // Duplicar el buffer para que cada canal tenga su propia posición de lectura
            ioLoop.enqueueWrite(state, template.duplicate());
        }
    }

    /** Envía a todos los usuarios conectados EXCEPTO al canal indicado. */
    private void broadcastExcluding(String json, SocketChannel excluded) {
        ByteBuffer template = JsonHelper.encode(json);
        for (Map.Entry<SocketChannel, ClientState> entry : stateMap.entrySet()) {
            if (entry.getKey() != excluded) {
                ioLoop.enqueueWrite(entry.getValue(), template.duplicate());
            }
        }
    }

    /** Construye la lista de usuarios conectados como string separado por comas. */
    private String buildUserList(String excludeUsername) {
        List<String> names = new ArrayList<>();
        for (String name : userMap.keySet()) {
            if (!name.equals(excludeUsername)) {
                names.add(name);
            }
        }
        return String.join(",", names);
    }
}
