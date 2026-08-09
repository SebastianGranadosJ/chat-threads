"""
Script de prueba - simula dos clientes de chat hablando con el servidor Java.
No requiere instalar nada (solo librería estándar de Python).

Uso: python test_clientes.py
Servidor debe estar corriendo en localhost:5000 antes de ejecutar esto.
"""

import socket
import struct
import json
import threading
import time

HOST = "localhost"
PORT = 5000


def send_message(sock, msg_dict):
    payload = json.dumps(msg_dict).encode("utf-8")
    header = struct.pack(">I", len(payload))
    sock.sendall(header + payload)


def recv_exact(sock, n):
    data = b""
    while len(data) < n:
        chunk = sock.recv(n - len(data))
        if not chunk:
            return None
        data += chunk
    return data


def recv_message(sock):
    header = recv_exact(sock, 4)
    if header is None:
        return None
    length = struct.unpack(">I", header)[0]
    payload = recv_exact(sock, length)
    if payload is None:
        return None
    return json.loads(payload.decode("utf-8"))


def listener(sock, username):
    while True:
        try:
            msg = recv_message(sock)
        except (ConnectionError, OSError):
            break
        if msg is None:
            print(f"[{username}] << conexion cerrada por el servidor")
            break
        print(f"[{username}] << {msg}")


def run_client(username, actions, delay_before_start=0):
    time.sleep(delay_before_start)
    sock = socket.create_connection((HOST, PORT))
    t = threading.Thread(target=listener, args=(sock, username), daemon=True)
    t.start()

    print(f"[{username}] >> LOGIN")
    send_message(sock, {"type": "LOGIN", "sender": username, "recipient": None,
                         "content": "", "timestamp": ""})
    time.sleep(1)

    for action in actions:
        print(f"[{username}] >> {action}")
        send_message(sock, action)
        time.sleep(1)

    time.sleep(3)  # tiempo para recibir lo que falte antes de cerrar
    sock.close()
    print(f"[{username}] conexion cerrada por el cliente")


if __name__ == "__main__":
    t1 = threading.Thread(target=run_client, args=("userx", [
        {"type": "GROUP_MESSAGE", "sender": "userx", "recipient": None,
         "content": "hola a todos", "timestamp": ""},
    ]))
    t2 = threading.Thread(target=run_client, args=("usery", [
        {"type": "PRIVATE_MESSAGE", "sender": "usery", "recipient": "userx",
         "content": "este mensaje es solo para vos", "timestamp": ""},
    ], 0.5))  # arranca medio segundo despues, para que userx ya este logueado

    t1.start()
    t2.start()
    t1.join()
    t2.join()

    print("\nPrueba terminada.")