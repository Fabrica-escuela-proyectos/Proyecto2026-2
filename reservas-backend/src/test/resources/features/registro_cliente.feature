@HU01 @identity
Feature: Registro de cliente

  # Omitidos a propósito: "Acceso del usuario tras el registro" y "Protección de la contraseña y los datos sensibles"
  # (el primero es ambiguo para una API y el segundo se verifica en pruebas de integración y en la base de datos).

  @positivo
  Scenario: Registro exitoso con datos válidos
    Given un visitante no registrado quiere crear una cuenta de cliente
    When completa todos los datos con información válida y envía la solicitud
    Then la cuenta queda creada exitosamente
    And tiene el rol "Cliente" por defecto

  @excepcion
  Scenario: Registro con correo ya existente
    Given ya existe una cuenta de cliente registrada
    When un visitante intenta registrarse con el mismo correo de esa cuenta
    Then la solicitud es rechazada y no se crea una cuenta duplicada
    And recibe un mensaje indicando que el correo ya está en uso

  @excepcion
  Scenario: Registro con formato de correo inválido
    Given un visitante no registrado quiere crear una cuenta de cliente
    When ingresa el correo "usuario@@dominio" y envía la solicitud
    Then la solicitud es rechazada y no se crea ninguna cuenta
    And recibe un mensaje de error indicando que el formato del correo no es válido

  @excepcion
  Scenario Outline: Número de celular con formato inválido
    Given un visitante tiene nombre, correo y contraseña válidos
    When envía la solicitud con el celular "<celular>"
    Then la solicitud es rechazada y no se crea ninguna cuenta
    And recibe un mensaje de error indicando que el número de celular no es válido

    Examples:
      | celular    |
      | abcde12345 |
      | 12345      |
      | 123        |
      | 1234567890 |

  @excepcion
  Scenario: Registro con número de celular ya registrado en otra cuenta
    Given ya existe una cuenta de cliente registrada
    When un visitante intenta registrarse con el mismo celular de esa cuenta
    Then la solicitud es rechazada y no se crea una cuenta duplicada
    And recibe un mensaje indicando que el número de celular ya está en uso

  @positivo
  Scenario Outline: Contraseña válida con un carácter especial permitido
    Given un visitante tiene nombre, correo y celular válidos
    When envía la contraseña "<contraseña>" que incluye el carácter especial "<carácter especial>"
    Then la cuenta queda creada exitosamente

    Examples:
      | carácter especial | contraseña  |
      | ¡                 | Segura¡2026 |
      | *                 | Segura*2026 |
      | +                 | Segura+2026 |
      | °                 | Segura°2026 |

  @excepcion
  Scenario Outline: Contraseña que no cumple la política de seguridad
    Given un visitante tiene nombre, correo y celular válidos
    When envía la contraseña "<contraseña>" que incumple el requisito "<requisito>"
    Then la solicitud es rechazada y no se crea ninguna cuenta
    And recibe un mensaje indicando el requisito de seguridad no cumplido

    Examples:
      | requisito                              | contraseña    |
      | contener al menos una mayúscula        | clave#segura1 |
      | contener al menos una minúscula        | CLAVE#SEGURA1 |
      | tener mínimo 8 caracteres              | Ab#xyz1       |
      | contener al menos un carácter especial | ClaveSegura1  |

  @excepcion
  Scenario Outline: Campo obligatorio faltante
    Given un visitante no registrado quiere crear una cuenta de cliente
    When deja vacío el campo "<campo>" y envía la solicitud
    Then la solicitud es rechazada y no se crea ninguna cuenta
    And recibe un mensaje de error específico para el campo "<campo>"

    Examples:
      | campo              |
      | nombre completo    |
      | correo electrónico |
      | número de celular  |
      | contraseña         |

  # Este escenario necesita el límite de producción (5 intentos). Se ejecuta aparte del resto: ver etiqueta @rate-limit.
  @acceso @rate-limit
  Scenario: Bloqueo temporal por múltiples intentos de registro
    Given un mismo origen realizó 5 intentos de registro en poco tiempo
    When intenta registrarse nuevamente
    Then se bloquean temporalmente nuevas solicitudes desde ese origen
