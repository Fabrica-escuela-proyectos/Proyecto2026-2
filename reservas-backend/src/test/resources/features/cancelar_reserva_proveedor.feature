@HU26 @reservation
Feature: Cancel a booking as a provider

  @positivo
  Scenario: Successful cancellation of a booking by the provider
    Given a provider has a confirmed booking in their business
    When they cancel it giving a specific reason
    Then the booking is in status "Cancelled"
    And the reason given by the provider is recorded

  @positivo
  Scenario: The client sees the reason written by the provider
    Given a provider cancelled a booking giving a reason
    When the client checks their bookings
    Then they see the cancelled booking with the exact reason given by the provider

  @excepcion
  Scenario Outline: Cancellation without a valid reason
    Given a provider has a confirmed booking in their business
    When they try to cancel it with the reason "<reason>"
    Then the cancellation is rejected and the booking remains confirmed
    And they receive a message saying the reason is required

    Examples:
      | reason        |
      |               |
      | (only spaces) |

  @positivo
  Scenario: Cancellation with less than 1 hour of notice
    Given a confirmed booking of a business starts in less than 1 hour
    When its provider cancels it giving a reason
    Then the booking is in status "Cancelled"

  @excepcion
  Scenario Outline: Cancellation of a booking that is already cancelled or completed
    Given a provider has a booking in status "<status>"
    When the provider tries to cancel it
    Then the cancellation is rejected
    And they receive a message saying the booking cannot be cancelled in its current status

    Examples:
      | status    |
      | Cancelled |
      | Completed |

  @excepcion @acceso
  Scenario: Provider tries to cancel a booking of another business
    Given a provider has logged in
    When they try to cancel a booking that belongs to another business
    Then access is denied
    And the booking does not change

  @excepcion @acceso
  Scenario: Client tries to cancel as a provider
    Given a user with the role "Cliente" has logged in
    When they try to cancel a booking using the provider cancellation
    Then access is denied

  # An expired session cannot be simulated without waiting for the token to expire (1 hour): no session is used.
  @excepcion @sesion
  Scenario: Cancellation attempt without a valid session
    Given a user has not logged in or their session has expired
    When they try to cancel a booking of a business
    Then access is denied
    And they must authenticate again
