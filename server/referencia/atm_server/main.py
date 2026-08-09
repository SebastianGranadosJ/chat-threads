# main.py  —  punto de entrada del servidor ATM.
# Ensambla la capa de transporte (Server/Session/socket) con la capa de
# dominio (ATMServer) y arranca el loop de aceptación de operaciones.
#
# Flujo por operación (una conexión TCP = una operación):
#   bind()   -> accept() de la conexión entrante
#   listen() -> recibe OperationRequest del cliente
#   dispatch -> delega al método correcto de ATMServer
#   response()-> envía OperationResponse al cliente
#   close()  -> cierra esa conexión y vuelve a esperar

from server_socket_factory import ServerSocketFactory
from server import Server
from atm_server import ATMServer
from models import OperationRequest, OperationResponse


def _dispatch(atm: ATMServer, request: OperationRequest) -> OperationResponse:
    """Enruta la petición al método ATM correspondiente."""
    if request.operation == "DEPOSIT":
        return atm.deposit(
            request.account_number, request.pin, request.amount or 0.0
        )
    if request.operation == "WITHDRAW":
        return atm.withdraw(
            request.account_number, request.pin, request.amount or 0.0
        )
    if request.operation == "CHECK_BALANCE":
        return atm.check_balance(request.account_number, request.pin)
    return OperationResponse(
        success=False, message=f"Operación desconocida: '{request.operation}'."
    )


def main() -> None:
    # --- Capa de transporte: crear socket de escucha ---
    factory = ServerSocketFactory()
    server_socket = factory.get()
    if server_socket is None:
        print("[ERROR] No se pudo iniciar el servidor ATM. Abortando.")
        return

    # --- Capa de dominio: lógica de negocio del cajero ---
    atm = ATMServer()

    # La misma instancia de Server reutiliza el socket de escucha en cada
    # iteración; bind() acepta una nueva conexión en cada vuelta.
    server = Server(server_socket)

    print("=" * 45)
    print("   Servidor ATM iniciado en 127.0.0.1:1802")
    print("   Esperando operaciones... (Ctrl+C para salir)")
    print("=" * 45)

    try:
        while True:
            # 1. Aceptar la siguiente conexión entrante
            if not server.bind(): # Se queda escuchando, si falla vuelve a intentarlo con continue
                continue

            # 2. Recibir la petición del cliente
            # Una vez creada la conexion recibe la peticion
            data: list = server.listen()
            if not data:
                print("[AVISO] Petición vacía recibida, se ignora.")
                server.close() #Se cierra la conexion pq no venia nada
                continue

            request: OperationRequest = OperationRequest(**data[0]) # Recostruye el Json en Operation request
            print(
                f"[PETICIÓN] op={request.operation} "
                f"| cuenta={request.account_number}"
            )

            # 3. Procesar la operación en la capa de dominio
            result: OperationResponse = _dispatch(atm, request) # la despacah

            # 4. Enviar la respuesta al cliente
            server.response([result]) # responde 

            # 5. Cerrar esta conexión (sin sesión persistente)
            server.close()
            print(f"[RESPUESTA] success={result.success} | {result.message}")

    except KeyboardInterrupt:
        print("\n[INFO] Servidor detenido manualmente.")
    finally:
        server_socket.close()


if __name__ == "__main__":
    main()
