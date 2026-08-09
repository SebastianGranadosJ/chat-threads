# main_screen.py  —  capa de presentación: ventana principal (chat grupal).
# Ver references/diseno-interfaz-grafica.md §2.
# Panel central: log de GROUP_MESSAGE, formato "emisor: contenido".
# Panel derecho: lista de usuarios conectados (doble clic abre chat privado).
# Abajo: campo de texto + botón "Enviar".

import tkinter as tk
from tkinter import ttk
from typing import Callable, List

from theme import style_listbox, style_text_widget


class MainScreen(ttk.Frame):

    def __init__(
        self,
        parent: tk.Misc,
        username: str,
        on_send_group: Callable[[str], None],
        on_open_private: Callable[[str], None],
    ) -> None:
        super().__init__(parent, padding=8)
        self._username = username
        self._on_send_group = on_send_group
        self._on_open_private = on_open_private
        self.pack(expand=True, fill="both")

        self.columnconfigure(0, weight=3)
        self.columnconfigure(1, weight=1)
        self.rowconfigure(0, weight=1)

        # Panel central: log del chat grupal
        log_frame = ttk.Frame(self)
        log_frame.grid(row=0, column=0, sticky="nsew", padx=(0, 8))
        log_frame.rowconfigure(0, weight=1)
        log_frame.columnconfigure(0, weight=1)

        self._log = tk.Text(log_frame, state="disabled", wrap="word")
        style_text_widget(self._log)
        self._log.grid(row=0, column=0, sticky="nsew")
        log_scroll = ttk.Scrollbar(log_frame, orient="vertical", command=self._log.yview)
        log_scroll.grid(row=0, column=1, sticky="ns")
        self._log.config(yscrollcommand=log_scroll.set)

        # Panel derecho: lista de usuarios conectados
        users_frame = ttk.Frame(self)
        users_frame.grid(row=0, column=1, sticky="nsew")
        users_frame.rowconfigure(1, weight=1)
        users_frame.columnconfigure(0, weight=1)

        ttk.Label(users_frame, text="Usuarios conectados").grid(row=0, column=0, sticky="w")
        self._user_listbox = tk.Listbox(users_frame)
        style_listbox(self._user_listbox)
        self._user_listbox.grid(row=1, column=0, sticky="nsew")
        self._user_listbox.bind("<Double-Button-1>", self._on_user_double_click)

        # Abajo: entrada de texto + botón enviar
        entry_frame = ttk.Frame(self)
        entry_frame.grid(row=1, column=0, columnspan=2, sticky="ew", pady=(8, 0))
        entry_frame.columnconfigure(0, weight=1)

        self._entry_var = tk.StringVar()
        self._entry = ttk.Entry(entry_frame, textvariable=self._entry_var)
        self._entry.grid(row=0, column=0, sticky="ew")
        self._entry.bind("<Return>", lambda _e: self._send())
        self._entry.focus_set()

        send_button = ttk.Button(entry_frame, text="Enviar", command=self._send)
        send_button.grid(row=0, column=1, padx=(8, 0))

    # ------------------------------------------------------------------
    # Envío
    # ------------------------------------------------------------------

    def _send(self) -> None:
        text = self._entry_var.get().strip()
        if not text:
            return
        self._entry_var.set("")
        self._on_send_group(text)

    def _on_user_double_click(self, _event: tk.Event) -> None:
        selection = self._user_listbox.curselection()
        if not selection:
            return
        user = self._user_listbox.get(selection[0])
        self._on_open_private(user)

    # ------------------------------------------------------------------
    # Actualizaciones desde el controlador (siempre en el hilo principal)
    # ------------------------------------------------------------------

    def append_message(self, sender: str, content: str) -> None:
        self._log.config(state="normal")
        self._log.insert("end", f"{sender}: {content}\n")
        self._log.see("end")
        self._log.config(state="disabled")

    def set_users(self, users: List[str]) -> None:
        self._user_listbox.delete(0, "end")
        for user in sorted(users):
            self._user_listbox.insert("end", user)

    def add_user(self, user: str) -> None:
        current = self._user_listbox.get(0, "end")
        if user not in current:
            self._user_listbox.insert("end", user)

    def remove_user(self, user: str) -> None:
        current = self._user_listbox.get(0, "end")
        if user in current:
            self._user_listbox.delete(current.index(user))
