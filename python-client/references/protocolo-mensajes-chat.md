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
| `LOGIN` | cliente → servidor | Primer mensaje al conectar: registra el nombre de usuario. **El nombre va en `sender`** (mismo campo que usan todos los demás tipos para identificar quién manda; `content` queda vacío en este mensaje) |
| `LOGIN_OK` / `LOGIN_ERROR` | servidor → cliente | Confirma o rechaza (ej. nombre ya en uso) |
| `GROUP_MESSAGE` | ambos sentidos | Cliente lo manda para el grupo; servidor lo reenvía a **todos** los conectados, **incluido el propio emisor** (confirmado por prueba) |
| `PRIVATE_MESSAGE` | ambos sentidos | Cliente lo manda con `recipient`; servidor lo reenvía al destinatario **y también de vuelta al emisor** (confirmado por prueba) |
| `USER_CONNECTED` / `USER_DISCONNECTED` | servidor → todos | Aviso automático cuando alguien entra o sale |
| `USER_LIST` | servidor → cliente | Lista completa de conectados, al hacer login |
| `ERROR` | servidor → cliente | Ej. mensaje privado a alguien que ya no está conectado |

## Nota para la implementación de los clientes (pasos 11/12)

Como el servidor devuelve un eco de `GROUP_MESSAGE`/`PRIVATE_MESSAGE` al propio emisor, el
cliente **no debe** agregar el mensaje a su pantalla apenas lo envía — debe esperar a que llegue
por el socket, igual que cualquier otro mensaje. Si lo agrega localmente además, va a aparecer
duplicado.
