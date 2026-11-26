@HU15 @resource
Feature: Edit resource

  @positivo
  Scenario: Editing a resource successfully
    Given an authenticated Provider has a resource registered in their business
    When they modify the name or the type with valid data
    And they confirm the changes
    Then the resource is updated
    And the changes are shown in later queries

  @excepcion
  Scenario: Provider tries to edit a resource leaving the name empty
    Given an authenticated Provider is editing a resource of their business
    When they remove the name and confirm the changes
    Then the changes are not saved
    And the Provider is informed that the name is required

  @excepcion
  Scenario: Provider tries to edit a resource with a name that already exists
    Given the business has the resources "Sala 1" and "Sala 2"
    When the Provider changes the name of "Sala 2" to "Sala 1"
    Then the changes are not saved
    And the Provider is informed that a resource with that name already exists

  @excepcion @acceso
  Scenario: Provider tries to edit a resource of another business
    Given a resource belongs to the business of another Provider
    When the Provider tries to edit it
    Then access is denied
    And the resource remains unchanged

  @excepcion
  Scenario: Provider tries to edit a resource that does not exist
    Given an authenticated Provider requests to edit a resource that does not exist
    When they try to save the changes
    Then no modification is made
    And it is reported that the resource does not exist

  @excepcion @acceso
  Scenario: Provider tries to change the business associated with a resource
    Given an authenticated Provider is editing a resource of their business
    When they try to assign it to another business
    Then the change of business is ignored
    And the resource remains associated with its original business
