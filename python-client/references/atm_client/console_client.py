# console_client.py  —  capa de presentación: interfaz de consola del cajero.
# Responsable únicamente de la interacción con el usuario: mostrar el menú,
# leer inputs y mostrar el resultado de OperationResponse.
# No contiene lógica de negocio ni de red; delega todo al Controller.
# Sin sesiones: cada operación pide todos los datos desde cero.

from controller import Controller
from models import OperationResponse

_SEPARATOR: str = "-" * 45


def _print_separator() -> None:
    print(_SEPARATOR)


def _print_response(response: OperationResponse) -> None:
    """Imprime el resultado de una operación de forma uniforme."""
    _print_separator()
    label: str = "EXITO" if response.success else "ERROR"
    print(f"[{label}] {response.message}")
    if response.balance is not None:
        print(f"Saldo actual: ${response.balance:,.2f}")
    _print_separator()


class ATMClient:
    """Menú de consola del cajero automático.

    Recibe el Controller por inyección de dependencia; ignora completamente
    cómo se implementa la comunicación con el servidor.
    """

    def __init__(self, controller: Controller) -> None:
        self._controller: Controller = controller

    # ------------------------------------------------------------------
    # Manejadores de cada opción del menú
    # ------------------------------------------------------------------

    def _handle_deposit(self) -> None:
        print("\n--- DEPÓSITO ---")
        card_number: str = input("Número de tarjeta: ").strip()
        raw_amount: str = input("Monto a depositar: $").strip()
        pin: str = input("PIN: ").strip()

        try:
            amount: float = float(raw_amount)
        except ValueError:
            _print_separator()
            print("[ERROR] El monto ingresado no es válido.")
            _print_separator()
            return

        _print_response(self._controller.deposit(card_number, pin, amount))

    def _handle_withdraw(self) -> None:
        print("\n--- RETIRO ---")
        account_number: str = input("Número de cuenta: ").strip()
        raw_amount: str = input("Monto a retirar: $").strip()
        pin: str = input("PIN: ").strip()

        try:
            amount: float = float(raw_amount)
        except ValueError:
            _print_separator()
            print("[ERROR] El monto ingresado no es válido.")
            _print_separator()
            return

        _print_response(self._controller.withdraw(account_number, pin, amount))

    def _handle_check_balance(self) -> None:
        print("\n--- CONSULTA DE SALDO ---")
        account_number: str = input("Número de cuenta: ").strip()
        pin: str = input("PIN: ").strip()

        _print_response(self._controller.check_balance(account_number, pin))

    # ------------------------------------------------------------------
    # Menú principal
    # ------------------------------------------------------------------

    def _print_menu(self) -> None:
        print("\n" + _SEPARATOR)
        print("       CAJERO AUTOMÁTICO VIRTUAL")
        print(_SEPARATOR)
        print("  1. Depositar")
        print("  2. Retirar")
        print("  3. Consultar saldo")
        print("  4. Salir")
        print(_SEPARATOR)

    def run(self) -> None:
        """Inicia el loop de menú; termina cuando el usuario elige Salir."""
        print("\nBienvenido al Cajero Automático Virtual.")
        while True:
            self._print_menu()
            option: str = input("Seleccione una opción: ").strip()

            if option == "1":
                self._handle_deposit()
            elif option == "2":
                self._handle_withdraw()
            elif option == "3":
                self._handle_check_balance()
            elif option == "4":
                print("\nGracias por usar el cajero. ¡Hasta pronto!")
                break
            else:
                print("\n[ERROR] Opción no válida. Por favor elija entre 1 y 4.")
