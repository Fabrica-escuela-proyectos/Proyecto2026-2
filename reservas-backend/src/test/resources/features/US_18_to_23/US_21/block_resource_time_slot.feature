@e2e @US21 @closure
Feature: Block a specific time slot of a resource
  As a Provider
  I want to close a resource on a specific date or time, such as maintenance or a holiday
  So that it does not receive bookings without changing its regular opening hours

  @positive
  Scenario: Closing a resource without affected bookings
    Given a Provider owns a resource with weekly opening hours
    When they close the resource from "14:00" to "18:00" on a future date
    Then the time slots in that period are no longer available
    And the weekly opening hours of the resource do not change

  @positive
  Scenario: Closing a resource with bookings requires confirmation
    Given a resource has a confirmed booking on a future date
    When the Provider tries to close the resource on that date without confirming
    Then they are informed of how many bookings will be cancelled
    And the booking remains confirmed

  @positive
  Scenario: Confirming the closure cancels the affected bookings
    Given a resource has a confirmed booking on a future date
    When the Provider closes the resource on that date and confirms the action
    Then the closure is saved
    And the booking is in status "Cancelled"
