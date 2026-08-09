# Paso 6 — Protocolo de mensajes del chat

Mismo framing del ATM (header de 4 bytes big-endian + payload JSON), sobre una conexión
**persistente** por cliente (no por operación, a diferencia del ATM — ver nota del paso 5).
Nombres de campos y tipos en inglés, consistente con el resto del código.

## Formato del sobre

```json
{"type": "...", "sender": "...", "recipient": "...", "content": "...", "timestamp": "..."}
```

- `recipient`: `null` en mensajes grupales.
- `timestamp`: lo completa el servidor al reenviar, no el cliente (evita depender de relojes
  locales desincronizados — conecta con lo leído en el §6.1 del libro guía).

## Tipos de mensaje

| Tipo | Dirección | Uso |
|---|---|---|
| `LOGIN` | cliente → servidor | Primer mensaje al conectar: registra el nombre de usuario |
| `LOGIN_OK` / `LOGIN_ERROR` | servidor → cliente | Confirma o rechaza (ej. nombre ya en uso) |
| `GROUP_MESSAGE` | ambos sentidos | Cliente lo manda para el grupo; servidor lo reenvía a todos los conectados |
| `PRIVATE_MESSAGE` | ambos sentidos | Cliente lo manda con `recipient`; servidor lo reenvía solo a ese usuario |
| `USER_CONNECTED` / `USER_DISCONNECTED` | servidor → todos | Aviso automático cuando alguien entra o sale |
| `USER_LIST` | servidor → cliente | Lista completa de conectados, al hacer login |
| `ERROR` | servidor → cliente | Ej. mensaje privado a alguien que ya no está conectado |
