@HU27 @reservation
Feature: Complete a booking

  # The API only has the statuses CONFIRMADA, CANCELADA and COMPLETADA: there is no "In progress" status,
  # so the scenario starts from a "Confirmed" booking.
  @positivo
  Scenario: Successful completion of the booked service
    Given the provider has a booking in status "Confirmed"
    And the date and time of the service have already passed
    When the provider marks the booking as "Completed"
    Then the status of the booking is updated to "Completed"

  # Rating or review of the service: Quality marked it as out of scope on 2026-11-06 (assumption P4 / D5).
  # It stays as @pendiente so it is not executed until a user story defines it.
  @pendiente
  Scenario: The client can rate the completed service
    Given the provider has a booking in status "Confirmed"
    And the date and time of the service have already passed
    When the provider marks the booking as "Completed"
    Then the option for the client to leave a rating or review is enabled
