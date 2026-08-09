# main.py  —  punto de entrada del cliente ATM.
# Instancia el Controller (capa de transporte/proxy) y arranca la consola.
# No importa nada relacionado con sockets directamente; eso es responsabilidad
# del Controller y las capas inferiores.

from controller import Controller
from console_client import ATMClient


def main() -> None:
    # Controller encapsula toda la lógica de conexión al servidor remoto
    controller: Controller = Controller()

    # ATMClient sólo conoce el Controller; ignora la red completamente
    client: ATMClient = ATMClient(controller=controller)
    client.run()


if __name__ == "__main__":
    main()
