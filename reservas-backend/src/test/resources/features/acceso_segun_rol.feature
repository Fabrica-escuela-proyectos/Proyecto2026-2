@HU06 @identity
Feature: Access according to role

  # Intentionally omitted: "The Provider's general permission is not enough for someone else's resources" (it is the same
  # behavior as "Provider tries to manage resources of another business") and the "expired" session state (it would require waiting one hour).

  @positivo @acceso
  Scenario: Client manages their own data
    Given a user with the role "Cliente" has logged in
    When they try to view data that belongs to them
    Then the operation is allowed

  @excepcion @acceso
  Scenario: Client tries to manage another user's data
    Given a user with the role "Cliente" has logged in
    When they try to view data that does not belong to them
    Then access is denied

  @positivo @acceso
  Scenario: Provider manages the resources of their own business
    Given a user with the role "Proveedor" has logged in
    When they try to manage the resources of their own business
    Then the operation is allowed

  @excepcion @acceso
  Scenario: Provider tries to manage resources of another business
    Given a user with the role "Proveedor" has logged in
    When they try to manage the resources of another provider
    Then access is denied

  @excepcion @acceso
  Scenario Outline: Access denied to features not authorized for the role
    Given a user with the role "<role>" has logged in
    When they try to "<feature>"
    Then access is denied

    Examples:
      | role     | feature                          |
      | Cliente   | create a resource in a business  |
      | Cliente   | delete another user              |
      | Proveedor | assign a role to another user    |

  @excepcion @sesion
  Scenario Outline: Protected operation blocked without a valid session
    Given a user has a session that is "<session status>"
    When they try to perform a protected operation
    Then access is denied
    And they must authenticate again

    Examples:
      | session status |
      | revoked        |
      | not logged in  |
