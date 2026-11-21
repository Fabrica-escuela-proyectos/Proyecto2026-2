@HU03 @identity
Feature: Registro de proveedor de servicios

  # Los casos de correo inválido, celular inválido, contraseña y campos comunes son iguales a los de HU-01
  # y no se repiten aquí; solo se incluyen los comportamientos propios del proveedor.

  @positivo
  Scenario: Registro exitoso de proveedor con datos válidos
    Given un visitante no registrado quiere crear una cuenta de proveedor
    When completa todos los datos personales y del negocio con información válida y envía la solicitud
    Then la cuenta del proveedor queda creada exitosamente
    And la información del negocio queda registrada y asociada a esa cuenta

  @positivo
  Scenario: Asignación del rol Proveedor
    Given un visitante no registrado quiere crear una cuenta de proveedor
    When termina el registro con datos válidos
    Then tiene el rol "Proveedor" de forma controlada

  @excepcion
  Scenario: Registro de proveedor con correo ya existente
    Given ya existe una cuenta de proveedor registrada
    When un visitante intenta registrarse como proveedor con el mismo correo de esa cuenta
    Then la solicitud es rechazada y no se crea una cuenta duplicada
    And recibe un mensaje indicando que el correo ya está en uso

  @excepcion
  Scenario: Registro de proveedor sin nombre del negocio
    Given un visitante no registrado quiere crear una cuenta de proveedor
    When deja vacío el campo "nombre del negocio" y envía la solicitud
    Then la solicitud es rechazada y no se crea ninguna cuenta
    And recibe un mensaje de error específico para el campo "nombre del negocio"

  @acceso
  Scenario: El proveedor no puede autoasignarse un rol privilegiado
    Given un visitante no registrado quiere crear una cuenta de proveedor
    When intenta registrarse con el rol "Administrador"
    Then el rol solicitado es ignorado y se le asigna el rol "Proveedor" de forma controlada
    And la cuenta queda registrada únicamente con el rol autorizado
