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
