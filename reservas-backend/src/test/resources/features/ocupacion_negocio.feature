@HU30 @report
Feature: Check the occupancy of the business
  As a provider
  I want to see the occupancy of my business by day or by week
  So that I can identify the moments of highest and lowest demand and make decisions about my offer

  @positivo
  Scenario: View the occupancy of a day with bookings
    Given the business has active resources with a schedule that day
    And there are confirmed or completed bookings that take up minutes
    When the provider checks the occupancy of that day
    Then the result is the booked minutes divided by the available minutes times 100
    And the percentage is shown with one decimal

  @positivo
  Scenario: Day without available schedule
    Given the schedule of that day has no available minutes
    When the provider checks the occupancy of that day
    Then the system returns no occupancy percentage
    And it does not show 0 % but indicates that there is no data available

  @positivo
  Scenario: Day with a schedule but without bookings
    Given the provider has active resources with a current schedule that day
    And there are no confirmed or completed bookings that day
    When the provider checks the occupancy of that day
    Then the system shows 0 %

  @positivo
  Scenario: Inactive resources do not count
    Given the business has one active resource and one inactive resource
    When the provider checks the occupancy of that day
    Then only the available minutes of the active resource are taken into account

  @positivo
  Scenario: Query by day
    Given the provider is authenticated and has an active business
    When they request the occupancy of a specific day
    Then they receive the occupancy percentage of that day

  @positivo
  Scenario: Query by week
    Given the provider is authenticated and has an active business
    When they request the occupancy of a specific week
    Then they receive the occupancy percentage for each day of that week

  @positivo
  Scenario: Query grouped by week
    Given the provider is authenticated and has an active business
    When they request the occupancy of a specific week grouped by week
    Then they receive one occupancy percentage from Monday to Sunday

  @excepcion
  Scenario: Invalid period (start date after the end date)
    Given the provider is authenticated
    When they check the occupancy with a start date after the end date
    Then the system rejects the request with an invalid period error message

  @positivo
  Scenario: Period without data returns an empty report
    Given there are no bookings or resources with a schedule in the queried period
    When the provider checks the occupancy of that period
    Then the system returns the empty report with a message saying there is no data

  @excepcion @acceso
  Scenario: A provider cannot see the occupancy of the business of another provider
    Given there is a business that belongs to another provider
    When the provider tries to check its occupancy
    Then the system rejects the request with an unauthorized access error

  @excepcion @acceso
  Scenario: A client does not have access to the occupancy reports
    Given a user with the role "Cliente" has logged in
    When they try to check the occupancy of a business
    Then access is denied

  @excepcion @acceso
  Scenario: Access without a valid session
    Given a user has not logged in or their session has expired
    When they try to check the occupancy of a business
    Then access is denied
    And they must authenticate again
