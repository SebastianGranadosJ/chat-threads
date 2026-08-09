# main.py  —  punto de entrada del cliente de chat.
# Arranca la pantalla de login; el resto del flujo lo maneja ChatApp.

from app import ChatApp


def main() -> None:
    app = ChatApp()
    app.root.mainloop()


if __name__ == "__main__":
    main()
