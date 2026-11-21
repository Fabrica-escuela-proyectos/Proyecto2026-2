@HU06 @identity
Feature: Acceso según el rol

  # Omitidos a propósito: "Permiso general del Proveedor no es suficiente para recursos ajenos" (es el mismo comportamiento
  # que "Proveedor intenta gestionar recursos de otro negocio") y el estado de sesión "expirada" (obliga a esperar una hora).

  @positivo @acceso
  Scenario: Cliente gestiona sus propios datos
    Given un usuario con rol "Cliente" ha iniciado sesión
    When intenta consultar un dato que le pertenece
    Then la operación es permitida

  @excepcion @acceso
  Scenario: Cliente intenta gestionar datos de otro usuario
    Given un usuario con rol "Cliente" ha iniciado sesión
    When intenta consultar un dato que no le pertenece
    Then se le deniega el acceso

  @positivo @acceso
  Scenario: Proveedor gestiona los recursos de su propio negocio
    Given un usuario con rol "Proveedor" ha iniciado sesión
    When intenta gestionar los recursos de su propio negocio
    Then la operación es permitida

  @excepcion @acceso
  Scenario: Proveedor intenta gestionar recursos de otro negocio
    Given un usuario con rol "Proveedor" ha iniciado sesión
    When intenta gestionar los recursos de otro proveedor
    Then se le deniega el acceso

  @excepcion @acceso
  Scenario Outline: Denegación de acceso a funcionalidades no autorizadas
    Given un usuario con rol "<rol>" ha iniciado sesión
    When intenta "<funcionalidad>"
    Then se le deniega el acceso

    Examples:
      | rol       | funcionalidad                          |
      | Cliente   | crear un recurso en un negocio         |
      | Cliente   | eliminar a otro usuario                |
      | Proveedor | asignar un rol a otro usuario          |

  @excepcion @sesion
  Scenario Outline: Bloqueo de operación protegida sin sesión válida
    Given un usuario cuenta con una sesión "<estado de sesión>"
    When intenta realizar una operación protegida
    Then se le deniega el acceso
    And debe autenticarse nuevamente

    Examples:
      | estado de sesión   |
      | revocada           |
      | sin iniciar sesión |
