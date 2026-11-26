@HU31 @report
Feature: Check the bookings by status
  As a provider
  I want to know how many bookings of my business are confirmed, cancelled and completed in a period of time
  So that I can evaluate how my operation is going

  # @hallazgo = the scenario follows the acceptance criteria but the current API does not meet it (see the comment).
  # Not automated: "Filter by predefined periods" (Today, Last 7 days, This month, Previous month). The user picks them
  # in the interface, which turns them into dates; the API only receives "from" and "to".

  Background:
    Given I am an authenticated provider on the platform
    And I have a registered business with active services

  @positivo
  Scenario: Check the summary of bookings by status in a valid period
    Given I have bookings of my business in the period "01/10/2026" to "31/10/2026"
    When I select that date range in the bookings by status query
    Then the system shows the number of "Confirmed" bookings
    And the system shows the number of "Cancelled" bookings
    And the system shows the number of "Completed" bookings
    And the system shows the total number of bookings of the period

  @positivo
  Scenario: See only the bookings of my business
    Given there are bookings of other providers on the platform
    When I check the bookings by status in a period
    Then the system only counts the bookings of my business
    And the system does not show information about bookings of other providers

  @positivo
  Scenario: Check a period without bookings
    Given I have no bookings in the selected period
    When I check the bookings by status
    Then the system shows zero in the "Confirmed", "Cancelled" and "Completed" statuses
    And the system shows the message "No hay reservas en el periodo seleccionado"

  @positivo
  Scenario: Status without bookings within a period with data
    Given I have confirmed and completed bookings in the selected period
    But I have no cancelled bookings
    When I check the bookings by status
    Then the system shows the value 0 in "Cancelled"
    And the system shows the corresponding amounts in the other statuses

  # The API wording is "El periodo no es valido: la fecha de inicio no puede ser posterior a la de fin".
  @excepcion
  Scenario: Period with a start date later than the end date
    When I enter a start date later than the end date
    Then the system says that the start date cannot be later than the end date
    And the system does not run the query

  # Finding: without a period the API answers the last 30 days (the rule of HU-29 and HU-32) instead of an error.
  @excepcion @hallazgo
  Scenario: Check without selecting a period
    When I try to check the bookings by status without defining a period
    Then the system shows the message "Debes seleccionar un periodo para consultar"
    And the system does not show results

  @positivo
  Scenario: Consistency of the statuses with the life cycle of the booking
    Given a booking changed status during the period
    When I check the bookings by status
    Then the system counts the booking only in its current status
    And the booking is not counted in more than one status at once

  @positivo
  Scenario: Update of the data after a status change
    Given I checked the summary of bookings by status
    When a client cancels a booking of my business
    And I check the same period again
    Then the number of "Cancelled" bookings increases by one
    And the number of the previous status decreases by one

  @excepcion @acceso
  Scenario: Access restricted to users without the provider role
    Given I am an authenticated client
    When I try to access the bookings by status query of the business
    Then access is denied
    And the system says that they do not have permission to do it

  # A REST API does not redirect: it answers 401 and the interface sends the user to the login screen.
  @excepcion @acceso
  Scenario: Access without a session
    Given I have not logged in
    When I try to access the bookings by status query
    Then the system redirects me to the login screen
