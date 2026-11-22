@HU02 @identity
Feature: Log in

  # Intentionally omitted: session expiration after one hour (it would require waiting or changing the clock),
  # protection of sensitive data in internal logs (verified in integration tests) and password change
  # (there is no equivalent operation in the API).

  @positivo @sesion
  Scenario: Successful login with valid credentials
    Given a registered client wants to log in
    When they provide their correct email and password
    Then they gain access

  @excepcion @sesion
  Scenario Outline: Login attempt with invalid credentials
    Given a registered client wants to log in
    When they try to log in with the email "<email>" and the password "<password>"
    Then the access attempt is rejected and they cannot enter the platform

    Examples:
      | email                    | password                 |
      | other.email@example.com  | the registered password  |
      | the registered email     | Wrong#2026               |
      | (empty)                  | the registered password  |
      | the registered email     | (empty)                  |

  @acceso
  Scenario Outline: Identification of the user's role after logging in
    Given a user with the role "<role>" has logged in successfully
    When their account is recognized
    Then the role "<role>" associated with the user is identified

    Examples:
      | role     |
      | Cliente   |
      | Proveedor |

  @acceso @mfa
  Scenario: Additional verification request for an administrative account
    Given an Administrator with additional verification enabled enters their correct email and password
    When they try to log in without the verification code
    Then they are asked to complete the additional verification

  @positivo @mfa
  Scenario: Access with correct additional verification
    Given an Administrator with additional verification enabled enters their correct email and password
    When they complete the verification with the current code
    Then they gain access

  @excepcion @mfa
  Scenario: Access blocked with incorrect additional verification
    Given an Administrator with additional verification enabled enters their correct email and password
    When they submit an incorrect verification code
    Then they do not gain access

  @acceso @mfa
  Scenario: Sensitive operation blocked without additional confirmation
    Given an authenticated Administrator and a user with the role "Cliente"
    When they try to change that user's permissions without the additional confirmation
    Then they cannot perform the action
