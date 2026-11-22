@HU04 @identity
Feature: Log out

  @positivo @sesion
  Scenario: Successful logout
    Given a user has an active, authenticated session
    When they perform the log out action
    Then the logout is successful

  @excepcion @sesion
  Scenario: A closed session does not allow protected operations
    Given a user logged out successfully
    When they try to perform a protected operation with that session
    Then access is denied
    And they must log in again

  @excepcion @sesion
  Scenario: Logout without an active session
    Given a user has not logged in
    When they try to log out
    Then access is denied
