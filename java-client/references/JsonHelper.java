import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JsonHelper.java — serialización y parsing manual del sobre del protocolo.
 *
 * El sobre tiene siempre exactamente 5 campos conocidos:
 *   {"type":"...","sender":"...","recipient":"...","content":"...","timestamp":"..."}
 *
 * Dado que el formato es fijo y pequeño, no se usa ninguna librería externa
 * (Gson, Jackson, org.json). Esto evita configurar classpath/build tool y
 * cumple la restricción del enunciado.
 *
 * Parsing: expresiones regulares por campo, soportando valores string o null.
 * Serialización: concatenación de strings con escape mínimo de los caracteres
 *   especiales JSON (\\ y \").
 * Framing: encode() produce [4 bytes big-endian longitud][N bytes UTF-8 payload],
 *   mismo protocolo que connection.py del ATM de referencia.
 */
public class JsonHelper {

    // ----------------------------------------------------------------
    // Patrones de extracción (compilados una sola vez — son caros)
    // ----------------------------------------------------------------

    // Captura "campo":"valor" o "campo":null.
    // Grupo 1 = el valor string; null si el campo es JSON null.
    // El inner group [^"\\\\]|\\\\.  captura cualquier char excepto " y \,
    // o bien una secuencia de escape \X.
    private static final Pattern P_TYPE      = fieldPat("type");
    private static final Pattern P_SENDER    = fieldPat("sender");
    private static final Pattern P_RECIPIENT = fieldPat("recipient");
    private static final Pattern P_CONTENT   = fieldPat("content");
    private static final Pattern P_TIMESTAMP = fieldPat("timestamp");

    private static Pattern fieldPat(String name) {
        return Pattern.compile(
            "\"" + name + "\"\\s*:\\s*(?:\"((?:[^\"\\\\]|\\\\.)*)\"|null)"
        );
    }

    // ----------------------------------------------------------------
    // Parsing
    // ----------------------------------------------------------------

    /**
     * Parsea un string JSON con el sobre del protocolo y construye un Message.
     *
     * @param source canal que envió el mensaje (se guarda en Message.source)
     * @param state  estado de la conexión (se guarda en Message.sourceState)
     * @param json   el string JSON a parsear
     * @return el Message construido, o null si `type` está ausente o hay error
     */
    public static Message parse(SocketChannel source, ClientState state, String json) {
        try {
            String type      = extract(P_TYPE,      json);
            String sender    = extract(P_SENDER,    json);
            String recipient = extract(P_RECIPIENT, json);
            String content   = extract(P_CONTENT,   json);
            String timestamp = extract(P_TIMESTAMP, json);

            if (type == null || type.isEmpty()) {
                return null;    // `type` es obligatorio
            }

            // Unescape los valores string: \\→\ y \"→"
            return new Message(
                unescape(type),
                unescape(sender),
                unescape(recipient),
                unescape(content),
                unescape(timestamp),
                source,
                state
            );
        } catch (Exception e) {
            return null;    // cualquier fallo de parsing se trata como JSON malformado
        }
    }

    /** Extrae el valor de un campo; devuelve null si el campo es null o no aparece. */
    private static String extract(Pattern p, String json) {
        Matcher m = p.matcher(json);
        if (!m.find()) return null;
        return m.group(1);   // null si la alternativa `null` fue la que hizo match
    }

    /** Reversa del escaping JSON: \\→\ y \"→" */
    private static String unescape(String s) {
        if (s == null) return null;
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    // ----------------------------------------------------------------
    // Serialización
    // ----------------------------------------------------------------

    /**
     * Serializa los 5 campos del sobre a JSON. El timestamp se genera en este
     * momento (hora del servidor en ISO-8601 UTC), según el protocolo, para
     * evitar depender de los relojes locales de los clientes.
     */
    public static String toJson(String type, String sender, String recipient,
                                String content) {
        return buildJson(type, sender, recipient, content, Instant.now().toString());
    }

    /**
     * Versión con timestamp explícito (usado en USER_LIST y mensajes de control
     * donde el contenido ya incluye la lista o el timestamp es irrelevante).
     */
    public static String toJson(String type, String sender, String recipient,
                                String content, String timestamp) {
        return buildJson(type, sender, recipient, content, timestamp);
    }

    private static String buildJson(String type, String sender, String recipient,
                                    String content, String timestamp) {
        return "{"
            + "\"type\":"      + quote(type)      + ","
            + "\"sender\":"    + quote(sender)    + ","
            + "\"recipient\":" + quote(recipient) + ","
            + "\"content\":"   + quote(content)   + ","
            + "\"timestamp\":" + quote(timestamp)
            + "}";
    }

    /** Devuelve el valor como string JSON: "valor" con escapes, o el literal null. */
    private static String quote(String s) {
        if (s == null) return "null";
        // Escape: \ primero (para no re-escapar los que agregamos), luego "
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ----------------------------------------------------------------
    // Framing: encode  [4 bytes big-endian long][N bytes UTF-8]
    // ----------------------------------------------------------------

    /**
     * Codifica un string JSON en un ByteBuffer con el framing del protocolo:
     *   [ 4 bytes: longitud del payload en big-endian ][ N bytes: payload UTF-8 ]
     *
     * El ByteBuffer devuelto ya está en flip() y listo para channel.write().
     * Mismo protocolo que connection.py: struct.pack("!I", len) + payload.
     */
    public static ByteBuffer encode(String json) {
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        // ByteBuffer.putInt escribe en big-endian por defecto (order = BIG_ENDIAN)
        ByteBuffer buf = ByteBuffer.allocate(4 + payload.length);
        buf.putInt(payload.length);     // header: 4 bytes big-endian
        buf.put(payload);               // payload: N bytes UTF-8
        buf.flip();                     // prepara para lectura (limit=pos, pos=0)
        return buf;
    }
}
