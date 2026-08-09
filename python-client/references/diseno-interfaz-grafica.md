# Paso 9 — Interfaz gráfica

Basado en la maqueta del profesor. Mismo diseño para el cliente Java (Swing) y el cliente Python
(Tkinter) — cambia la tecnología, no la estructura.

## 1. Pantalla de login (clientes)

No aparece en la maqueta (que muestra la ventana ya conectada), pero sigue haciendo falta un paso
previo para identificarse:

- Campo de texto: nombre de usuario.
- Botón "Conectar" → manda `LOGIN`.
- No avanza a la ventana principal hasta recibir `LOGIN_OK`; si llega `LOGIN_ERROR`, muestra el
  motivo (ej. nombre en uso) y se queda en esta pantalla.

## 2. Ventana principal — chat grupal (clientes), tras `LOGIN_OK`

- **Título**: nombre del usuario propio (ej. "userx").
- **Panel central**: log de mensajes grupales (`GROUP_MESSAGE`), formato `emisor: contenido`.
- **Panel derecho**: lista de todos los usuarios conectados.
- **Abajo**: campo de texto + botón "Enviar".

## 3. Chat privado — misma interfaz, ventana aparte

No es la misma ventana con destinatario seleccionable: al hacer doble clic sobre un usuario de la
lista se abre una **ventana nueva**, con el mismo layout (log + lista + campo + enviar), pero
acotada a esa conversación de a dos:

- El log solo muestra los mensajes intercambiados entre el usuario propio y ese contacto
  (`PRIVATE_MESSAGE`, filtrando por `sender`/`recipient`).
- El panel derecho de esa ventana solo lista a esos dos usuarios, no a todos los conectados.
- El campo de texto envía siempre `PRIVATE_MESSAGE` con `recipient` fijo a ese contacto.

Si ya hay una ventana privada abierta con ese usuario, doble clic la enfoca en vez de abrir una
duplicada.

## 4. Servidor — sin interfaz gráfica

Corre sin GUI, solo consola/logs por salida estándar si hace falta ver actividad durante la
demo. La caja "LINUX / JAVA" con el ícono de base de datos en la maqueta es solo el ícono
genérico de "servidor" del diagrama — no implica persistencia real: sigue sin base de datos ni
histórico (estado de sesión en memoria, como quedó en el resumen del paso 1).

## Nota de sincronización en el cliente

El hilo que actualiza la GUI no puede ser el mismo que hace `recv()` bloqueante (paso 5) — hay
que pasar los datos del hilo listener al hilo de la GUI de forma segura: `SwingUtilities.invokeLater()`
en Java, `root.after()` en Tkinter. Ninguno de los dos exige un monitor propio como el del
servidor, porque ya están pensados justo para este traspaso entre hilo de fondo e hilo de interfaz.
