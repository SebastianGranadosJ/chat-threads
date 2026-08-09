# theme.py  —  paleta y estilo visual rosado, compartido por toda la GUI.
#
# Centraliza los colores acá para que login/ventana principal/chat privado
# se vean consistentes. Los widgets ttk (Frame, Label, Button, Entry) se
# estilizan vía ttk.Style; los widgets clásicos (Text, Listbox) no usan
# ttk.Style, así que se configuran directo con las funciones de abajo.

import tkinter as tk
from tkinter import ttk

BG = "#ffe4ec"           # fondo general (rosa claro)
BG_PANEL = "#fff0f5"      # fondo de logs y listas (rosa casi blanco)
ACCENT = "#e75480"        # rosa fuerte (botones, selección)
ACCENT_ACTIVE = "#d63e6c"  # rosa fuerte al presionar
ACCENT_DISABLED = "#f2b8cc"
TEXT = "#5c1a34"          # texto oscuro con tinte rosa, buen contraste
ENTRY_BG = "#ffffff"
ERROR = "#c0245c"


def apply_theme(root: tk.Tk) -> None:
    """Aplica la paleta rosada a todo lo que se construya con ttk de acá en más."""
    root.configure(bg=BG)

    style = ttk.Style(root)
    style.theme_use("clam")

    style.configure("TFrame", background=BG)
    style.configure("TLabel", background=BG, foreground=TEXT)
    style.configure(
        "TButton",
        background=ACCENT,
        foreground="white",
        borderwidth=0,
        focuscolor=ACCENT,
        padding=6,
    )
    style.map(
        "TButton",
        background=[("active", ACCENT_ACTIVE), ("disabled", ACCENT_DISABLED)],
    )
    style.configure("TEntry", fieldbackground=ENTRY_BG, foreground=TEXT, borderwidth=1)
    style.configure("TScrollbar", background=BG, troughcolor=BG_PANEL, arrowcolor=TEXT)


def style_text_widget(widget: tk.Text) -> None:
    """Para tk.Text (logs de chat): no es ttk, se pinta directo."""
    widget.configure(
        bg=BG_PANEL,
        fg=TEXT,
        insertbackground=TEXT,
        relief="flat",
        borderwidth=1,
        highlightthickness=1,
        highlightbackground=ACCENT,
    )


def style_listbox(widget: tk.Listbox) -> None:
    """Para tk.Listbox (usuarios): no es ttk, se pinta directo."""
    widget.configure(
        bg=BG_PANEL,
        fg=TEXT,
        selectbackground=ACCENT,
        selectforeground="white",
        relief="flat",
        borderwidth=1,
        highlightthickness=1,
        highlightbackground=ACCENT,
    )
