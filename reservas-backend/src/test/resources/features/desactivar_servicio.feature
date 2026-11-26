@HU11 @services
Feature: Deactivate a service
  As a provider
  I want to stop offering a service
  so that nobody can book it

  @happy_path @positive
  Scenario: Deactivating a service without future bookings
    Given an active service has no future bookings
    When the provider asks to deactivate the service
    Then the service is deactivated
    And no new bookings are allowed for that service

  @exception
  Scenario: Deactivating a service with future bookings asks for confirmation
    Given an active service has 3 future bookings
    When the provider asks to deactivate the service
    Then the provider is told that 3 future bookings would be affected and must confirm before continuing
    And the service stays active

  @positive
  Scenario: Confirming the deactivation of a service with future bookings
    Given an active service has 3 future bookings
    When the provider asks to deactivate the service and confirms the operation
    Then the service is deactivated
    And the 3 future bookings are in status "Cancelada"
    And the reason of the cancellation is "servicio no disponible"

  @exception
  Scenario: The provider does not confirm the deactivation
    Given an active service has 3 future bookings
    And the provider asked to deactivate it and was told how many bookings would be affected
    When they do not confirm the operation
    Then the service stays active
    And the future bookings are not cancelled

  @positive
  Scenario: Past bookings stay in the history after the deactivation
    Given an active service has a completed booking in the past and a future booking
    When the provider asks to deactivate the service and confirms the operation
    Then the past booking keeps its status "Completada"
    And no booking is deleted

  @positive
  Scenario: A deactivated service is no longer shown to clients
    Given a service was deactivated successfully
    When a client checks the services of that business
    Then the service does not appear among the available services

  @exception
  Scenario: A deactivated service does not allow new bookings
    Given a service was deactivated successfully
    When a client tries to create a new booking for that service
    Then the booking is rejected
    And they receive a message saying the service is not available

  @positive
  Scenario: Deactivating a service that is already inactive
    Given a service was deactivated successfully
    When the provider asks to deactivate the service
    Then the service is deactivated

  @access
  Scenario: A provider tries to deactivate a service of another business
    Given a provider is logged in
    And there is a service that belongs to another provider
    When they try to deactivate that service
    Then access is denied
    And the status of the service does not change

  @access
  Scenario: A client tries to deactivate a service
    Given a user with the Client role is logged in
    When they try to deactivate a service
    Then access is denied

  @access
  Scenario: Attempt to deactivate a service without a valid session
    Given a user has not logged in
    When they try to deactivate a service
    Then access is denied
    And they must log in again
