# server.py  —  capa de transporte: implementación concreta del proceso servidor.
# Usa una Connection para leer/escribir objetos sobre el socket ya conectado.
# Replica JavaServerSocket: bind() acepta la conexión, listen() lee la
# petición, response() envía la respuesta, close() libera el socket del cliente.

import socket
from typing import Optional

from connection import Connection
from socket_process import SocketProcess


class Server(SocketProcess):
    """Gestiona la comunicación TCP con un cliente por operación.

    El socket de escucha (`server_socket`) es creado por ServerSocketFactory
    y compartido entre iteraciones del loop; cada llamada a bind() acepta
    una nueva conexión y crea una Connection independiente para esa operación.
    """

    def __init__(self, server_socket: socket.socket) -> None:
        # Socket de escucha (ya con bind+listen), listo para accept()
        self._server_socket: socket.socket = server_socket # socket de escucha
        # Connection del cliente actualmente conectado (None entre operaciones)
        self._connection: Optional[Connection] = None

    # ------------------------------------------------------------------
    # Implementación de SocketProcess
    # ------------------------------------------------------------------

    def bind(self) -> bool:
        """Bloquea hasta recibir una conexión y crea la Connection para ella."""
        try:
            conn, addr = self._server_socket.accept() # Acepta la conexion, se queda escuchando hasta que llegie
            # Recibe un socket nuevo para esa conexion (conn) y addr que es una tupla con el ip y puerto
            self._connection = Connection(conn)
            print(f"[CONEXIÓN] Cliente conectado desde {addr[0]}:{addr[1]}")
            return True
        except socket.timeout:
            # No llegó ninguna conexión en este intervalo; se reintenta
            # en la siguiente vuelta del loop sin imprimir error.
            return False
        except OSError as exc:
            print(f"[ERROR] Fallo al aceptar conexión: {exc}")
            return False

    def listen(self) -> list:
        """Lee objetos en loop hasta recibir el marcador entero 0.

        El marcador NO se incluye en la lista devuelta.
        
        Escucha lo que se manda por la conexion para recibir objetos
        """
        result: list = []
        if self._connection is None:
            return result
        while True:
            obj = self._connection.read()
            if obj is None or obj == 0: # Hasta que aparezca el 0
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
        # Marcador de fin de transmisión , marca donde terminan los objetos
        self._connection.write(0)
        return True

    def close(self) -> bool:
        """Cierra la conexión con el cliente actual y libera el recurso."""
        if self._connection is None:
            return False
        ok = self._connection.close()
        self._connection = None
        return ok
