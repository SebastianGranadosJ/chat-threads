# models.py  —  capa de datos compartida (idéntica en cliente y servidor).
# Define los objetos que viajan serializados por el socket TCP.
# En un sistema real equivaldrían a los DTOs / mensajes Protobuf.

from dataclasses import dataclass
from typing import Optional


@dataclass
class OperationRequest:

    operation: str
    account_number: str
    pin: str
    amount: Optional[float] = None


@dataclass
class OperationResponse:

    success: bool
    message: str
    balance: Optional[float] = None
