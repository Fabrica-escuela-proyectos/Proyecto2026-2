@HU13 @catalog
Feature: View businesses and services

  @positivo
  Scenario: Viewing the list of businesses
    Given an authenticated Client wants to see the available businesses
    When they access the catalog
    Then they see the registered businesses
    And each business is identified by its name

  @positivo
  Scenario: Viewing the services of a business
    Given an authenticated Client selects a business with registered services
    When they check the details of the business
    Then they see the services it offers
    And each service includes name, description, duration and price

  @positivo
  Scenario: Viewing the catalog when there are no registered businesses
    Given an authenticated Client
    And no businesses are registered on the platform
    When they access the catalog
    Then they see an empty catalog
    And they are informed that there are no businesses available yet

  @positivo
  Scenario: Viewing a business without services
    Given a Client selects a business without registered services
    When they check the details of the business
    Then they do not see any services
    And they are informed that the business does not offer services yet

  @excepcion
  Scenario: Client tries to view a business that does not exist
    Given an authenticated Client requests a business that does not exist
    When they try to view its details
    Then they do not see any information
    And they are informed that the requested business does not exist

  # The platform is an API: "asked to authenticate again" means it answers 401.
  @excepcion @sesion
  Scenario: User without a valid session tries to view the catalog
    Given a user does not have a valid session
    When they try to view the catalog
    Then access is denied
    And they must authenticate again
