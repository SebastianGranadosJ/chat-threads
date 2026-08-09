# connection.py  —  capa de transporte: envuelve un socket ya conectado.
# Replica el rol de ObjectInputStream / ObjectOutputStream de Java, pero
# usando JSON + struct para delimitar mensajes, ya que TCP no garantiza
# que recv() devuelva exactamente los bytes de un sendall().

import dataclasses
import json
import socket
import struct
from typing import Any, Optional


class Connection:
    """Abstrae la lectura y escritura de objetos Python sobre un socket TCP.

    Protocolo de framing (length-prefixed):
      [ 4 bytes big-endian (longitud del payload) ][ payload JSON UTF-8 ]
    """

    def __init__(self, conn: socket.socket) -> None:
        # Socket ya conectado (resultado de accept() o connect())
        self._socket: socket.socket = conn


    # Helpers internos


    def _recv_exact(self, n: int) -> Optional[bytes]:
        """Lee exactamente `n` bytes del socket, haciendo loop si hace falta.

        Retorna None si la conexión se cierra antes de completar los bytes.
        """
        data = b""
        while len(data) < n:
            chunk = self._socket.recv(n - len(data))
            if not chunk:
                return None          # conexión cerrada por el otro extremo
            data += chunk
        return data


    # API pública


    def write(self, obj: Any) -> bool:
        """Serializa `obj` con JSON y lo envía con un header de 4 bytes."""
        try:
            payload_obj = dataclasses.asdict(obj) if (
                dataclasses.is_dataclass(obj) and not isinstance(obj, type)
            ) else obj
            payload: bytes = json.dumps(payload_obj).encode("utf-8")
            header: bytes = struct.pack("!I", len(payload))
            self._socket.sendall(header + payload)
            return True
        except OSError:
            return False

    def read(self) -> Any:
        """Lee y deserializa el siguiente objeto del stream.

        Retorna None si la conexión falla o se cierra.
        Devuelve tipos básicos (dict, int, str, …); la reconstrucción al
        dataclass específico es responsabilidad del llamador.
        """
        try:
            header = self._recv_exact(4)
            if header is None:
                return None
            length: int = struct.unpack("!I", header)[0]
            payload = self._recv_exact(length)
            if payload is None:
                return None
            return json.loads(payload.decode("utf-8"))
        except (OSError, json.JSONDecodeError, struct.error):
            return None

    def close(self) -> bool:
        """Cierra el socket subyacente."""
        try:
            self._socket.close()
            return True
        except OSError:
            return False
