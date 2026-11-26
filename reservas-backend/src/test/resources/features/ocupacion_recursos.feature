@HU29 @report
Feature: Check the occupancy of the resources
  As a provider
  I want to see what percentage of the available time of each of my resources is booked
  So that I know which ones are used the most and which ones have free hours

  # @hallazgo = the scenario follows the acceptance criteria but the current API does not meet it (see the comment).

  @positivo
  Scenario: Successful query of the occupancy of my resources
    Given a provider has logged in and their resource "Consultorio 1" serves from "10:00" to "15:00"
    And they have "Consultorio 1" with 80 % of its time booked and "Consultorio 2" with 20 % from "3/11/2026" to "5/11/2026"
    When they check the occupancy of their resources from "3/11/2026" to "5/11/2026"
    Then they first see "Consultorio 1" with 80 % usage and then "Consultorio 2" with 20 % usage

  @positivo
  Scenario: Check the occupancy without choosing a period
    Given a provider has logged in and their resource "Consultorio 1" serves from "10:00" to "15:00"
    When they check the occupancy of their resources without choosing dates
    Then they see the usage percentage of their resources calculated over the last 30 days

  @positivo
  Scenario: A one-off closure reduces the service time of the resource
    Given a provider has logged in and their resource "Consultorio 1" serves from "10:00" to "15:00"
    And they closed the resource from "10:00" to "12:00" that same day
    When they check the occupancy of that day
    Then they see "Consultorio 1" calculated over the 3 hours of service that remained

  @positivo
  Scenario Outline: Bookings that count as booked time according to their status
    Given a provider has logged in and their resource "Consultorio 1" serves from "10:00" to "15:00"
    And they have a 1 hour booking in status "<status>"
    When they check the occupancy of that day
    Then they see "Consultorio 1" with "<percentage>" usage

    Examples:
      | status    | percentage |
      | Confirmed | 20 %       |
      | Completed | 20 %       |
      | Cancelled | 0 %        |

  # Finding: the available time counts every day of the period, also the days before the resource was created.
  @positivo @hallazgo
  Scenario: Resource created within the queried period
    Given a provider has logged in and their resource "Consultorio 1" serves from "10:00" to "15:00"
    And they created "Consultorio 3" halfway through the period they are going to check
    When they check the occupancy of that period
    Then they see the percentage of "Consultorio 3" calculated only from the day it was created

  @positivo
  Scenario: Resource with a schedule and no bookings in the period
    Given a provider has logged in and their resource "Consultorio 1" serves from "10:00" to "15:00"
    And their resource "Consultorio 1" has a schedule and no bookings in the period
    When they check the occupancy of their resources
    Then they see "Consultorio 1" with 0 % usage

  # Finding: the resource comes without a percentage, but the report only has a message when no resource of the
  # business has a schedule; there is no message for each resource.
  @positivo @hallazgo
  Scenario: Resource without a defined schedule
    Given a provider has logged in and their resource "Consultorio 1" serves from "10:00" to "15:00"
    And their resource "Consultorio 2" does not have a defined schedule
    When they check the occupancy of their resources
    Then they see "Consultorio 2" without a usage percentage
    And they receive a message saying the resource does not have a defined schedule

  @excepcion
  Scenario Outline: Invalid query period
    Given a provider has logged in and their resource "Consultorio 1" serves from "10:00" to "15:00"
    When they check the occupancy of their resources with start "<start>" and end "<end>"
    Then the query is rejected
    And they receive a message saying the query period is invalid

    Examples:
      | start      | end        |
      | 15/11/2026 | 3/11/2026  |
      | 1/1/2026   | 5/1/2027   |
      | 31/02/2026 | 15/11/2026 |

  @positivo
  Scenario: Deactivated resources do not appear in the query
    Given a provider has logged in and their resource "Consultorio 1" serves from "10:00" to "15:00"
    And they have "Consultorio 1" active and "Consultorio 2" deactivated
    When they check the occupancy of their resources
    Then they only see "Consultorio 1" in the result

  @excepcion @acceso
  Scenario: Provider tries to check the occupancy of another business
    Given a provider has logged in
    When they try to check the occupancy of the resources of a business that belongs to another provider
    Then access is denied and they do not see any data of that business

  @excepcion @acceso
  Scenario: Client tries to check the occupancy of the resources
    Given a user with the role "Cliente" has logged in
    When they try to check the occupancy of the resources of a business
    Then access is denied

  @excepcion @acceso
  Scenario: Query attempt without a valid session
    Given a user has not logged in or their session has expired
    When they try to check the occupancy of the resources of a business
    Then access is denied
    And they must authenticate again
