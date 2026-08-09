# listener.py  —  hilo dedicado a la lectura bloqueante del socket.
#
# Ver referencias/reutilizacion-modulo-atm.md y diseno-interfaz-grafica.md:
# el cliente de chat mantiene UNA conexión persistente, y necesita poder
# recibir mensajes empujados por el servidor en cualquier momento sin
# congelar la GUI. Este hilo hace recv() bloqueante en loop; nunca toca
# widgets de Tkinter directamente — solo invoca los callbacks que recibe,
# y es responsabilidad de quien los registra pasar al hilo principal
# (típicamente con root.after()).

import threading
from typing import Callable

from chat_client import ChatClient


class ListenerThread(threading.Thread):
    """Hilo que lee mensajes del socket en loop hasta que se cierra la conexión."""

    def __init__(
        self,
        chat_client: ChatClient,
        on_message: Callable[[dict], None],
        on_disconnect: Callable[[], None],
    ) -> None:
        super().__init__(daemon=True)
        self._chat_client = chat_client
        self._on_message = on_message
        self._on_disconnect = on_disconnect
        self._stopped = False

    def run(self) -> None:
        while not self._stopped:
            msg = self._chat_client.receive()
            if self._stopped:
                break
            if msg is None:
                self._on_disconnect()
                break
            self._on_message(msg)

    def stop(self) -> None:
        """Marca el hilo para detenerse. No interrumpe un recv() ya bloqueado;
        eso ocurre naturalmente cuando se cierra el socket (chat_client.close())."""
        self._stopped = True
