# socket_process.py  —  capa de transporte: contrato del proceso servidor.
# ABC que define las operaciones del lado servidor sobre el socket,
# replicando la interfaz SocketProcess del ejemplo Java del profesor:
# bind (accept), listen, response, close.

from abc import ABC, abstractmethod


class SocketProcess(ABC):
    """Interfaz abstracta del proceso socket en el lado servidor."""

    @abstractmethod
    def bind(self) -> bool:
        """acepta la siguiente conexión entrante de un cliente.

        Returns:
            True si se aceptó la conexión correctamente.
        """
        ...

    @abstractmethod
    def listen(self) -> list:
        """lee objetos del socket hasta recibir el marcador de fin (0).

        Returns:
            Lista con los objetos recibidos (sin incluir el marcador).
        """
        ...

    @abstractmethod
    def response(self, data: list) -> bool:
        """envia todos los objetos de `data` seguidos del marcador de fin (0).

        Returns:
            True si todos los objetos se enviaron correctamente.
        """
        ...

    @abstractmethod
    def close(self) -> bool:
        """cierra la conexión activa con el cliente actual.

        Returns:
            True si se cerró sin errores.
        """
        ...
