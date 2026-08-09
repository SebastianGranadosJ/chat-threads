import java.awt.Color;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.border.LineBorder;

/**
 * UiTheme.java — paleta y helpers de estilo compartidos por las 3 pantallas
 * (login, chat grupal, chat privado), para que la interfaz se vea consistente
 * en verde en todas ellas.
 */
public final class UiTheme {

    public static final Color BACKGROUND = new Color(0xE8, 0xF5, 0xE9);
    public static final Color PANEL      = new Color(0xC8, 0xE6, 0xC9);
    public static final Color BORDER     = new Color(0x66, 0xBB, 0x6A);
    public static final Color ACCENT     = new Color(0x2E, 0x7D, 0x32);
    public static final Color TEXT_ON_ACCENT = Color.WHITE;

    private UiTheme() {
    }

    public static void styleButton(JButton button) {
        button.setBackground(ACCENT);
        button.setForeground(TEXT_ON_ACCENT);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setBorderPainted(false);
    }

    public static void styleTextArea(JComponent area) {
        area.setBackground(Color.WHITE);
        area.setBorder(new LineBorder(BORDER, 1));
    }
}
