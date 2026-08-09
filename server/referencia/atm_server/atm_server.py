
from typing import Optional

from atm_interface import ATMInterface
from models import OperationResponse



# Base de datos

_ACCOUNTS: list[dict] = [
    {"account_number": "6767", "pin": "1234", "balance": 5000.00},
    {"account_number": "6969", "pin": "4321", "balance": 12500.75},
    {"account_number": "1001", "pin": "0000", "balance": 300.00},
]


class ATMServer(ATMInterface):
    """Lógica de negocio del cajero. Corre en el proceso servidor."""

    def __init__(self) -> None:
        # Copia mutable para que los cambios de saldo persistan en el proceso
        self._accounts: list[dict] = [acc.copy() for acc in _ACCOUNTS]

   
    #  privados

    def _find_account(self, account_number: str) -> Optional[dict]:
        for acc in self._accounts:
            if acc["account_number"] == account_number:
                return acc
        return None

    def _authenticate(
        self, account_number: str, pin: str
    ) -> tuple[bool, Optional[dict], str]:
        acc = self._find_account(account_number)
        if acc is None:
            return False, None, "Cuenta no encontrada. Verifique el número ingresado."
        if acc["pin"] != pin:
            return False, None, "PIN incorrecto. Intente nuevamente."
        return True, acc, ""


    # Implementación de Interface
  

    def deposit(
        self, card_number: str, pin: str, amount: float
    ) -> OperationResponse:
        if amount <= 0:
            return OperationResponse(
                success=False,
                message="El monto a depositar debe ser mayor a cero.",
            )
        ok, acc, error = self._authenticate(card_number, pin)
        if not ok:
            return OperationResponse(success=False, message=error)
        acc["balance"] += amount
        return OperationResponse(
            success=True,
            message=f"Depósito de ${amount:,.2f} realizado con éxito.",
            balance=acc["balance"],
        )

    def withdraw(
        self, account_number: str, pin: str, amount: float
    ) -> OperationResponse:
        if amount <= 0:
            return OperationResponse(
                success=False,
                message="El monto a retirar debe ser mayor a cero.",
            )
        ok, acc, error = self._authenticate(account_number, pin)
        if not ok:
            return OperationResponse(success=False, message=error)
        if amount > acc["balance"]:
            return OperationResponse(
                success=False,
                message=(
                    f"Fondos insuficientes. "
                    f"Su saldo disponible es ${acc['balance']:,.2f}."
                ),
            )
        acc["balance"] -= amount
        return OperationResponse(
            success=True,
            message=f"Retiro de ${amount:,.2f} realizado con éxito.",
            balance=acc["balance"],
        )

    def check_balance(
        self, account_number: str, pin: str
    ) -> OperationResponse:
        ok, acc, error = self._authenticate(account_number, pin)
        if not ok:
            return OperationResponse(success=False, message=error)
        return OperationResponse(
            success=True,
            message="Consulta de saldo exitosa.",
            balance=acc["balance"],
        )
