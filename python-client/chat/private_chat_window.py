# private_chat_window.py  —  capa de presentación: chat privado con un usuario.
# Ver references/diseno-interfaz-grafica.md §3.
# Ventana aparte (Toplevel), mismo layout que la ventana principal pero
# acotada a la conversación con un único contacto: el log solo muestra los
# PRIVATE_MESSAGE de/hacia ese usuario, y el panel derecho solo lista a los
# dos participantes.

import tkinter as tk
from tkinter import ttk
from typing import Callable


class PrivateChatWindow(tk.Toplevel):

    def __init__(
        self,
        parent: tk.Misc,
        own_username: str,
        peer: str,
        on_send: Callable[[str, str], None],
        on_close: Callable[[str], None],
    ) -> None:
        super().__init__(parent)
        self._own_username = own_username
        self._peer = peer
        self._on_send = on_send
        self._on_close = on_close
        self._closing_silently = False

        self.title(f"Chat privado — {peer}")
        self.protocol("WM_DELETE_WINDOW", self._handle_close)

        container = ttk.Frame(self, padding=8)
        container.pack(expand=True, fill="both")
        container.columnconfigure(0, weight=3)
        container.columnconfigure(1, weight=1)
        container.rowconfigure(0, weight=1)

        log_frame = ttk.Frame(container)
        log_frame.grid(row=0, column=0, sticky="nsew", padx=(0, 8))
        log_frame.rowconfigure(0, weight=1)
        log_frame.columnconfigure(0, weight=1)

        self._log = tk.Text(log_frame, state="disabled", wrap="word")
        self._log.grid(row=0, column=0, sticky="nsew")
        log_scroll = ttk.Scrollbar(log_frame, orient="vertical", command=self._log.yview)
        log_scroll.grid(row=0, column=1, sticky="ns")
        self._log.config(yscrollcommand=log_scroll.set)

        users_frame = ttk.Frame(container)
        users_frame.grid(row=0, column=1, sticky="nsew")
        users_frame.rowconfigure(1, weight=1)
        users_frame.columnconfigure(0, weight=1)

        ttk.Label(users_frame, text="Participantes").grid(row=0, column=0, sticky="w")
        participants = tk.Listbox(users_frame)
        participants.grid(row=1, column=0, sticky="nsew")
        participants.insert("end", own_username)
        participants.insert("end", peer)
        participants.config(state="disabled")

        entry_frame = ttk.Frame(container)
        entry_frame.grid(row=1, column=0, columnspan=2, sticky="ew", pady=(8, 0))
        entry_frame.columnconfigure(0, weight=1)

        self._entry_var = tk.StringVar()
        self._entry = ttk.Entry(entry_frame, textvariable=self._entry_var)
        self._entry.grid(row=0, column=0, sticky="ew")
        self._entry.bind("<Return>", lambda _e: self._send())
        self._entry.focus_set()

        send_button = ttk.Button(entry_frame, text="Enviar", command=self._send)
        send_button.grid(row=0, column=1, padx=(8, 0))

    def _send(self) -> None:
        text = self._entry_var.get().strip()
        if not text:
            return
        self._entry_var.set("")
        # No se agrega a pantalla acá: el servidor hace eco de PRIVATE_MESSAGE
        # al propio emisor (ver referencias/protocolo-mensajes-chat.md).
        self._on_send(self._peer, text)

    def append_message(self, sender: str, content: str) -> None:
        self._log.config(state="normal")
        self._log.insert("end", f"{sender}: {content}\n")
        self._log.see("end")
        self._log.config(state="disabled")

    def focus(self) -> None:
        self.deiconify()
        self.lift()
        self._entry.focus_set()

    def _handle_close(self) -> None:
        self._on_close(self._peer)
        self.destroy()

    def destroy_silently(self) -> None:
        """Usado por el controlador al perder la conexión: cierra la ventana
        sin volver a notificar al controlador (ya está limpiando su estado)."""
        self.destroy()
