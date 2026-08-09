# app.py  —  controlador principal de la aplicación cliente de chat.
#
# Une las tres capas: transporte (chat_client / listener), datos (models) y
# presentación (login_screen / main_screen / private_chat_window). Sostiene
# el único root de Tkinter durante toda la vida de la app y hace de "router"
# de mensajes entrantes hacia la pantalla correspondiente.
#
# Sincronización: `_private_windows` (qué ventana de chat privado corresponde
# a cada usuario) lo escribe el hilo principal (al abrir/cerrar ventanas) y lo
# lee el hilo listener (para decidir a qué ventana dirigir un PRIVATE_MESSAGE
# entrante) — ver referencias/diseno-interfaz-grafica.md. Se protege con un
# threading.Lock explícito, igual que exige el enunciado para cualquier
# estructura compartida entre hilos.

import threading
import tkinter as tk
from tkinter import messagebox
from typing import Dict, Optional

from chat_client import ChatClient
from client_socket_factory import PORT
from listener import ListenerThread
from login_screen import LoginScreen
from main_screen import MainScreen
from models import ChatMessage
from private_chat_window import PrivateChatWindow
from theme import apply_theme


class ChatApp:
    """Controlador de la aplicación: dueño del root y del estado de sesión."""

    def __init__(self) -> None:
        self.root = tk.Tk()
        self.root.title("Chat")
        self.root.protocol("WM_DELETE_WINDOW", self._on_root_close)
        apply_theme(self.root)

        self._chat_client = ChatClient()
        self._listener: Optional[ListenerThread] = None
        self._username: Optional[str] = None
        self._intentional_close = False

        self._main_screen: Optional[MainScreen] = None
        self._login_screen: Optional[LoginScreen] = None

        self._private_windows: Dict[str, PrivateChatWindow] = {}
        self._private_windows_lock = threading.Lock()

        self._show_login()

    # ------------------------------------------------------------------
    # Pantallas
    # ------------------------------------------------------------------

    def _clear_root(self) -> None:
        for widget in self.root.winfo_children():
            widget.destroy()

    def _show_login(self) -> None:
        self._clear_root()
        self.root.title("Chat — Conectar")
        self._main_screen = None
        self._login_screen = LoginScreen(self.root, on_submit=self._attempt_login)

    def _enter_main_screen(self) -> None:
        self._clear_root()
        self.root.title(f"Chat — {self._username}")
        self._login_screen = None
        self._main_screen = MainScreen(
            self.root,
            username=self._username,
            on_send_group=self._send_group_message,
            on_open_private=self.open_private_window,
        )
        # El servidor excluye al propio usuario de USER_LIST (ver
        # WorkerThread.buildUserList) porque asume que la GUI ya sabe que
        # está conectada; acá la mostramos igual para que la lista sea
        # "todos los conectados", incluido uno mismo.
        self._main_screen.add_user(self._username)

    # ------------------------------------------------------------------
    # Login
    # ------------------------------------------------------------------

    def _attempt_login(self, host: str, username: str) -> None:
        if not self._chat_client.connect(host, PORT):
            self._login_screen.show_error(
                "No se pudo conectar al servidor. Verifique host/puerto e intente de nuevo."
            )
            return

        self._username = username
        self._intentional_close = False
        self._listener = ListenerThread(
            self._chat_client,
            on_message=self._on_socket_message,
            on_disconnect=self._on_socket_disconnect,
        )
        self._listener.start()

        login_msg = ChatMessage(type="LOGIN", sender=username, content="")
        if not self._chat_client.send(login_msg):
            self._login_screen.show_error("Error al enviar los datos de conexión.")
            self._reset_connection()

    # ------------------------------------------------------------------
    # Recepción de mensajes (llamado desde el hilo listener)
    # ------------------------------------------------------------------

    def _on_socket_message(self, msg: dict) -> None:
        """Se ejecuta en el hilo listener. Nunca toca widgets directamente."""
        if msg.get("type") == "PRIVATE_MESSAGE":
            peer = self._peer_for(msg)
            with self._private_windows_lock:
                window = self._private_windows.get(peer)
            self.root.after(0, lambda: self._handle_private_message(peer, window, msg))
        else:
            self.root.after(0, lambda: self._dispatch(msg))

    def _on_socket_disconnect(self) -> None:
        self.root.after(0, self._handle_disconnect)

    def _peer_for(self, msg: dict) -> str:
        """El "otro" usuario de un PRIVATE_MESSAGE (el servidor hace eco al emisor)."""
        sender = msg.get("sender")
        recipient = msg.get("recipient")
        return recipient if sender == self._username else sender

    # ------------------------------------------------------------------
    # Dispatch (ya en el hilo principal, vía root.after)
    # ------------------------------------------------------------------

    def _dispatch(self, msg: dict) -> None:
        mtype = msg.get("type")

        if self._main_screen is None:
            # Todavía en la pantalla de login: solo interesan LOGIN_OK/LOGIN_ERROR.
            if mtype == "LOGIN_OK":
                self._enter_main_screen()
            elif mtype == "LOGIN_ERROR":
                self._login_screen.show_error(msg.get("content") or "Login rechazado.")
                self._reset_connection()
            return

        if mtype == "USER_LIST":
            content = msg.get("content") or ""
            users = [u for u in content.split(",") if u]
            # El servidor no incluye al propio usuario en la lista; lo
            # agregamos acá para que la lista muestre a todos los conectados.
            if self._username not in users:
                users.append(self._username)
            self._main_screen.set_users(users)
        elif mtype == "USER_CONNECTED":
            user = msg.get("sender") or msg.get("content")
            if user:
                self._main_screen.add_user(user)
        elif mtype == "USER_DISCONNECTED":
            user = msg.get("sender") or msg.get("content")
            if user:
                self._main_screen.remove_user(user)
        elif mtype == "GROUP_MESSAGE":
            self._main_screen.append_message(msg.get("sender") or "?", msg.get("content") or "")
        elif mtype == "ERROR":
            messagebox.showerror("Error del servidor", msg.get("content") or "Error desconocido.")

    def _handle_private_message(
        self, peer: str, window: Optional[PrivateChatWindow], msg: dict
    ) -> None:
        if window is None:
            window = self._create_private_window(peer)
        window.append_message(msg.get("sender") or "?", msg.get("content") or "")

    # ------------------------------------------------------------------
    # Ventanas de chat privado
    # ------------------------------------------------------------------

    def open_private_window(self, peer: str) -> None:
        """Doble clic sobre un usuario: abre su ventana o la enfoca si ya existe."""
        if peer == self._username:
            return
        with self._private_windows_lock:
            window = self._private_windows.get(peer)
        if window is None:
            window = self._create_private_window(peer)
        window.focus()

    def _create_private_window(self, peer: str) -> PrivateChatWindow:
        with self._private_windows_lock:
            existing = self._private_windows.get(peer)
            if existing is not None:
                return existing
            window = PrivateChatWindow(
                self.root,
                own_username=self._username,
                peer=peer,
                on_send=self._send_private_message,
                on_close=self._close_private_window,
            )
            self._private_windows[peer] = window
            return window

    def _close_private_window(self, peer: str) -> None:
        with self._private_windows_lock:
            self._private_windows.pop(peer, None)

    # ------------------------------------------------------------------
    # Envío (siempre desde el hilo principal, disparado por la GUI)
    # ------------------------------------------------------------------

    def _send_group_message(self, text: str) -> None:
        # No se agrega a pantalla acá: el servidor hace eco de GROUP_MESSAGE
        # al propio emisor (ver referencias/protocolo-mensajes-chat.md).
        msg = ChatMessage(type="GROUP_MESSAGE", sender=self._username, content=text)
        self._chat_client.send(msg)

    def _send_private_message(self, peer: str, text: str) -> None:
        # Mismo criterio: el servidor también hace eco de PRIVATE_MESSAGE al emisor.
        msg = ChatMessage(
            type="PRIVATE_MESSAGE", sender=self._username, recipient=peer, content=text
        )
        self._chat_client.send(msg)

    # ------------------------------------------------------------------
    # Desconexión / cierre
    # ------------------------------------------------------------------

    def _handle_disconnect(self) -> None:
        if self._intentional_close:
            return
        messagebox.showerror(
            "Conexión perdida", "Se perdió la conexión con el servidor de chat."
        )
        self._reset_connection()
        self._show_login()

    def _reset_connection(self) -> None:
        self._intentional_close = True
        if self._listener is not None:
            self._listener.stop()
            self._listener = None
        self._chat_client.close()
        self._chat_client = ChatClient()

        with self._private_windows_lock:
            windows = list(self._private_windows.values())
            self._private_windows.clear()
        for window in windows:
            window.destroy_silently()

        self._username = None

    def _on_root_close(self) -> None:
        self._intentional_close = True
        if self._listener is not None:
            self._listener.stop()
        self._chat_client.close()
        self.root.destroy()
