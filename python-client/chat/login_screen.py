# login_screen.py  —  capa de presentación: pantalla de login.
# Ver references/diseno-interfaz-grafica.md §1.
# Campo de usuario + botón "Conectar"; se queda en esta pantalla si llega
# LOGIN_ERROR, mostrando el motivo.

import tkinter as tk
from tkinter import ttk
from typing import Callable

from client_socket_factory import DEFAULT_HOST
from theme import ERROR


class LoginScreen(ttk.Frame):
    """Pantalla inicial: pide host (opcional) y nombre de usuario."""

    def __init__(self, parent: tk.Misc, on_submit: Callable[[str, str], None]) -> None:
        super().__init__(parent, padding=20)
        self._on_submit = on_submit
        self.pack(expand=True, fill="both")

        title = ttk.Label(self, text="Chat distribuido", font=("Segoe UI", 14, "bold"))
        title.grid(row=0, column=0, columnspan=2, pady=(0, 15))

        ttk.Label(self, text="Servidor:").grid(row=1, column=0, sticky="e", pady=4)
        self._host_var = tk.StringVar(value=DEFAULT_HOST)
        self._host_entry = ttk.Entry(self, textvariable=self._host_var)
        self._host_entry.grid(row=1, column=1, sticky="we", pady=4)

        ttk.Label(self, text="Usuario:").grid(row=2, column=0, sticky="e", pady=4)
        self._username_var = tk.StringVar()
        self._username_entry = ttk.Entry(self, textvariable=self._username_var)
        self._username_entry.grid(row=2, column=1, sticky="we", pady=4)
        self._username_entry.focus_set()

        self._connect_button = ttk.Button(self, text="Conectar", command=self._submit)
        self._connect_button.grid(row=3, column=0, columnspan=2, pady=(15, 5))

        self._error_var = tk.StringVar()
        self._error_label = ttk.Label(self, textvariable=self._error_var, foreground=ERROR)
        self._error_label.grid(row=4, column=0, columnspan=2)

        self.columnconfigure(1, weight=1)
        self._username_entry.bind("<Return>", lambda _e: self._submit())
        self._host_entry.bind("<Return>", lambda _e: self._submit())

    def _submit(self) -> None:
        host = self._host_var.get().strip() or DEFAULT_HOST
        username = self._username_var.get().strip()

        if not username:
            self.show_error("Ingrese un nombre de usuario.")
            return

        self._error_var.set("")
        self._set_busy(True)
        self._on_submit(host, username)

    def show_error(self, message: str) -> None:
        self._error_var.set(message)
        self._set_busy(False)

    def _set_busy(self, busy: bool) -> None:
        state = "disabled" if busy else "normal"
        self._connect_button.config(state=state)
        self._username_entry.config(state=state)
        self._host_entry.config(state=state)
