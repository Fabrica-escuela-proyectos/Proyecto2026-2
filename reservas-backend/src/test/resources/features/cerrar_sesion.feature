@HU04 @identity
Feature: Cerrar sesión

  # Omitidos a propósito: expiración automática de la sesión (obliga a esperar una hora) y fallo de conexión al cerrar
  # (no se puede provocar por la API). Revisar con el equipo: la HU habla de invalidar "todos los dispositivos asociados"
  # pero la documentación del endpoint dice que se revoca la sesión actual.

  @positivo @sesion
  Scenario: Cierre de sesión exitoso
    Given un usuario tiene una sesión activa y autenticada
    When ejecuta la acción de cerrar sesión
    Then el cierre de sesión es exitoso

  @excepcion @sesion
  Scenario: Sesión cerrada no permite operaciones protegidas
    Given un usuario cerró su sesión exitosamente
    When intenta realizar una operación protegida con esa sesión
    Then se le deniega el acceso
    And debe iniciar sesión nuevamente

  @excepcion @sesion
  Scenario: Cierre de sesión sin una sesión activa
    Given un usuario no ha iniciado sesión
    When intenta cerrar sesión
    Then se le deniega el acceso
