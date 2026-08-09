import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.io.IOException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * LoginFrame.java — pantalla de login (ver referencia/diseno-interfaz-grafica.md, sección 1).
 *
 * Campo de usuario + botón "Conectar" que manda LOGIN. No avanza a la ventana
 * principal hasta recibir LOGIN_OK; si llega LOGIN_ERROR, muestra el motivo y
 * se queda en esta pantalla.
 */
public class LoginFrame extends JFrame {

    private final JTextField hostField;
    private final JTextField usernameField;
    private final JButton connectButton;
    private final JLabel statusLabel;

    private ChatClient client;

    public LoginFrame() {
        super("Chat — Conectar");

        JPanel form = new JPanel(new GridLayout(2, 2, 8, 8));
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        hostField = new JTextField("localhost");
        usernameField = new JTextField();

        form.add(new JLabel("Servidor:"));
        form.add(hostField);
        form.add(new JLabel("Usuario:"));
        form.add(usernameField);

        connectButton = new JButton("Conectar");
        statusLabel = new JLabel(" ");
        statusLabel.setForeground(java.awt.Color.RED);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        bottom.add(connectButton, BorderLayout.NORTH);
        bottom.add(statusLabel, BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        connectButton.addActionListener(e -> onConnect());
        usernameField.addActionListener(e -> onConnect());

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(360, 180);
        setLocationRelativeTo(null);
    }

    private void onConnect() {
        String host = hostField.getText().trim();
        String username = usernameField.getText().trim();

        if (host.isEmpty()) host = "localhost";
        if (username.isEmpty()) {
            statusLabel.setText("Ingresá un nombre de usuario.");
            return;
        }

        connectButton.setEnabled(false);
        statusLabel.setText("Conectando...");

        final String finalUsername = username;
        client = new ChatClient();
        try {
            client.connect(host, 5000);
        } catch (IOException ex) {
            statusLabel.setText("No se pudo conectar: " + ex.getMessage());
            connectButton.setEnabled(true);
            return;
        }

        client.setHandler(msg -> handleLoginResponse(msg, finalUsername));
        client.setDisconnectHandler(this::handleDisconnectDuringLogin);

        try {
            client.login(finalUsername);
        } catch (IOException ex) {
            statusLabel.setText("Error enviando LOGIN: " + ex.getMessage());
            connectButton.setEnabled(true);
        }
    }

    private void handleLoginResponse(Message msg, String username) {
        switch (msg.type) {
            case "LOGIN_OK":
                dispose();
                new MainChatFrame(client, username).setVisible(true);
                break;
            case "LOGIN_ERROR":
                statusLabel.setText(msg.content != null ? msg.content : "Login rechazado por el servidor.");
                connectButton.setEnabled(true);
                client.close();
                break;
            default:
                // Cualquier otro mensaje antes de LOGIN_OK se ignora en esta pantalla.
        }
    }

    private void handleDisconnectDuringLogin() {
        statusLabel.setText("Se perdió la conexión con el servidor.");
        connectButton.setEnabled(true);
    }
}
