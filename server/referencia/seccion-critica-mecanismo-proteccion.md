# Paso 8 — Sección crítica y mecanismo de protección

## Estructura compartida (sección crítica)

La lista interna de `MessageQueue`, la cola entre el hilo de I/O (productor) y el hilo worker
(consumidor) diseñada en el paso 7. Es la única estructura del servidor tocada por dos hilos al
mismo tiempo — el mapa de usuarios conectados solo lo toca el worker, así que no necesita
protección propia.

## Mecanismo de protección: monitor construido a mano

`synchronized` (mutex intrínseco de Java, uno por objeto) + `wait()`/`notify()` (coordinación por
condición), empaquetados dentro de la propia clase `MessageQueue` — no se usa `BlockingQueue` de
`java.util.concurrent` por la restricción del profesor de no delegar la sincronización a una
librería que ya la resuelva.

```java
class MessageQueue {
    private final LinkedList<Message> queue = new LinkedList<>();

    public synchronized void put(Message m) {
        queue.addLast(m);
        notify();               // despierta al worker si estaba dormido esperando
    }

    public synchronized Message take() throws InterruptedException {
        while (queue.isEmpty()) {
            wait();              // el worker se duerme acá si no hay nada, sin gastar CPU
        }
        return queue.removeFirst();
    }
}
```

- **Exclusión mutua**: `synchronized` en `put()` y `take()` garantiza que un solo hilo a la vez
  modifique `queue`.
- **Coordinación**: `wait()` duerme al worker cuando no hay mensajes, sin espera activa;
  `notify()` lo despierta cuando el hilo de I/O agrega uno nuevo. El `while` (no `if`) revisa la
  condición de nuevo al despertar, por si hay más de un hilo esperando (buena práctica estándar
  de monitores en Java).

## Por qué no hace falta proteger nada más

El hilo de I/O nunca toca el mapa de usuarios ni decide lógica de negocio — solo produce mensajes
completos para la cola. El hilo worker es el único que lee/escribe el mapa de usuarios, así que
ahí no hay acceso concurrente real. Si en el futuro se agregara un pool de varios workers, el
mapa de usuarios pasaría a ser una segunda sección crítica y necesitaría su propio monitor.
