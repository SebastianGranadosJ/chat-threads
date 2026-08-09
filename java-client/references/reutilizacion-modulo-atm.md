# Paso 5 — Reutilización del módulo de comunicación del ATM

Conclusión final, ya con la restricción confirmada de que **solo el servidor debe ser no
bloqueante** (el cliente usa un hilo dedicado con sockets bloqueantes — ver justificación abajo).

## Decisión sobre bloqueante/no bloqueante

- **Servidor (Java)**: no bloqueante obligatorio. Con N clientes y un solo proceso, un hilo por
  cliente no escala (cada hilo del SO tiene costo real de creación/memoria). Se necesita el
  modelo de máquina de estado finito — `Selector` de `java.nio` — tal como describe la lectura del
  paso 1.
- **Clientes (Java y Python)**: bloqueante está bien. Cada cliente tiene siempre exactamente una
  conexión (al servidor), sin importar cuántas conversaciones (grupal/privada) tenga abiertas en
  la GUI — todas viajan por el mismo socket. Con N fijo en 1 no hay problema de escala que
  resolver, así que alcanza con el modelo multihilos: un hilo dedicado a `recv()` bloqueante
  (listener) separado del hilo de la GUI, para que uno no congele al otro.

## Qué se reutiliza de cada pieza

| Componente | Base | Se conserva | Se reescribe |
|---|---|---|---|
| Protocolo (diseño) | Header 4 bytes big-endian + payload JSON del ATM | Completo — es agnóstico al lenguaje | Agregar los tipos de operación propios del chat (paso 6) |
| Cliente Python | `connection.py` / `socket_process.py` / `client_socket_factory.py` | Casi todo — sigue siendo bloqueante, framing intacto | Agregar el hilo listener separado del hilo principal/GUI |
| Cliente Java | Ejemplo original del profesor (`JavaClientSocket.java`, `SocketProcess.java`, `Session.java`) | Patrón de capas; framing si ya separa header/payload | Confirmar si serializa en JSON (si no, adaptarlo); agregar hilo listener + GUI Swing |
| Servidor Java | Mismo ejemplo del profesor, lado servidor | Patrón de capas (transporte separado de dominio) | Migración de fondo: `ServerSocket`/`Socket` bloqueantes → `Selector` no bloqueante |

## Nota importante para el paso 6 (protocolo)

El ATM usa conexión **por operación**: el cliente abre el socket, manda una petición, recibe una
respuesta, y cierra — diseño "sin sesión" (stateless, una conexión = una operación). El chat
**no puede funcionar así**: necesita una conexión **persistente** por cliente durante toda la
sesión, porque el servidor tiene que poder empujarle mensajes al cliente en cualquier momento
(alguien más le escribe) sin que el cliente esté preguntando. Esto no afecta el framing a nivel
de bytes (se reutiliza igual), pero sí cambia el patrón de uso de la conexión — hay que tenerlo
en cuenta al diseñar el protocolo del paso 6.
