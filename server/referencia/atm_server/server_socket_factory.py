# Crea el Socket Servidor :)))

import socket
from typing import Optional

HOST: str = "127.0.0.1"
PORT: int = 1802


class ServerSocketFactory:
    """Crea y configura el socket TCP de escucha del servidor ATM."""

    def get(self) -> Optional[socket.socket]:
        """Crea el socket, lo enlaza al puerto y lo pone a escuchar.

        Returns:
            Socket listo para usarser 
        """
        try:
            # AF INET indica que es direccion IPV4, SOCK STREAM que es por TCP 
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            # SO_REUSEADDR evita "Address already in use" al reiniciar el servidor
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            sock.bind((HOST, PORT)) # Le asigna el host y el puerto
            sock.listen(5) # Cuantos se queda esperando
            sock.settimeout(1.0)  # Desbloquea accept() cada segundo para procesar Ctrl+C
            return sock
        except OSError as exc:
            print(f"[ERROR] No se pudo crear el socket del servidor: {exc}")
            return None
