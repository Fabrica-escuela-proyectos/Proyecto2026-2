@HU34 @report
Feature: Check the platform summary
  As an administrator
  I want to see a summary with the active businesses, the bookings by status and the overall occupancy
  So that I can supervise the use of the platform

  # @hallazgo = the scenario follows the acceptance criteria but the current API does not meet it (see the comment).
  # The summary adds up every business of the platform, so each scenario puts its bookings on a day of its own.

  @positivo
  Scenario: Check the platform summary
    Given an authenticated Administrator wants to supervise the platform
    And the platform has confirmed, cancelled and completed bookings in a period
    When they check the summary of a valid period
    Then the system shows the number of active businesses
    And it shows the confirmed, cancelled and completed bookings
    And it shows the overall occupancy as a percentage

  @positivo
  Scenario: Check a period of a single day
    Given an authenticated Administrator wants to check a single day
    When they enter the same date as the start and the end
    Then the system shows the summary of that day

  @positivo
  Scenario: Check a period without bookings
    Given an authenticated Administrator wants to supervise the platform
    And there are no bookings in the indicated period
    When the Administrator checks the summary
    Then the system shows the bookings at zero and the occupancy at 0 %
    And it reports that there are no bookings in the period

  # Finding: the occupancy is calculated with every active resource, also those of businesses whose provider is
  # disabled, so with 0 active businesses there is still a percentage and no message about the available hours.
  # Warning: this scenario disables every provider of the test database.
  @positivo @hallazgo
  Scenario: Check when there are no active businesses
    Given an authenticated Administrator wants to supervise the platform
    And the platform has no active businesses
    When the Administrator checks the summary
    Then the system shows 0 active businesses
    And it does not calculate an occupancy percentage
    And it reports that there are no available hours

  @positivo
  Scenario: Cancelled bookings do not count as occupancy
    Given an authenticated Administrator wants to supervise the platform
    And there are cancelled bookings in the period
    When the Administrator checks the summary
    Then the cancelled bookings are counted in their status
    And they are not added to the overall occupancy

  @positivo
  Scenario: A business whose provider is disabled is not counted as active
    Given an authenticated Administrator wants to supervise the platform
    And the Provider of a business has a disabled account
    When the Administrator checks the summary
    Then that business is not included among the active businesses

  @excepcion
  Scenario: Try to check with an invalid period
    Given an authenticated Administrator wants to check the summary
    When they enter a start date later than the end date
    Then the system does not calculate the summary
    And it reports that the period is invalid

  @excepcion
  Scenario Outline: Try to check with a date in a format that is not valid
    Given an authenticated Administrator wants to check the summary
    When they enter "<start>" as the start and "<end>" as the end of the summary
    Then the system does not calculate the summary
    And it reports that the period is invalid

    Examples:
      | start      | end        |
      | 32/13/2026 | 2026-10-31 |
      | 2026-10-01 | abc        |

  # Finding: when one date is missing the API fills in the other one with 30 days and answers the summary.
  @excepcion @hallazgo
  Scenario Outline: Try to check without indicating the complete period
    Given an authenticated Administrator wants to check the summary
    When they enter "<start>" as the start and "<end>" as the end of the summary
    Then the system does not calculate the summary
    And it reports that the "<missing>" date is missing

    Examples:
      | start      | end        | missing |
      | (empty)    | 2026-10-31 | inicio  |
      | 2026-10-01 | (empty)    | fin     |

  @excepcion @acceso
  Scenario: Try to check as a Provider
    Given a user with the role "Proveedor" has logged in
    When they try to check the platform summary
    Then access is denied
    And no data of the platform is shown

  @excepcion @acceso
  Scenario: Try to check as a Client
    Given a user with the role "Cliente" has logged in
    When they try to check the platform summary
    Then access is denied

  @excepcion @acceso
  Scenario: Try to check without a valid session
    Given a user does not have a valid session
    When they try to check the platform summary
    Then access is denied
    And they must authenticate again

  @excepcion @acceso @mfa
  Scenario: Try to check as an Administrator without MFA enabled
    Given an Administrator has not enabled two-step authentication
    When they try to check the platform summary
    Then access is denied
    And they are told that they must enable MFA first
