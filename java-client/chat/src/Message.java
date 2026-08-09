/**
 * Message.java — modelo de datos del sobre del protocolo, lado cliente.
 *
 * Mismos 5 campos que el sobre JSON del servidor (ver referencia/Message.java),
 * sin las referencias a canal/estado que solo tienen sentido del lado servidor.
 * Inmutable una vez construido.
 */
public class Message {

    public final String type;
    public final String sender;
    public final String recipient;
    public final String content;
    public final String timestamp;

    public Message(String type, String sender, String recipient,
                   String content, String timestamp) {
        this.type      = type;
        this.sender    = sender;
        this.recipient = recipient;
        this.content   = content;
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "Message{type=" + type + ", sender=" + sender
             + ", recipient=" + recipient + ", content=" + content + "}";
    }
}
