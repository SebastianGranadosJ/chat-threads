# client.py  —  capa de transporte: implementación concreta del proceso cliente.
# Usa ClientSocketFactory para crear la conexión y Connection para leer/escribir
# objetos.  Cada instancia gestiona UNA conexión (una operación ATM).
# Replica JavaClientSocket: connect, listen, response, close.

from typing import Optional

from client_socket_factory import ClientSocketFactory
from connection import Connection
from socket_process import SocketProcess


class Client(SocketProcess):
    """Gestiona la comunicación TCP con el servidor para una operación.

    Ciclo de vida esperado:
        connect() -> response([request]) -> listen() -> close()
    """

    def __init__(self) -> None:
        # Connection se asigna en connect(); None indica que no hay conexión activa
        self._connection: Optional[Connection] = None

    # ------------------------------------------------------------------
    # Implementación de SocketProcess
    # ------------------------------------------------------------------

    def connect(self) -> bool:
        """Crea el socket mediante la fábrica y lo envuelve en una Connection."""
        sock = ClientSocketFactory().get()
        if sock is None:
            return False
        self._connection = Connection(sock)
        return True

    def listen(self) -> list:
        """Lee objetos en loop hasta recibir el marcador entero 0.

        El marcador NO se incluye en la lista devuelta.
        """
        result: list = []
        if self._connection is None:
            return result
        while True:
            obj = self._connection.read()
            if obj is None or obj == 0:
                break
            result.append(obj)
        return result

    def response(self, data: list) -> bool:
        """Escribe cada objeto de `data` y finaliza con el marcador 0."""
        if self._connection is None:
            return False
        for item in data:
            if not self._connection.write(item):
                return False
        # Marcador de fin de transmisión (equivalente al flag=0 de Java)
        self._connection.write(0)
        return True

    def close(self) -> bool:
        """Cierra la conexión y libera el socket."""
        if self._connection is None:
            return False
        ok = self._connection.close()
        self._connection = None
        return ok
