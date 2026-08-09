# socket_process.py  —  capa de transporte: contrato del proceso cliente.
# ABC que define las operaciones del lado cliente sobre el socket,
# replicando la interfaz SocketProcess del ejemplo Java del profesor:
# connect, listen, response, close.

from abc import ABC, abstractmethod


class SocketProcess(ABC):
    """Interfaz abstracta del proceso socket en el lado cliente."""

    @abstractmethod
    def connect(self) -> bool:
        """Establece la conexión TCP con el servidor ATM.

        Returns:
            True si la conexión fue exitosa.
        """
        ...

    @abstractmethod
    def listen(self) -> list:
        """Lee objetos del socket hasta recibir el marcador de fin (0).

        Returns:
            Lista con los objetos recibidos (sin incluir el marcador).
        """
        ...

    @abstractmethod
    def response(self, data: list) -> bool:
        """Envía todos los objetos de `data` seguidos del marcador de fin (0).

        Returns:
            True si todos los objetos se enviaron correctamente.
        """
        ...

    @abstractmethod
    def close(self) -> bool:
        """Cierra la conexión con el servidor.

        Returns:
            True si se cerró sin errores.
        """
        ...
