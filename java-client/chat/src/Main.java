import javax.swing.SwingUtilities;

/** Main.java — punto de entrada: abre la pantalla de login. */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
