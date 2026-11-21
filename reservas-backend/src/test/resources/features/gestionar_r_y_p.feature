@HU05 @identity
Feature: Gestionar roles y permisos

  # Omitido a propósito: "Historial tras eliminar un usuario" (ya se verifica en integración con la base de datos).
  # Las operaciones del Administrador exigen su confirmación adicional (código de verificación vigente).

  @positivo @mfa
  Scenario: Administrador asigna el rol "Administrador" a un usuario Cliente
    Given un Administrador autenticado y un usuario con rol "Cliente"
    When le asigna el rol "Administrador" a ese usuario con su confirmación adicional
    Then el rol del usuario queda actualizado a "Administrador"

  # VERIFICAR contra la API antes de automatizar: el bloqueo del nuevo Administrador hasta completar la verificación adicional.
  @acceso @mfa
  Scenario: Nuevo Administrador sin verificación adicional completada no puede acceder
    Given un usuario recibió el rol "Administrador" y aún no completa la creación de la verificación adicional
    When intenta acceder como Administrador
    Then se le deniega el acceso hasta que complete la creación de la verificación adicional

  @excepcion @mfa
  Scenario: Administrador intenta cambiar el rol de un Proveedor
    Given un Administrador autenticado y un usuario con rol "Proveedor"
    When intenta cambiarle el rol a ese usuario con su confirmación adicional
    Then la operación es rechazada y el rol no cambia
    And recibe un mensaje indicando que a los usuarios con rol "Proveedor" no se les puede cambiar el rol

  @positivo @mfa
  Scenario Outline: Administrador elimina un usuario
    Given un Administrador autenticado y un usuario con rol "<rol>"
    When elimina a ese usuario con su confirmación adicional
    Then el usuario queda eliminado en su totalidad

    Examples:
      | rol       |
      | Cliente   |
      | Proveedor |

  @acceso
  Scenario Outline: Cuenta eliminada sin acceso
    Given un usuario con rol "<rol>" fue eliminado
    When intenta iniciar sesión con sus credenciales anteriores
    Then se le deniega el acceso

    Examples:
      | rol       |
      | Cliente   |
      | Proveedor |

  @excepcion @mfa
  Scenario: Administrador intenta eliminar a un Administrador
    Given un Administrador autenticado y un usuario con rol "Administrador"
    When intenta eliminar a ese usuario con su confirmación adicional
    Then la operación es rechazada y el usuario no es eliminado

  @excepcion @mfa
  Scenario: Administrador intenta asignar un rol inexistente
    Given un Administrador autenticado y un usuario con rol "Cliente"
    When intenta asignarle el rol inexistente "Supervisor" con su confirmación adicional
    Then la operación es rechazada
    And recibe un mensaje de error indicando que el rol no existe

  # VERIFICAR el mensaje que reciben Cliente y Proveedor (solo el Administrador puede cambiar roles).
  @acceso
  Scenario Outline: Usuario intenta modificar sus propios permisos o rol
    Given un usuario autenticado con rol "<rol>"
    When intenta modificar sus propios permisos o rol
    Then la operación es rechazada
    And recibe un mensaje de error indicando que no puede modificarse a sí mismo

    Examples:
      | rol           |
      | Cliente       |
      | Proveedor     |
      | Administrador |
