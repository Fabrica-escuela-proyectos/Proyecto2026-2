@HU02 @identity
Feature: Inicio de sesión

  # Omitidos a propósito: expiración de la sesión a la hora (obliga a esperar o alterar el reloj), protección de datos
  # sensibles en registros internos (se verifica en integración) y cambio de contraseña (no hay operación equivalente en la API).

  @positivo @sesion
  Scenario: Inicio de sesión exitoso con credenciales válidas
    Given un cliente registrado quiere iniciar sesión
    When proporciona su correo y contraseña correctos
    Then obtiene acceso

  @excepcion @sesion
  Scenario Outline: Intento de inicio de sesión con credenciales inválidas
    Given un cliente registrado quiere iniciar sesión
    When intenta iniciar sesión con el correo "<correo>" y la contraseña "<contraseña>"
    Then el intento de acceso es rechazado y no puede ingresar a la plataforma

    Examples:
      | correo                  | contraseña               |
      | otro.correo@ejemplo.com | la contraseña registrada |
      | el correo registrado    | Incorrecta#2026          |
      | (vacío)                 | la contraseña registrada |
      | el correo registrado    | (vacío)                  |

  @acceso
  Scenario Outline: Identificación del rol del usuario tras iniciar sesión
    Given un usuario con rol "<rol>" inició sesión exitosamente
    When su cuenta es reconocida
    Then se identifica el rol "<rol>" asociado al usuario

    Examples:
      | rol       |
      | Cliente   |
      | Proveedor |

  @acceso @mfa
  Scenario: Solicitud de verificación adicional a cuenta administrativa
    Given un Administrador con verificación adicional activa ingresa su correo y contraseña correctos
    When intenta iniciar sesión sin el código de verificación
    Then se le solicita completar la verificación adicional

  @positivo @mfa
  Scenario: Acceso con verificación adicional correcta
    Given un Administrador con verificación adicional activa ingresa su correo y contraseña correctos
    When completa la verificación con el código vigente
    Then obtiene acceso

  @excepcion @mfa
  Scenario: Bloqueo de acceso con verificación adicional incorrecta
    Given un Administrador con verificación adicional activa ingresa su correo y contraseña correctos
    When envía un código de verificación incorrecto
    Then no obtiene acceso

  @acceso @mfa @pendiente
  Scenario: Bloqueo de operación sensible sin confirmación adicional
    Given un Administrador autenticado y un usuario con rol "Cliente"
    When intenta modificar los permisos de ese usuario sin la confirmación adicional
    Then no puede realizar la acción
