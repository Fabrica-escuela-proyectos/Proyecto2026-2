@HU14 @resource
Feature: Register resource

  @positivo
  Scenario: Registering a resource successfully
    Given an authenticated Provider wants to register a resource
    When they provide the name and the type of the resource
    And they confirm the registration
    Then the resource is registered
    And it is associated only with the business of the Provider who created it

  @excepcion
  Scenario: Provider tries to register a resource without a name
    Given an authenticated Provider wants to register a resource
    When they try to register it without providing a name
    Then the resource is not registered
    And the Provider is informed that the name is required

  @excepcion
  Scenario: Provider tries to register a resource without a type
    Given an authenticated Provider wants to register a resource
    When they try to register it without indicating the type
    Then the resource is not registered
    And the Provider is informed that the type is required

  @excepcion
  Scenario: Provider tries to register a resource with a duplicate name
    Given the business of the Provider already has a resource named "Sala 1"
    When they try to register another resource with the name "Sala 1"
    Then the resource is not registered
    And the Provider is informed that a resource with that name already exists

  @excepcion @acceso
  Scenario: Provider tries to associate the resource with another business
    Given an authenticated Provider wants to register a resource
    When they try to associate it with a business that is not theirs
    Then the indicated business is ignored
    And the resource is associated only with their own business

  @excepcion @acceso
  Scenario: Client tries to register a resource
    Given a user with the Client role is authenticated
    When they try to register a resource
    Then access is denied
    And no resource is created
