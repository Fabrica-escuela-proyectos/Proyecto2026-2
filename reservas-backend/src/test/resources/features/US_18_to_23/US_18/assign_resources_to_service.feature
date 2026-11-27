@e2e @US18 @service
Feature: Assign resources to a service
  As a Provider
  I want to indicate which resources each service is offered on
  So that I know where and with what the client will be served

  @positive
  Scenario: Assigning several resources to a service
    Given a Provider has a service and two resources in their business
    When they assign both resources to the service
    Then the service is offered on both resources

  @negative
  Scenario Outline: Assigning a resource that is not valid for the business
    Given a Provider has a service with one resource assigned
    When they try to assign a resource that <condition>
    Then the assignment is rejected
    And the service keeps only its previous resource

    Examples:
      | condition                   |
      | belongs to another business |
      | does not exist              |
