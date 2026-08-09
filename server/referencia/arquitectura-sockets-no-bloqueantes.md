# Paso 7 — Arquitectura de sockets no bloqueantes (servidor Java)

Modelo de máquina de estado finito (`Selector` de `java.nio`), como en la lectura del paso 1,
más un segundo hilo de procesamiento — necesario para que la sincronización elegida en el paso
3/4 tenga un motivo real de existir (si todo corriera en un solo hilo, no habría nada que
proteger).

## Dos hilos, un rol cada uno

**Hilo de I/O (el `Selector`)**: el único que toca los sockets. Nunca decide lógica de negocio,
solo mueve bytes.

1. `ServerSocketChannel` no bloqueante, registrado para `OP_ACCEPT`.
2. Bucle: `selector.select()` (esto sí bloquea, pero es un solo hilo esperando por *todas* las
   conexiones a la vez — no es lo mismo que bloquear un hilo por cliente).
3. Por cada `SelectionKey` listo:
   - **Aceptable**: acepta la conexión, la pone no bloqueante, la registra para `OP_READ`, y le
     asocia (`attach()`) un objeto de estado propio — la "tabla" que menciona la lectura.
   - **Legible**: lee lo que haya disponible hacia el buffer de esa conexión. Como el mensaje
     puede llegar fragmentado, el estado de cada conexión lleva una mini máquina de estados de
     framing: primero esperar los 4 bytes del header, después esperar exactamente N bytes del
     payload (N = lo que decía el header). Recién cuando el payload está completo se arma el JSON.
   - **Escribible**: si esa conexión tiene bytes pendientes de salida, escribe lo que se pueda sin
     bloquear; si quedó algo sin mandar, se queda registrada en `OP_WRITE` para el próximo ciclo.
4. Cuando un mensaje queda completo (JSON armado), no lo procesa acá — lo mete en la cola
   compartida y sigue con el siguiente evento.

**Hilo de procesamiento (worker)**: el único que toca la lógica de negocio (login, lista de
usuarios, decidir a quién reenviar).

1. Saca mensajes de la cola compartida (`take()` de la cola construida a mano en el paso 3/4 —
   `synchronized` + `wait()`/`notify()`).
2. Según el `type` del mensaje (paso 6), actualiza el mapa de usuarios conectados o decide los
   destinatarios del reenvío.
3. Para cada destinatario, encola los bytes de salida en el estado de *esa* conexión y llama a
   `selector.wakeup()` — necesario porque el hilo de I/O puede estar dormido dentro de
   `select()`, y sin esto no se enteraría de que ya hay algo para escribir.

## Estado por conexión (la "tabla")

Un objeto por `SocketChannel` (asociado vía `SelectionKey.attach()`), con:

- Buffer de lectura parcial + fase actual (`ESPERANDO_HEADER` / `ESPERANDO_PAYLOAD`) y bytes
  restantes.
- Nombre de usuario (una vez hecho `LOGIN`; antes de eso, `null`).
- Cola de bytes/mensajes pendientes de salida.

## Dónde queda la sección crítica (adelanto del paso 8)

La cola compartida entre el hilo de I/O (productor) y el hilo worker (consumidor) es la sección
crítica real y suficiente: dos hilos, un recurso compartido, sin coordinación se corrompe. No
hace falta inventar un pool de workers para "forzar" más contención — un productor y un
consumidor ya es el caso de libro que justifica el monitor construido en el paso 3/4.
