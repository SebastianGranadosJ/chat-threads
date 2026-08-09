# atm_interface.py  —  capa de dominio: contrato de la lógica de negocio ATM.
# Define QUÉ operaciones expone el servidor de negocio, independientemente
# de cómo se transporten por red.

from abc import ABC, abstractmethod

from models import OperationResponse


class ATMInterface(ABC):

    @abstractmethod
    def deposit(
        self, card_number: str, pin: str, amount: float
    ) -> OperationResponse:
        ...

    @abstractmethod
    def withdraw(
        self, account_number: str, pin: str, amount: float
    ) -> OperationResponse:
        ...

    @abstractmethod
    def check_balance(
        self, account_number: str, pin: str
    ) -> OperationResponse:
        ...
