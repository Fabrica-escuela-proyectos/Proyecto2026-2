@e2e @US20 @availability
Feature: Check the availability of a service
  As a Client
  I want to know the free time slots of a service
  So that I can choose the one that suits me best

  @positive
  Scenario: Checking the free time slots of a service
    Given a service has opening hours and no bookings on a future date
    When a Client checks its availability for that date
    Then they see every time slot defined for that day

  @positive
  Scenario: Booked time slots are not shown as free
    Given a service has a booking at "10:00" on a future date
    When a Client checks its availability for that date
    Then the "10:00" time slot is not shown as free

  @negative
  Scenario: Checking a day without opening hours
    Given a service has no opening hours on Sundays
    When a Client checks its availability for next Sunday
    Then they are informed that there are no time slots available that day
