import java.util.LinkedList;

/**
 * MessageQueue.java — cola compartida entre el hilo de I/O (productor)
 * y el hilo worker (consumidor).
 *
 * ═══════════════════════════════════════════════════════════════════
 *  SECCIÓN CRÍTICA — MONITOR CONSTRUIDO A MANO
 * ═══════════════════════════════════════════════════════════════════
 *
 * Problema:
 *   Dos hilos acceden a la misma lista (`queue`) sin coordinación →
 *   condición de carrera: corrupción de datos o lecturas inconsistentes.
 *
 * Mecanismo elegido: MONITOR (exclusión mutua + variable de condición).
 *
 *   • synchronized en cada método público:
 *       Garantiza que solo UN hilo a la vez ejecuta el cuerpo del método.
 *       Java asocia un "lock intrínseco" (mutex) a cada objeto; `synchronized`
 *       lo adquiere al entrar y lo libera al salir, sea por return o excepción.
 *
 *   • wait() en take():
 *       Si la cola está vacía, el worker no puede avanzar.
 *       `wait()` ATOMICAMENTE libera el lock y suspende el hilo actual,
 *       sin gastar CPU (no es espera activa / spin-lock).
 *       El lock se vuelve a adquirir antes de que `wait()` retorne.
 *
 *   • notify() en put():
 *       Cuando el hilo de I/O agrega un mensaje, despierta al worker
 *       (que estaba en wait()) para que lo procese.
 *       Se usa notify() (no notifyAll()) porque hay exactamente un consumidor.
 *
 *   • while (no if) alrededor de wait():
 *       Buena práctica estándar de monitores en Java: al despertar hay que
 *       re-verificar la condición porque pueden ocurrir "wakeups espurios"
 *       (el SO puede despertar al hilo sin que notify() haya sido llamado).
 *       Con `if`, el hilo asumiría que la condición cambió y procedería
 *       con una cola vacía. Con `while`, se vuelve a dormir si sigue vacía.
 *
 * Por qué NO se usa java.util.concurrent.BlockingQueue:
 *   La restricción del profesor exige que el mecanismo de bloqueo sea
 *   construido explícitamente para poder explicar cada primitiva.
 *   BlockingQueue ya encapsula todo esto internamente y no es transparente.
 * ═══════════════════════════════════════════════════════════════════
 */
public class MessageQueue {

    // La lista interna. Solo se accede dentro de métodos synchronized,
    // por eso no necesita ser una colección thread-safe por sí misma.
    private final LinkedList<Message> queue = new LinkedList<>();

    // ---------------------------------------------------------------
    // Productor: hilo de I/O
    // ---------------------------------------------------------------

    /**
     * Agrega un mensaje al final de la cola y despierta al worker.
     *
     * La anotación synchronized adquiere el lock de `this` antes de entrar.
     * notify() despierta a UN hilo que esté bloqueado en wait() sobre
     * este mismo objeto (el worker). Como solo hay un consumidor, notify()
     * es suficiente; notifyAll() sería correcto pero innecesario.
     *
     * @param m el mensaje completo recién ensamblado por el framing loop
     */
    public synchronized void put(Message m) {
        queue.addLast(m);   // O(1) — LinkedList agrega al final en tiempo constante
        notify();           // despierta al worker si estaba en wait()
    }

    // ---------------------------------------------------------------
    // Consumidor: hilo worker
    // ---------------------------------------------------------------

    /**
     * Extrae el mensaje más antiguo de la cola.
     * Si la cola está vacía, el hilo actual se duerme hasta que put() agregue algo.
     *
     * Flujo completo del monitor:
     *   1. Worker entra a take(), adquiere el lock.
     *   2. Verifica queue.isEmpty() → true (cola vacía).
     *   3. Llama wait(): libera el lock y se suspende (sin CPU).
     *   4. El hilo de I/O entra a put(), adquiere el lock (ahora libre).
     *   5. Agrega el mensaje, llama notify(), libera el lock.
     *   6. Worker se despierta, re-adquiere el lock, vuelve al while.
     *   7. queue.isEmpty() → false → sale del while y retorna el mensaje.
     *
     * @return el mensaje más antiguo de la cola
     * @throws InterruptedException si el hilo es interrumpido mientras duerme
     *         (señal de apagado limpio — Main.java captura esto)
     */
    public synchronized Message take() throws InterruptedException {
        while (queue.isEmpty()) {
            wait();     // libera lock; recupera lock antes de retornar
        }
        return queue.removeFirst();  // O(1) — LinkedList elimina del frente en tiempo constante
    }
}
