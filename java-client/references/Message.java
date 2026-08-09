import java.nio.channels.SocketChannel;

/**
 * Message.java — modelo de datos del sobre del protocolo.
 *
 * Representa un mensaje tal como fluye por el servidor: contiene los 5 campos
 * del sobre JSON (type, sender, recipient, content, timestamp) más referencias
 * al canal y al estado de la conexión que lo originó, para que el WorkerThread
 * pueda operar sin búsquedas inversas adicionales.
 *
 * Inmutable una vez construido. Los campos pueden ser null según el tipo:
 *   - recipient: null en mensajes grupales o de control.
 *   - sender:    null en mensajes sintéticos internos (ej. desconexión abrupta).
 *   - timestamp: el servidor lo sobreescribe al reenviar.
 */
public class Message {

    public final String type;
    public final String sender;
    public final String recipient;
    public final String content;
    public final String timestamp;

    /**
     * Canal TCP que produjo este mensaje.
     * El hilo de I/O lo usa para saber de dónde vino;
     * el worker lo usa para indexar/buscar en su stateMap.
     */
    public final SocketChannel source;

    /**
     * Estado de conexión asociado al canal origen.
     * Permite al worker encolar respuestas hacia el remitente sin mantener un
     * map paralelo Channel→ClientState en el propio WorkerThread.
     */
    public final ClientState sourceState;

    public Message(String type, String sender, String recipient,
                   String content, String timestamp,
                   SocketChannel source, ClientState sourceState) {
        this.type        = type;
        this.sender      = sender;
        this.recipient   = recipient;
        this.content     = content;
        this.timestamp   = timestamp;
        this.source      = source;
        this.sourceState = sourceState;
    }

    @Override
    public String toString() {
        return "Message{type=" + type + ", sender=" + sender
             + ", recipient=" + recipient + "}";
    }
}
