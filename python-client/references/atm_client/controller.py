# controller.py  —  capa intermediaria: desacopla la consola del transporte.
# Replica el rol del stub/proxy en una arquitectura cliente-servidor real:
# serializa la llamada, la envía por socket y devuelve la respuesta
# deserializada, ocultando completamente el detalle de red al console_client.
#
# Sin sesiones: cada método abre su propia conexión TCP, envía UNA petición,
# recibe UNA respuesta y cierra.  Si falla en cualquier punto, retorna un
# OperationResponse de error sin estado pendiente.

from client import Client
from models import OperationRequest, OperationResponse


class Controller:
    """Puente entre la UI de consola y el servidor ATM remoto."""

    # ------------------------------------------------------------------
    # API pública (misma firma que el Controller del ejercicio anterior)
    # ------------------------------------------------------------------

    def deposit(
        self, card_number: str, pin: str, amount: float
    ) -> OperationResponse:
        """Envía una petición de depósito al servidor y retorna la respuesta."""
        return self._send(
            OperationRequest(
                operation="DEPOSIT",
                account_number=card_number,
                pin=pin,
                amount=amount,
            )
        )

    def withdraw(
        self, account_number: str, pin: str, amount: float
    ) -> OperationResponse:
        """Envía una petición de retiro al servidor y retorna la respuesta."""
        return self._send(
            OperationRequest(
                operation="WITHDRAW",
                account_number=account_number,
                pin=pin,
                amount=amount,
            )
        )

    def check_balance(
        self, account_number: str, pin: str
    ) -> OperationResponse:
        """Envía una petición de consulta de saldo y retorna la respuesta."""
        return self._send(
            OperationRequest(
                operation="CHECK_BALANCE",
                account_number=account_number,
                pin=pin,
            )
        )

    # Lógica interna de transporte (una conexión por operación)

    def _send(self, request: OperationRequest) -> OperationResponse:
        """Abre conexión, envía request, recibe response, cierra conexión.

        Maneja cualquier fallo de red retornando un OperationResponse de
        error, sin lanzar excepciones al llamador.
        """
        atm_client = Client()

        # 1. Conectar al servidor
        if not atm_client.connect():
            return OperationResponse(
                success=False,
                message=(
                    "No se pudo conectar al servidor ATM. "
                    "Verifique que esté en ejecución e intente nuevamente."
                ),
            )

        # 2. Enviar la petición (+ marcador de fin automático)
        if not atm_client.response([request]):
            atm_client.close()
            return OperationResponse(
                success=False,
                message="Error al enviar la solicitud al servidor.",
            )

        # 3. Recibir la respuesta del servidor
        data: list = atm_client.listen()

        # 4. Cerrar la conexión (operación atómica: sin estado persistente)
        atm_client.close()

        if not data:
            return OperationResponse(
                success=False,
                message="El servidor no envió respuesta. Intente nuevamente.",
            )

        response = OperationResponse(**data[0])
        return response
