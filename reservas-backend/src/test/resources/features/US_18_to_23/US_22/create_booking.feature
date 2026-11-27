@e2e @US22 @booking
Feature: Create a booking
  As a Client
  I want to book a service at an available time
  So that I can secure my spot

  @positive
  Scenario: Creating a booking successfully
    Given a Client wants to book a service with free time slots
    When they book the "10:00" time slot of a future date
    Then the booking is created with status "Confirmed"
    And that time slot is no longer available for other clients

  @negative
  Scenario: Booking a time slot that is already taken
    Given the "10:00" time slot of a service is already booked
    When another Client tries to book that time slot
    Then the booking is rejected
    And they are informed that the time slot is not available

  @negative
  Scenario: Booking outside the opening hours
    Given a service whose resource is open from "09:00" to "12:00"
    When a Client tries to book it from "13:00" to "14:00"
    Then the booking is rejected
    And they are informed that the time is outside the opening hours
