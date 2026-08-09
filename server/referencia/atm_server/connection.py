# connection.py  —  capa de transporte: envuelve un socket ya conectado.
# Petmite la escritura y lectura de objetos

import dataclasses
import json
import socket
import struct
from typing import Any, Optional


class Connection:
    """Abstrae la lectura y escritura de objetos Python sobre un socket TCP.

    Protocolo de framing (length-prefixed):
      [ 4 bytes: cuántos bytes vienen ][ N bytes: el JSON en sí ]
    Framing permite saber de donde a donde va el mensaje en la fila de bytes que vamosa  mandar o recibir
    Los primeros 4 bytes son para decir cuantos bytes vienen y lo N bytes son la data en si
    
    Big edian mas importante 300 va el 3 primero
      
    """
    # Recibe el socket por el socket factory
    def __init__(self, conn: socket.socket) -> None:
        # Socket ya conectado (resultado de accept() o connect())
        self._socket: socket.socket = conn


    # Helpers internos


    def _recv_exact(self, n: int) -> Optional[bytes]:
        """Lee exactamente `n` bytes del socket, haciendo loop si hace falta.

        Retorna None si la conexión se cierra antes de completar los bytes.
        
        Esto sirve pq no siempre la conexion manda todos los bytes que son, puede demorar
        entonces este se queda esperando
        """
        data = b"" # Sintaxis para recibir literal Bytes (Contiene bytes)
        while len(data) < n:
            chunk = self._socket.recv(n - len(data)) # pide los que faltan para  completar
            if not chunk:
                return None          # conexión cerrada por el otro extremo
            data += chunk
        return data

    # Conexion

    def write(self, obj: Any) -> bool:
        """Serializa `obj` con JSON y lo envía con un header de 4 bytes."""
        try:
            payload_obj = dataclasses.asdict(obj) if ( # Se hace el dict
                dataclasses.is_dataclass(obj) and not isinstance(obj, type) # Tipo de ser clase
            ) else obj
            
            payload: bytes = json.dumps(payload_obj).encode("utf-8") # se hace Json
            header: bytes = struct.pack("!I", len(payload)) # Obtiene cuantos Bytes pesa el paquete
            self._socket.sendall(header + payload) # Manta todo, cabecera y contenido
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
            length: int = struct.unpack("!I", header)[0] # Obtiene cuantos bytes son
            payload = self._recv_exact(length) # Pide esos Bytes
            if payload is None:
                return None
            return json.loads(payload.decode("utf-8")) # Lo hace Json
        except (OSError, json.JSONDecodeError, struct.error):
            return None

    def close(self) -> bool:
        """Cierra el socket subyacente."""
        try:
            self._socket.close()
            return True
        except OSError:
            return False
