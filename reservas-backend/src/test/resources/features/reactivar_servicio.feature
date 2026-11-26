@HU12 @services
Feature: Reactivate a service
  As a provider
  I want to offer a deactivated service again
  to make it available without having to create it again

  @happy_path @positive
  Scenario: Successful reactivation of a service
    Given a deactivated service keeps its registered information
    When the provider asks to reactivate the service
    Then the service is in status "Active"
    And the service is available for new bookings again

  @positive
  Scenario: Reactivating without creating the service again
    Given a deactivated service keeps its registered information
    When the provider reactivates it
    Then it is the same service with its registered information
    And no duplicate service is created

  @positive
  Scenario: Reactivation after a deactivation that cancelled bookings
    Given a service was deactivated and its future bookings were cancelled with the reason "servicio no disponible"
    When the provider reactivates it
    Then the service is in status "Active"
    And the bookings cancelled before stay in status "Cancelada" and are not restored automatically

  @positive
  Scenario: A reactivated service is shown to clients again
    Given a service was reactivated successfully
    When a client checks the services of that business
    Then the service appears again among the available services
    And a new booking can be made if it meets the availability and advance notice rules

  @positive
  Scenario: Reactivating a service that is already active
    Given a provider has a service in their business
    When the provider reactivates it
    Then the service is in status "Active"
    And no duplicate service is created

  @access
  Scenario: A provider tries to reactivate a service of another business
    Given a provider is logged in
    And there is a deactivated service that belongs to another provider
    When they try to reactivate that service
    Then access is denied
    And the service stays deactivated

  @access
  Scenario: A client tries to reactivate a service
    Given a user with the Client role is logged in
    When they try to reactivate a service
    Then access is denied

  @access
  Scenario: Attempt to reactivate a service without a valid session
    Given a user has not logged in
    When they try to reactivate a service
    Then access is denied
    And they must log in again
