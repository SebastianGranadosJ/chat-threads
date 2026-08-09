# chat_client.py  —  capa de transporte: proceso cliente de chat.
#
# A diferencia de client.py del ATM (una conexión = una operación, con marcador
# de fin de transmisión), el chat usa una conexión persistente por cliente
# (ver referencias/reutilizacion-modulo-atm.md): se conecta una vez y después
# se leen/escriben mensajes sueltos, uno por uno, mientras dure la sesión.

from typing import Any, Optional

from client_socket_factory import ClientSocketFactory, DEFAULT_HOST, PORT
from connection import Connection


class ChatClient:
    """Gestiona la conexión TCP persistente con el servidor de chat."""

    def __init__(self) -> None:
        self._connection: Optional[Connection] = None

    def connect(self, host: str = DEFAULT_HOST, port: int = PORT) -> bool:
        """Crea el socket mediante la fábrica y lo envuelve en una Connection."""
        sock = ClientSocketFactory().get(host, port)
        if sock is None:
            return False
        self._connection = Connection(sock)
        return True

    def send(self, message: Any) -> bool:
        """Envía un único mensaje (dataclass o dict) al servidor."""
        if self._connection is None:
            return False
        return self._connection.write(message)

    def receive(self) -> Any:
        """Lee el próximo mensaje del socket (bloqueante).

        Retorna None si el servidor cerró la conexión o hubo un error.
        Pensado para ser llamado en loop desde el hilo listener.
        """
        if self._connection is None:
            return None
        return self._connection.read()

    def close(self) -> None:
        """Cierra la conexión con el servidor, si hay una activa."""
        if self._connection is not None:
            self._connection.close()
            self._connection = None
