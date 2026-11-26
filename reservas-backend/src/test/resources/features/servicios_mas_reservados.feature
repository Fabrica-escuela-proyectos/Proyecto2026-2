@HU32 @report
Feature: Check the most booked services
  As a provider
  I want to see which services of my business are booked the most
  So that I can decide which ones to promote or adjust

  # @hallazgo = the scenario follows the acceptance criteria but the current API does not meet it (see the comment).

  @positivo
  Scenario: Successful query of the most booked services
    Given a provider has logged in and offers the service "Consulta general"
    And they have "Consulta general" with 12 bookings and "Limpieza dental" with 5 bookings from "3/11/2026" to "5/11/2026"
    When they check the most booked services from "3/11/2026" to "5/11/2026"
    Then they first see "Consulta general" with 12 bookings and then "Limpieza dental" with 5 bookings

  @positivo
  Scenario: Check the most booked services without choosing a period
    Given a provider has logged in and offers the service "Consulta general"
    When they check the most booked services without choosing dates
    Then they see the number of bookings of their services calculated over the last 30 days

  @positivo
  Scenario Outline: Bookings that count according to their status
    Given a provider has logged in and offers the service "Consulta general"
    And they have a booking of the service "Consulta general" in status "<status>"
    When they check the most booked services of that period
    Then they see "Consulta general" with "<amount>" bookings

    Examples:
      | status    | amount |
      | Confirmed | 1      |
      | Completed | 1      |
      | Cancelled | 0      |

  @positivo
  Scenario: Services with the same number of bookings
    Given a provider has logged in and offers the service "Consulta general"
    And they have "Limpieza dental" and "Consulta general" with 5 bookings each in the period
    When they check the most booked services of that period
    Then they first see "Consulta general" and then "Limpieza dental"

  # Finding: the ranking is built from the bookings, so an active service without bookings does not appear.
  @positivo @hallazgo
  Scenario: Active service without bookings in the period
    Given a provider has logged in and offers the service "Consulta general"
    And their service "Ortodoncia" is active and has no bookings in the period
    When they check the most booked services of that period
    Then they see "Ortodoncia" with 0 bookings at the end of the list

  @positivo
  Scenario: Deactivated service without bookings in the period
    Given a provider has logged in and offers the service "Consulta general"
    And they have "Consulta general" active and "Ortodoncia" deactivated without bookings in the period
    When they check the most booked services of that period
    Then they only see the service "Consulta general" in the result

  @positivo
  Scenario: Deactivated service with bookings in the period
    Given a provider has logged in and offers the service "Consulta general"
    And their service "Ortodoncia" was deactivated but has 3 confirmed bookings in the period
    When they check the most booked services of that period
    Then they see "Ortodoncia" with 3 bookings in the result

  @positivo
  Scenario: Period without bookings
    Given a provider has logged in and offers the service "Consulta general"
    And they have no confirmed or completed bookings in the period
    When they check the most booked services of that period
    Then they see the empty report
    And they receive a message saying there is no data in the period

  @excepcion
  Scenario Outline: Invalid query period
    Given a provider has logged in and offers the service "Consulta general"
    When they check the most booked services with start "<start>" and end "<end>"
    Then the query is rejected
    And they receive a message saying the query period is invalid

    Examples:
      | start      | end        |
      | 15/11/2026 | 3/11/2026  |
      | 1/1/2026   | 5/1/2027   |
      | 31/02/2026 | 15/11/2026 |

  @excepcion @acceso
  Scenario: Provider tries to check the most booked services of another business
    Given a provider has logged in
    When they try to check the most booked services of a business that belongs to another provider
    Then access is denied and they do not see any data of that business

  @excepcion @acceso
  Scenario: Client tries to check the most booked services
    Given a user with the role "Cliente" has logged in
    When they try to check the most booked services of a business
    Then access is denied

  @excepcion @acceso
  Scenario: Query attempt without a valid session
    Given a user has not logged in or their session has expired
    When they try to check the most booked services of a business
    Then access is denied
    And they must authenticate again
