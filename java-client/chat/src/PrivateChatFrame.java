import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.IOException;
import java.util.function.Consumer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 * PrivateChatFrame.java — ventana de chat privado con un único contacto
 * (ver referencia/diseno-interfaz-grafica.md, sección 3).
 *
 * Mismo layout que la ventana principal (log + lista + campo + enviar), pero
 * acotada a la conversación con `contact`: el panel derecho solo lista a esos
 * dos usuarios (no a todos los conectados) y el campo de texto siempre manda
 * PRIVATE_MESSAGE con recipient fijo a `contact`.
 */
public class PrivateChatFrame extends JFrame {

    private final ChatClient client;
    private final String username;
    private final String contact;
    private final Consumer<String> onClosed;

    private final JTextArea log;
    private final JTextField messageField;

    public PrivateChatFrame(ChatClient client, String username, String contact,
                             Consumer<String> onClosed) {
        super(username + " — chat privado con " + contact);
        this.client = client;
        this.username = username;
        this.contact = contact;
        this.onClosed = onClosed;

        log = new JTextArea();
        log.setEditable(false);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);

        DefaultListModel<String> listModel = new DefaultListModel<>();
        listModel.addElement(username);
        listModel.addElement(contact);
        JList<String> userList = new JList<>(listModel);
        userList.setPreferredSize(new Dimension(150, 0));

        messageField = new JTextField();
        JButton sendButton = new JButton("Enviar");

        JPanel bottom = new JPanel(new BorderLayout(6, 0));
        bottom.add(messageField, BorderLayout.CENTER);
        bottom.add(sendButton, BorderLayout.EAST);

        JSplitPane split = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            new JScrollPane(log),
            new JScrollPane(userList)
        );
        split.setResizeWeight(0.8);

        setLayout(new BorderLayout());
        add(split, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        sendButton.addActionListener(e -> sendPrivateMessage());
        messageField.addActionListener(e -> sendPrivateMessage());

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                onClosed.accept(contact);
            }
        });

        setSize(480, 360);
        setLocationRelativeTo(null);
    }

    private void sendPrivateMessage() {
        String text = messageField.getText().trim();
        if (text.isEmpty()) return;
        try {
            // No se agrega localmente: el servidor hace eco del propio PRIVATE_MESSAGE
            // al emisor (ver referencia/protocolo-mensajes-chat.md); se espera ese eco.
            client.sendPrivateMessage(username, contact, text);
            messageField.setText("");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Error enviando mensaje: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Llamado por MainChatFrame cuando llega un PRIVATE_MESSAGE de/hacia este contacto. */
    public void appendMessage(Message msg) {
        log.append(msg.sender + ": " + msg.content + "\n");
    }
}
