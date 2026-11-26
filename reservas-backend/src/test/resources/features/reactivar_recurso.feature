@HU17 @resource
Feature: Reactivate resource

  @positivo
  Scenario: Successfully reactivating a deactivated resource
    Given an authenticated Provider manages a resource of their own business
    And the resource is currently deactivated
    When they request to reactivate the resource
    Then the resource becomes active
    And it is available again to receive bookings

  @positivo
  Scenario: Reactivating a resource without registering it again
    Given an authenticated Provider
    And an existing resource of their own business is currently deactivated
    When they request to reactivate the resource
    Then the existing resource is reactivated
    And it is available again to receive bookings

  @excepcion @acceso
  Scenario: Provider tries to reactivate a resource of another business
    Given an authenticated Provider
    And a deactivated resource that belongs to another business
    When they request to reactivate the resource
    Then the operation is not allowed
    And the resource keeps its current status
