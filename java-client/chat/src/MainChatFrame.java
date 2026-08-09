import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * MainChatFrame.java — ventana principal: chat grupal + lista de usuarios
 * (ver referencia/diseno-interfaz-grafica.md, sección 2).
 *
 * Mantiene el mapa de ventanas de chat privado abiertas (contacto -> ventana).
 * Ese mapa lo puede tocar tanto una acción de la GUI (doble clic) como el
 * handler de mensajes entrantes que dispara el hilo listener — aunque ambos
 * accesos terminan ejecutándose en el EDT (ChatClient siempre marshalla con
 * invokeLater antes de llamar al handler), se protege igual con `synchronized`
 * sobre el propio mapa, construido a mano (HashMap), sin colecciones
 * concurrentes de la librería — tal como pide el enunciado.
 */
public class MainChatFrame extends JFrame {

    private final ChatClient client;
    private final String username;

    private final JTextArea groupLog;
    private final JTextField messageField;
    private final DefaultListModel<String> userListModel;
    private final JList<String> userList;

    private final Map<String, PrivateChatFrame> privateWindows = new HashMap<>();

    public MainChatFrame(ChatClient client, String username) {
        super(username);
        this.client = client;
        this.username = username;

        groupLog = new JTextArea();
        groupLog.setEditable(false);
        groupLog.setLineWrap(true);
        groupLog.setWrapStyleWord(true);
        UiTheme.styleTextArea(groupLog);

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setPreferredSize(new Dimension(150, 0));
        userList.setBackground(UiTheme.PANEL);
        userList.setSelectionBackground(UiTheme.ACCENT);
        userList.setSelectionForeground(UiTheme.TEXT_ON_ACCENT);

        messageField = new JTextField();
        JButton sendButton = new JButton("Enviar");
        UiTheme.styleButton(sendButton);

        JPanel bottom = new JPanel(new BorderLayout(6, 0));
        bottom.setBackground(UiTheme.BACKGROUND);
        bottom.add(messageField, BorderLayout.CENTER);
        bottom.add(sendButton, BorderLayout.EAST);

        JSplitPane split = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            new JScrollPane(groupLog),
            new JScrollPane(userList)
        );
        split.setResizeWeight(0.8);
        split.setBackground(UiTheme.BACKGROUND);

        setLayout(new BorderLayout());
        getContentPane().setBackground(UiTheme.BACKGROUND);
        add(split, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        sendButton.addActionListener(e -> sendGroupMessage());
        messageField.addActionListener(e -> sendGroupMessage());

        userList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String contact = userList.getSelectedValue();
                    if (contact != null && !contact.equals(username)) {
                        openPrivateChat(contact);
                    }
                }
            }
        });

        client.setHandler(this::onMessage);
        client.setDisconnectHandler(this::onDisconnected);

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                client.close();
                System.exit(0);
            }
        });

        setSize(640, 420);
        setLocationRelativeTo(null);
    }

    private void sendGroupMessage() {
        String text = messageField.getText().trim();
        if (text.isEmpty()) return;
        try {
            // No se agrega localmente: el servidor hace eco del propio GROUP_MESSAGE
            // al emisor (ver referencia/protocolo-mensajes-chat.md); se espera ese eco.
            client.sendGroupMessage(username, text);
            messageField.setText("");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Error enviando mensaje: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Maneja cualquier mensaje entrante. Se ejecuta en el EDT (ver ChatClient). */
    private void onMessage(Message msg) {
        switch (msg.type) {
            case "GROUP_MESSAGE":
                groupLog.append(msg.sender + ": " + msg.content + "\n");
                break;

            case "PRIVATE_MESSAGE":
                routePrivateMessage(msg);
                break;

            case "USER_LIST":
                populateUserList(msg.content);
                break;

            case "USER_CONNECTED":
                if (msg.sender != null && !userListModel.contains(msg.sender)) {
                    userListModel.addElement(msg.sender);
                }
                break;

            case "USER_DISCONNECTED":
                if (msg.sender != null) {
                    userListModel.removeElement(msg.sender);
                }
                break;

            case "ERROR":
                JOptionPane.showMessageDialog(this,
                    msg.content != null ? msg.content : "Error del servidor",
                    "Error", JOptionPane.ERROR_MESSAGE);
                break;

            default:
                // Tipo desconocido: se ignora.
        }
    }

    private void routePrivateMessage(Message msg) {
        // Si el sender soy yo, es el eco de un mensaje que mandé: la otra punta
        // de la conversación es el recipient. Si no, la otra punta es el sender.
        String contact = username.equals(msg.sender) ? msg.recipient : msg.sender;
        if (contact == null) return;

        PrivateChatFrame win = getOrCreatePrivateChat(contact);
        win.appendMessage(msg);
    }

    private void populateUserList(String content) {
        userListModel.clear();
        if (content != null && !content.isEmpty()) {
            for (String name : content.split(",")) {
                String trimmed = name.trim();
                if (!trimmed.isEmpty() && !userListModel.contains(trimmed)) {
                    userListModel.addElement(trimmed);
                }
            }
        }
        // El servidor puede o no incluirme a mí mismo en USER_LIST; me aseguro
        // de aparecer igual, ya que la lista debe mostrar también al propio usuario.
        if (!userListModel.contains(username)) {
            userListModel.addElement(username);
        }
    }

    private void openPrivateChat(String contact) {
        PrivateChatFrame win = getOrCreatePrivateChat(contact);
        win.setVisible(true);
        win.toFront();
        win.requestFocus();
    }

    private PrivateChatFrame getOrCreatePrivateChat(String contact) {
        synchronized (privateWindows) {
            PrivateChatFrame win = privateWindows.get(contact);
            if (win == null) {
                win = new PrivateChatFrame(client, username, contact, this::onPrivateChatClosed);
                privateWindows.put(contact, win);
                win.setVisible(true);
            }
            return win;
        }
    }

    private void onPrivateChatClosed(String contact) {
        synchronized (privateWindows) {
            privateWindows.remove(contact);
        }
    }

    private void onDisconnected() {
        JOptionPane.showMessageDialog(this,
            "Se perdió la conexión con el servidor.",
            "Desconectado", JOptionPane.ERROR_MESSAGE);

        synchronized (privateWindows) {
            for (PrivateChatFrame win : privateWindows.values()) {
                win.dispose();
            }
            privateWindows.clear();
        }

        dispose();
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
