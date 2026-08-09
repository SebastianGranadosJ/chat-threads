import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import javax.swing.SwingUtilities;

/**
 * ChatClient.java — capa de comunicación del cliente.
 *
 * Socket bloqueante estándar (java.net.Socket) — nada de Selector/java.nio acá,
 * a diferencia del servidor (ver referencia/reutilizacion-modulo-atm.md: el
 * cliente siempre tiene exactamente una conexión, no necesita el modelo no
 * bloqueante).
 *
 * El envío (send) ocurre siempre desde el hilo de la GUI (clicks de botón).
 * La recepción corre en un hilo dedicado ("listener") que hace lectura
 * bloqueante en loop; cada mensaje completo se entrega al handler registrado
 * mediante SwingUtilities.invokeLater(), para nunca tocar componentes Swing
 * desde el hilo listener.
 */
public class ChatClient {

    public interface MessageHandler {
        void onMessage(Message msg);
    }

    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private Thread listenerThread;

    private volatile boolean running = false;
    private volatile MessageHandler handler;
    private volatile Runnable disconnectHandler;

    /** Conecta al servidor y arranca el hilo listener. */
    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);
        in  = socket.getInputStream();
        out = socket.getOutputStream();
        running = true;

        listenerThread = new Thread(this::listenLoop, "listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    /** Reemplaza el handler activo (ej. al pasar de la pantalla de login a la principal). */
    public void setHandler(MessageHandler h) {
        this.handler = h;
    }

    /** Se invoca (en el EDT) si el servidor cierra la conexión inesperadamente o hay error de I/O. */
    public void setDisconnectHandler(Runnable r) {
        this.disconnectHandler = r;
    }

    public void login(String username) throws IOException {
        send(new Message("LOGIN", username, null, "", null));
    }

    public void sendGroupMessage(String sender, String content) throws IOException {
        send(new Message("GROUP_MESSAGE", sender, null, content, null));
    }

    public void sendPrivateMessage(String sender, String recipient, String content) throws IOException {
        send(new Message("PRIVATE_MESSAGE", sender, recipient, content, null));
    }

    private void send(Message msg) throws IOException {
        String json = JsonHelper.toJson(msg.type, msg.sender, msg.recipient, msg.content);
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        byte[] header  = intToBytesBE(payload.length);
        out.write(header);
        out.write(payload);
        out.flush();
    }

    // ----------------------------------------------------------------
    // Hilo listener
    // ----------------------------------------------------------------

    private void listenLoop() {
        try {
            while (running) {
                Message msg = receiveMessage();
                final Message m = msg;
                SwingUtilities.invokeLater(() -> {
                    MessageHandler h = handler;
                    if (h != null) h.onMessage(m);
                });
            }
        } catch (IOException e) {
            running = false;
            SwingUtilities.invokeLater(() -> {
                Runnable r = disconnectHandler;
                if (r != null) r.run();
            });
        }
    }

    /** Lee un mensaje completo (header 4 bytes + payload JSON), bloqueante. */
    private Message receiveMessage() throws IOException {
        byte[] header = readFully(4);
        if (header == null) {
            throw new EOFException("El servidor cerró la conexión");
        }
        int len = bytesToIntBE(header);

        byte[] payload = readFully(len);
        if (payload == null) {
            throw new EOFException("El servidor cerró la conexión");
        }

        String json = new String(payload, StandardCharsets.UTF_8);
        Message msg = JsonHelper.parse(json);
        if (msg == null) {
            throw new IOException("Mensaje malformado recibido del servidor");
        }
        return msg;
    }

    /**
     * Lee exactamente n bytes del stream. Devuelve null si el stream llega a EOF
     * justo al principio (cierre limpio entre mensajes); lanza IOException si el
     * EOF ocurre a mitad de un mensaje (cierre abrupto).
     */
    private byte[] readFully(int n) throws IOException {
        byte[] buf = new byte[n];
        int total = 0;
        while (total < n) {
            int r = in.read(buf, total, n - total);
            if (r == -1) {
                if (total == 0) return null;
                throw new IOException("Conexión cerrada a mitad de un mensaje");
            }
            total += r;
        }
        return buf;
    }

    public void close() {
        running = false;
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
    }

    private static byte[] intToBytesBE(int v) {
        return new byte[] {
            (byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v
        };
    }

    private static int bytesToIntBE(byte[] b) {
        return ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16)
             | ((b[2] & 0xFF) << 8)  | (b[3] & 0xFF);
    }
}
