@e2e @US19 @resource
Feature: Define the opening hours of a resource
  As a Provider
  I want to define the days and hours each resource is available
  So that it can only be booked at those times

  @positive
  Scenario: Defining the weekly opening hours of a resource
    Given a Provider owns a resource without opening hours
    When they define opening hours from Monday to Saturday and leave Sunday empty
    Then the opening hours are saved
    And Sunday is marked as not available

  @positive
  Scenario: Editing the opening hours of a single day
    Given a Provider owns a resource with weekly opening hours
    When they change only the Monday hours
    Then the Monday hours are updated
    And the other days keep their previous hours

  @negative
  Scenario Outline: Defining an invalid time range
    Given a Provider owns a resource with weekly opening hours
    When they set a range from "<start>" to "<end>" on Monday
    Then the change is rejected
    And the resource keeps its previous opening hours

    Examples:
      | start | end   |
      | 14:00 | 10:00 |
      | 09:00 | 09:00 |
      | 25:00 | 26:00 |
