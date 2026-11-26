@HU16 @resource
Feature: Deactivate resource

  @positivo
  Scenario: Successfully deactivating a resource without future bookings
    Given an authenticated Provider manages a resource of their own business
    And the resource has no future bookings
    When they request to deactivate the resource
    Then the resource becomes deactivated
    And the resource is no longer available for new bookings

  @positivo
  Scenario: Deactivating a resource with future bookings
    Given an authenticated Provider manages a resource of their own business
    And the resource has future bookings
    When they request to deactivate the resource
    Then the Provider is informed of the number of future bookings that will be affected
    And they must confirm before continuing

  @positivo
  Scenario: Confirming the deactivation of a resource with future bookings
    Given a Provider has requested to deactivate a resource with future bookings
    And the Provider was informed of the number of affected bookings
    When the Provider confirms the deactivation
    Then the resource becomes deactivated
    And the affected future bookings are cancelled

  @positivo
  Scenario: Cancelling the deactivation of a resource
    Given a Provider has requested to deactivate a resource with future bookings
    And the Provider was informed of the number of affected bookings
    When the Provider does not confirm the deactivation
    Then the resource remains active
    And the future bookings of the resource are not cancelled

  @excepcion @acceso
  Scenario: Provider tries to manage a resource that is not theirs
    Given an authenticated Provider
    And a resource that belongs to another business
    When they request to deactivate the resource
    Then the operation is not allowed
    And the resource keeps its current status
