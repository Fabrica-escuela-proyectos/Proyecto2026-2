@HU28 @reservation
Feature: Cancel future bookings when a user is deleted

  @positivo @mfa
  Scenario: Deleting a client with future bookings
    Given a client has confirmed bookings on future dates
    When the administrator deletes their account
    Then their future bookings are in status "Cancelled"
    And the cancellation origin is "Account deletion"

  @positivo @mfa
  Scenario: The past bookings of the client are kept
    Given a client has past bookings
    When the administrator deletes their account
    Then their past bookings are kept unchanged in the history

  @positivo @mfa
  Scenario: Cancelled bookings are not deleted
    Given a client has confirmed bookings on future dates
    When the administrator deletes their account
    Then the cancelled bookings still exist in the history

  @positivo @mfa
  Scenario: Deleting a client with a booking that starts in less than 1 hour
    Given a client has a confirmed booking that starts in less than 1 hour
    When the administrator deletes their account
    Then the booking is in status "Cancelled"

  @positivo @mfa
  Scenario: An already cancelled booking keeps its original origin
    Given a client has a future booking cancelled with origin "Client"
    When the administrator deletes their account
    Then the booking keeps the origin "Client"

  @positivo @mfa
  Scenario: The time slot of a cancelled booking becomes available again
    Given the administrator deleted a client with a future booking
    When another client checks the availability of the service
    Then the time slot of the cancelled booking appears as free

  @positivo @mfa
  Scenario: Deleting a client without future bookings
    Given a client has no future bookings
    When the administrator deletes their account
    Then the account is deleted successfully

  @positivo @mfa
  Scenario: Deleting a provider with future bookings in their business
    Given a provider has confirmed bookings on future dates in their business
    When the administrator deletes their account
    Then the future bookings of their business are in status "Cancelled"
    And the cancellation origin is "Account deletion"

  @excepcion @mfa
  Scenario: The services of a deleted provider cannot be booked
    Given the administrator deleted the account of a provider
    When a client tries to book a service of that business
    Then the booking is not allowed

  @positivo @mfa
  Scenario: The past bookings of the business are kept
    Given a provider has past bookings in their business
    When the administrator deletes their account
    Then the past bookings of their business are kept unchanged in the history

  @excepcion @acceso
  Scenario Outline: User without the administrator role tries to delete an account
    Given a user with the role "<role>" has logged in
    When they try to delete the account of another user
    Then access is denied
    And the bookings of that user do not change

    Examples:
      | role      |
      | Cliente   |
      | Proveedor |

  # An expired session cannot be simulated without waiting for the token to expire (1 hour): no session is used.
  @excepcion @sesion
  Scenario: Deletion attempt without a valid session
    Given a user has not logged in or their session has expired
    When they try to delete an account
    Then access is denied
    And they must authenticate again
