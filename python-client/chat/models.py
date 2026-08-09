# models.py  —  capa de datos: el sobre del protocolo de chat.
# Mismos 5 campos que WorkerThread.java / JsonHelper.java del lado servidor.

from dataclasses import dataclass
from typing import Optional


@dataclass
class ChatMessage:

    type: str
    sender: Optional[str] = None
    recipient: Optional[str] = None
    content: Optional[str] = None
    timestamp: Optional[str] = None

    @staticmethod
    def from_dict(data: dict) -> "ChatMessage":
        return ChatMessage(
            type=data.get("type", ""),
            sender=data.get("sender"),
            recipient=data.get("recipient"),
            content=data.get("content"),
            timestamp=data.get("timestamp"),
        )
