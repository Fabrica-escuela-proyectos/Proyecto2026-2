@HU08 @business
Feature: Define the minimum advance notice for bookings
  As a provider
  I want to define the minimum advance notice with which my clients can book
  to have enough time to prepare the service and avoid last-minute bookings

  @exception
  Scenario: Default advance notice in a new business
    Given a provider registered a business and has not defined the advance notice
    When a client tries to book a service of that business 30 minutes in advance
    Then the booking is rejected
    And they receive a message saying the business requires at least 1 hour of advance notice

  @happy_path @positive
  Scenario: Successful definition of a new advance notice
    Given a provider is logged in and owns a business
    When they define a minimum advance notice of 2 hours
    Then the new advance notice is saved
    And they receive the confirmation that the change was successful

  @exception
  Scenario: The defined advance notice applies to new bookings
    Given a business has a minimum advance notice of 2 hours
    When a client tries to book 1 hour in advance
    Then the booking is rejected
    And they receive a message saying the business requires at least 2 hours of advance notice

  @positive
  Scenario: Existing bookings are not affected by the change
    Given a business has confirmed bookings
    When the provider changes the minimum advance notice
    Then the confirmed bookings stay the same

  @positive
  Scenario: Each business has its own advance notice
    Given a provider has more than one business
    When they define the minimum advance notice of one of them
    Then the other businesses keep their advance notice

  @exception
  Scenario Outline: Advance notice with an invalid value
    Given a provider is logged in and owns a business
    When they enter the advance notice "<value>"
    Then the change is rejected and the business keeps its previous advance notice
    And they receive a message saying the advance notice is not valid

    Examples:
      | value |
      | 0     |
      | -2    |

  # Límite definido por el equipo (supuesto S-05), no por la HU
  @positive
  Scenario Outline: Advance notice at the allowed limits
    Given a provider is logged in and owns a business
    When they enter the advance notice "<value>"
    Then the new advance notice is saved

    Examples:
      | value |
      | 1     |
      | 720   |

  # Límite definido por el equipo (supuesto S-05), no por la HU
  @exception
  Scenario: Advance notice longer than 30 days
    Given a provider is logged in and owns a business
    When they enter the advance notice "721"
    Then the change is rejected and the business keeps its previous advance notice
    And they receive a message saying the advance notice is not valid

  # Hallazgo: con un texto como "abc" la API rechaza la solicitud (400), pero responde un mensaje general
  # ("El cuerpo de la solicitud es inválido o está vacío") y no dice que la antelación no es válida, como pide la HU.
  @exception @hallazgo
  Scenario: Advance notice written as text
    Given a provider is logged in and owns a business
    When they enter the advance notice "abc"
    Then the change is rejected and the business keeps its previous advance notice
    And they receive a message saying the advance notice is not valid

  @access
  Scenario: A provider tries to define the advance notice of another business
    Given a provider is logged in
    When they try to define the advance notice of a business that belongs to another provider
    Then access is denied
    And the advance notice of that business does not change

  @access
  Scenario: A client tries to define the advance notice of a business
    Given a user with the Client role is logged in
    When they try to define the minimum advance notice of a business
    Then access is denied

  @access
  Scenario: Attempt to change the advance notice without a valid session
    Given a user has not logged in
    When they try to define the minimum advance notice of a business
    Then access is denied
    And they must log in again
