@HU05 @identity
Feature: Manage roles and permissions

  # Intentionally omitted: "History after deleting a user" (already verified in integration tests with the database).
  # The Administrator's operations require their additional confirmation (a current verification code).

  @positivo @mfa
  Scenario: Administrator assigns the "Administrador" role to a Client user
    Given an authenticated Administrator and a user with the role "Cliente"
    When they assign the role "Administrador" to that user with their additional confirmation
    Then the user's role is updated to "Administrador"

  # TO VERIFY against the API before automating: the block on the new Administrator until the additional verification is completed.
  @acceso @mfa
  Scenario: New Administrator without completed additional verification cannot access
    Given a user received the role "Administrador" and has not yet completed the creation of additional verification
    When they try to access as an Administrator
    Then access is denied until they complete the creation of additional verification

  @excepcion @mfa
  Scenario: Administrator tries to change the role of a Provider
    Given an authenticated Administrator and a user with the role "Proveedor"
    When they try to change that user's role with their additional confirmation
    Then the operation is rejected and the role does not change
    And they receive a message saying that the role of users with the role "Proveedor" cannot be changed

  @positivo @mfa
  Scenario Outline: Administrator deletes a user
    Given an authenticated Administrator and a user with the role "<role>"
    When they delete that user with their additional confirmation
    Then the user is completely deleted

    Examples:
      | role     |
      | Cliente   |
      | Proveedor |

  @acceso
  Scenario Outline: Deleted account without access
    Given a user with the role "<role>" was deleted
    When they try to log in with their previous credentials
    Then access is denied

    Examples:
      | role     |
      | Cliente   |
      | Proveedor |

  @excepcion @mfa
  Scenario: Administrator tries to delete an Administrator
    Given an authenticated Administrator and a user with the role "Administrador"
    When they try to delete that user with their additional confirmation
    Then the operation is rejected and the user is not deleted

  @excepcion @mfa
  Scenario: Administrator tries to assign a nonexistent role
    Given an authenticated Administrator and a user with the role "Cliente"
    When they try to assign the nonexistent role "Supervisor" with their additional confirmation
    Then the operation is rejected
    And they receive an error message saying the role does not exist

  # FINDING: Client and Provider get a generic 403 ("No tiene permisos para realizar esta operación"),
  # not the self-modification message the user story asks for. Only the Administrator gets "No puede modificar su propio rol".
  @acceso
  Scenario Outline: User tries to modify their own permissions or role
    Given an authenticated user with the role "<role>"
    When they try to modify their own permissions or role
    Then the operation is rejected
    And they receive an error message saying they cannot modify themselves

    Examples:
      | role          |
      | Administrador |

    @hallazgo
    Examples:
      | role     |
      | Cliente   |
      | Proveedor |
