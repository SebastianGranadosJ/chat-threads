# client_socket_factory.py  —  capa de transporte: fábrica del socket cliente.
# Adaptado de referencias/atm_client/client_socket_factory.py: host configurable
# (en vez de fijo) y puerto 5000, el del servidor de chat.

import socket
from typing import Optional

DEFAULT_HOST: str = "localhost"
PORT: int = 5000


class ClientSocketFactory:
    """Crea y conecta el socket TCP del cliente de chat."""

    def get(self, host: str = DEFAULT_HOST, port: int = PORT) -> Optional[socket.socket]:
        """Crea el socket y lo conecta al servidor de chat.

        Returns:
            Socket ya conectado, o None si no se pudo establecer la conexión.
        """
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.connect((host, port))
            return sock
        except OSError as exc:
            print(f"[ERROR] No se pudo conectar al servidor de chat: {exc}")
            return None
