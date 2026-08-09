/**
 * Main.java — punto de entrada del servidor de chat distribuido.
 *
 * Levanta los dos hilos del servidor y registra un shutdown hook para
 * apagado limpio con Ctrl+C.
 *
 * Compilar:
 *   javac *.java
 *
 * Ejecutar:
 *   java Main
 *
 * El servidor escucha en 0.0.0.0:5000 (todas las interfaces de red).
 *
 * ─── Arquitectura de dos hilos ────────────────────────────────────
 *
 *   ┌─────────────────────────────────────────────────────────────┐
 *   │  Hilo de I/O  (IoLoop)                                       │
 *   │  • Selector no bloqueante: OP_ACCEPT / OP_READ / OP_WRITE   │
 *   │  • Ensambla mensajes con framing de 4 bytes                  │
 *   │  • Pone mensajes completos en MessageQueue  ──────────────┐  │
 *   └──────────────────────────────────────────────────────────────┘
 *                                                               │
 *                         MessageQueue                          │
 *                   (monitor synchronized                       │
 *                    + wait/notify — SECCIÓN CRÍTICA)           │
 *                                                               │
 *   ┌──────────────────────────────────────────────────────────────┐
 *   │  Hilo Worker  (WorkerThread)                    ◄────────────┘
 *   │  • take() de la cola (se duerme si está vacía)               │
 *   │  • LOGIN / GROUP_MESSAGE / PRIVATE_MESSAGE / DISCONNECT      │
 *   │  • Mantiene userMap (username → canal)                       │
 *   │  • Solicita escrituras a IoLoop.enqueueWrite()               │
 *   └──────────────────────────────────────────────────────────────┘
 */
public class Main {

    public static void main(String[] args) {
        // Cola compartida: el único recurso tocado por ambos hilos.
        MessageQueue queue = new MessageQueue();

        // Capa de I/O: el bucle Selector no bloqueante.
        IoLoop ioLoop = new IoLoop(queue);

        // Capa de dominio: el hilo worker que interpreta y enruta mensajes.
        WorkerThread worker = new WorkerThread(queue, ioLoop);

        Thread ioThread     = new Thread(ioLoop,  "hilo-io");
        Thread workerThread = new Thread(worker,  "hilo-worker");

        // Apagado limpio cuando el usuario presiona Ctrl+C
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[MAIN] Apagando servidor...");
            ioLoop.stop();
            workerThread.interrupt();
        }, "hilo-shutdown"));

        // Iniciar I/O primero para que el Selector esté listo antes de que el
        // worker intente llamar a enqueueWrite()
        ioThread.start();
        workerThread.start();

        System.out.println("[MAIN] Servidor iniciado. Presiona Ctrl+C para detener.");
    }
}
