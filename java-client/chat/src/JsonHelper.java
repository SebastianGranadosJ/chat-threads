import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JsonHelper.java — serialización y parsing manual del sobre del protocolo, lado cliente.
 *
 * Mismo enfoque que referencia/JsonHelper.java (servidor): sin librerías externas,
 * regex por campo, escape mínimo de \\ y \". El framing de bytes (header 4 bytes
 * big-endian) se maneja en ChatClient, no acá, porque el cliente no usa ByteBuffer
 * ni SocketChannel — trabaja directo sobre los streams del Socket bloqueante.
 */
public class JsonHelper {

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

    /**
     * Parsea un string JSON con el sobre del protocolo y construye un Message.
     * Devuelve null si `type` está ausente o si hay error de parsing.
     */
    public static Message parse(String json) {
        try {
            String type      = extract(P_TYPE,      json);
            String sender    = extract(P_SENDER,    json);
            String recipient = extract(P_RECIPIENT, json);
            String content    = extract(P_CONTENT,   json);
            String timestamp = extract(P_TIMESTAMP, json);

            if (type == null || type.isEmpty()) {
                return null;
            }

            return new Message(
                unescape(type),
                unescape(sender),
                unescape(recipient),
                unescape(content),
                unescape(timestamp)
            );
        } catch (Exception e) {
            return null;
        }
    }

    private static String extract(Pattern p, String json) {
        Matcher m = p.matcher(json);
        if (!m.find()) return null;
        return m.group(1);
    }

    private static String unescape(String s) {
        if (s == null) return null;
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    /**
     * Serializa los 5 campos del sobre a JSON. El cliente nunca completa el
     * timestamp (lo hace el servidor al reenviar, ver protocolo-mensajes-chat.md),
     * así que siempre viaja como null.
     */
    public static String toJson(String type, String sender, String recipient, String content) {
        return "{"
            + "\"type\":"      + quote(type)      + ","
            + "\"sender\":"    + quote(sender)    + ","
            + "\"recipient\":" + quote(recipient) + ","
            + "\"content\":"   + quote(content)   + ","
            + "\"timestamp\":" + quote(null)
            + "}";
    }

    private static String quote(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
