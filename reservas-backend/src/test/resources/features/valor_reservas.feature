@HU33 @report
Feature: View the value of the bookings

  # "Executed" bookings are the ones in status COMPLETADA. The API answers completedValue, confirmedValue,
  # cancelledValue and totalValue (= completed + confirmed); "the total value" is checked against totalValue.
  # @hallazgo = the scenario follows the acceptance criteria but the current API does not meet it (see the comment).

  # Finding: the response does not include how many bookings the total includes.
  @positivo @hallazgo
  Scenario: Successful query of the total value of a period
    Given a provider has logged in and owns a business
    And their business has executed bookings between "2026-10-01" and "2026-10-31"
    When they query the total value of the bookings of that period
    Then the system shows the sum of the price stored in each executed booking
    And it shows how many bookings the total includes

  @positivo
  Scenario: The total uses the price stored in the booking and not the current price of the service
    Given a client booked a service when it cost "$30.000"
    And the booking was already executed
    And afterwards the provider changed the price of the service to "$40.000"
    When the provider queries the total value of the period of that booking
    Then the system adds "$30.000" for that booking

  # Finding: the response does not include how many bookings the total includes.
  @positivo @hallazgo
  Scenario: Cancelled bookings do not add to the total
    Given the queried period has executed bookings and cancelled bookings
    When the provider queries the total value of that period
    Then the system adds only the executed bookings
    And the cancelled bookings are not included in the total or in the count

  @positivo
  Scenario: Query of a period without bookings
    Given the business of the provider has no bookings between "2026-10-01" and "2026-10-07"
    When they query the total value of that period
    Then the system shows a total value of "$0"
    And it reports that there were no executed bookings in the queried period

  # Finding: totalValue also adds the confirmed bookings that have not happened yet, and no message is shown.
  @positivo @hallazgo
  Scenario: Query of a period that has not happened yet
    Given the provider has confirmed bookings for next week
    When they query the total value of that period
    Then the system shows a total value of "$0"
    And it reports that the period has no executed bookings yet

  # Finding: totalValue also adds the confirmed bookings whose end time has not passed.
  @positivo @hallazgo
  Scenario: Query of a period in progress
    Given the queried period includes past days and future days
    And the period has executed bookings and bookings still to happen
    When the provider queries the total value
    Then the system adds only the bookings whose end time has already passed

  @excepcion
  Scenario Outline: Period with invalid dates
    Given a provider is querying the total value of their business
    When they enter "<start>" as the start date and "<end>" as the end date
    Then the system rejects the query
    And it reports that the period is not valid

    Examples:
      | start      | end        |
      | 2026-10-31 | 2026-10-01 |
      | 32/13/2026 | 2026-10-31 |
      | abc        | 2026-10-31 |

  @excepcion @acceso
  Scenario: Provider tries to view the total of another business
    Given a provider has logged in
    When they try to query the total value of a business that belongs to another provider
    Then access is denied

  @excepcion @acceso
  Scenario: Client tries to view the total of a business
    Given a user with the role "Cliente" has logged in
    When they try to query the total value of the bookings of a business
    Then access is denied
