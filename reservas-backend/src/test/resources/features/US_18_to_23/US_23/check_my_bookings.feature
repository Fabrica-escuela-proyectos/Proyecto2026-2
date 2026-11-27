@e2e @US23 @booking
Feature: Check my bookings
  As a Client
  I want to see my active bookings and my history, with their status and cancellation reason if any
  So that I know what I have scheduled

  @positive
  Scenario: Checking my active bookings and history
    Given a Client has a confirmed booking and a cancelled booking
    When they check their bookings
    Then they see both bookings with their date, time, service and status
    And the cancelled booking shows its cancellation reason

  @positive
  Scenario: Checking my bookings when I have none
    Given a Client has not made any booking
    When they check their bookings
    Then they are informed that they have no bookings

  @negative
  Scenario: Client tries to see the bookings of another client
    Given a Client is logged in
    When they try to see the bookings of another client
    Then access is denied
