@HU25 @reservation
Feature: Cancel a booking as a client

  # "Cancellation origin" is the field cancelOrigin of the API (CLIENTE, PROVEEDOR, ELIMINACION_CUENTA...).
  @positivo
  Scenario: Successful cancellation with enough notice
    Given a client has a confirmed booking that starts in more than 1 hour
    When the client cancels the booking
    Then the booking is in status "Cancelled"
    And the cancellation origin is "Client"

  @positivo
  Scenario: The cancelled time slot becomes available again
    Given a client cancelled a booking with enough notice
    When another client checks the availability of the service
    Then the cancelled time slot appears as free

  @excepcion
  Scenario: Cancellation with less than 1 hour of notice
    Given a client has a confirmed booking that starts in less than 1 hour
    When the client tries to cancel it
    Then the cancellation is rejected and the booking remains confirmed
    And they receive a message saying a booking can only be cancelled with at least 1 hour of notice

  @excepcion
  Scenario Outline: Cancellation of a booking that is already cancelled or completed
    Given a client has a booking in status "<status>"
    When the client tries to cancel it
    Then the cancellation is rejected
    And they receive a message saying the booking cannot be cancelled in its current status

    Examples:
      | status    |
      | Cancelled |
      | Completed |

  @excepcion @acceso
  Scenario: Client tries to cancel the booking of another client
    Given a client has logged in
    When they try to cancel a booking that belongs to another client
    Then access is denied
    And the booking does not change

  # An expired session cannot be simulated without waiting for the token to expire (1 hour): no session is used.
  @excepcion @sesion
  Scenario: Cancellation attempt without a valid session
    Given a user has not logged in or their session has expired
    When they try to cancel a booking
    Then access is denied
    And they must authenticate again
