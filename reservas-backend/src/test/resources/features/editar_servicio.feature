@HU10 @services
Feature: Edit a service
  As a provider
  I want to modify the data of a service
  to keep its information up to date

  @happy_path @positive
  Scenario: Successful edition of a service
    Given a provider has a service in their business
    When they change the name, description, duration and price of the service with valid data
    Then the service information is updated
    And the provider sees the new data saved

  @positive
  Scenario: Editing only one piece of data keeps the others
    Given a provider has a service in their business
    When they change only the description of the service
    Then the service information is updated
    And the name, duration and price of the service stay the same

  @positive
  Scenario: Changing the price does not change bookings already made
    Given a provider has a service with a price of 50000 COP
    And there is a booking for that service with a price of 50000 COP
    When the provider changes the price of the service to 60000 COP
    Then the service has a price of 60000 COP
    And the existing booking keeps the price of 50000 COP

  @positive
  Scenario: Editing a service to make it free
    Given a provider has a service in their business
    When they change the price of the service to 0 COP
    Then the change is accepted
    And the service has a price of 0 COP

  @exception
  Scenario Outline: Edition with invalid data
    Given a provider has a service in their business
    When they change the "<field>" of the service to "<value>"
    Then the change is rejected and the service keeps its previous data
    And they receive an error message about the field "<field>"

    Examples:
      | field    | value |
      | name     |       |
      | duration | 0     |
      | duration | -15   |
      | price    | -1000 |

  # Límite definido por el equipo (supuesto S-06), no por la HU
  @positive
  Scenario Outline: Edition with values at the allowed limits
    Given a provider has a service in their business
    When they change the "<field>" of the service to "<value>"
    Then the change is accepted

    Examples:
      | field    | value      |
      | duration | 1          |
      | duration | 1440       |
      | price    | 1000000000 |

  # Límite definido por el equipo (supuesto S-06), no por la HU
  @exception
  Scenario Outline: Edition with values above the allowed limits
    Given a provider has a service in their business
    When they change the "<field>" of the service to "<value>"
    Then the change is rejected and the service keeps its previous data
    And they receive an error message about the field "<field>"

    Examples:
      | field    | value      |
      | duration | 1441       |
      | price    | 1000000001 |

  @exception
  Scenario: Edition with a name that another service of the business already uses
    Given a provider has two services called "Corte clasico" and "Barba"
    When they change the name of "Barba" to "corte clasico"
    Then the change is rejected and the service keeps its previous data
    And they receive a message saying a service with that name already exists

  @access
  Scenario: A provider tries to edit a service of another business
    Given a provider is logged in
    And there is a service that belongs to another provider
    When they try to modify that service
    Then access is denied
    And the information of the service is not modified

  @access
  Scenario: A client tries to edit a service
    Given a user with the Client role is logged in
    When they try to modify a service
    Then access is denied

  @access
  Scenario: Attempt to edit a service without a valid session
    Given a user has not logged in
    When they try to modify a service
    Then access is denied
    And they must log in again
