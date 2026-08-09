# client_socket_factory.py  —  capa de transporte: fábrica del socket cliente.
# Replica JavaClientSocket: crea el socket y lo conecta al servidor.
# Equivalente al Socket(HOST, PORT) de Java.

import socket
from typing import Optional

HOST: str = "127.0.0.1"
PORT: int = 1802


class ClientSocketFactory:
    """Crea y conecta el socket TCP del cliente ATM."""

    def get(self) -> Optional[socket.socket]:
        """Crea el socket y lo conecta al servidor ATM.

        Returns:
            Socket ya conectado, o None si no se pudo establecer la conexión.
        """
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.connect((HOST, PORT))
            return sock
        except OSError as exc:
            print(f"[ERROR] No se pudo conectar al servidor ATM: {exc}")
            return None
