@HU09 @services
Feature: Create a service
  As a provider
  I want to register a service with its name, duration and price in pesos
  so that my clients know what I offer and can book it

  @happy_path @positive
  Scenario: Successful creation of a service with valid data
    Given a provider is logged in and owns a business
    When they register a service with a valid name, duration and price
    Then the service is created in their business
    And the service is active

  @positive
  Scenario: Creation of a free service
    Given a provider is logged in and owns a business
    When they register a service with a price of 0 pesos
    Then the service is created as free

  @positive
  Scenario: Creation of a service with a description
    Given a provider is logged in and owns a business
    When they register a valid service with the description "Includes washing and styling"
    Then the service is created in their business
    And the service keeps the description "Includes washing and styling"

  @positive
  Scenario: Creation of a service without a description
    Given a provider is logged in and owns a business
    When they register a valid service without a description
    Then the service is created in their business
    And the service has no description

  @positive
  Scenario: Clients can see the created service
    Given a provider created an active service
    When a client checks the services of that business
    Then they see the service with its duration and its price in pesos

  @exception
  Scenario: A new service without resources cannot be booked yet
    Given a provider created a service that has no resources assigned
    When a client tries to book that service
    Then the booking is rejected
    And they receive a message saying the service has no availability yet

  @exception
  Scenario Outline: Required field missing when creating a service
    Given a provider is logged in and owns a business
    When they register a service leaving the field "<field>" empty
    Then the request is rejected and the service is not created
    And they receive an error message about the field "<field>"

    Examples:
      | field    |
      | name     |
      | duration |
      | price    |

  @exception
  Scenario Outline: Duration with an invalid value
    Given a provider is logged in and owns a business
    When they register a service with the duration "<duration>"
    Then the request is rejected and the service is not created
    And they receive a message saying the duration is not valid

    Examples:
      | duration |
      | 0        |
      | -30      |

  @exception
  Scenario Outline: Price with an invalid value
    Given a provider is logged in and owns a business
    When they register a service with the price "<price>"
    Then the request is rejected and the service is not created
    And they receive a message saying the price is not valid

    Examples:
      | price |
      | -5000 |

  # Hallazgo: con un texto como "abc" la API rechaza la solicitud (400), pero responde un mensaje general
  # ("El cuerpo de la solicitud es inválido o está vacío") sin decir cuál campo falló, como pide la HU.
  @exception @hallazgo
  Scenario Outline: Duration or price written as text
    Given a provider is logged in and owns a business
    When they register a service with the "<field>" "abc"
    Then the request is rejected and the service is not created
    And they receive an error message about the field "<field>"

    Examples:
      | field    |
      | duration |
      | price    |

  # Límite definido por el equipo (supuesto S-06), no por la HU
  @positive
  Scenario Outline: Duration at the allowed limits
    Given a provider is logged in and owns a business
    When they register a service with the duration "<duration>"
    Then the service is created in their business

    Examples:
      | duration |
      | 1        |
      | 1440     |

  # Límite definido por el equipo (supuesto S-06), no por la HU
  @exception
  Scenario: Duration longer than one day
    Given a provider is logged in and owns a business
    When they register a service with the duration "1441"
    Then the request is rejected and the service is not created
    And they receive a message saying the duration is not valid

  # Límite definido por el equipo (supuesto S-06), no por la HU
  @positive
  Scenario: Price at the highest allowed value
    Given a provider is logged in and owns a business
    When they register a service with the price "1000000000"
    Then the service is created in their business

  # Límite definido por el equipo (supuesto S-06), no por la HU
  @exception
  Scenario: Price above the highest allowed value
    Given a provider is logged in and owns a business
    When they register a service with the price "1000000001"
    Then the request is rejected and the service is not created
    And they receive a message saying the price is not valid

  # Límite definido por el equipo (supuesto S-06), no por la HU
  @positive
  Scenario Outline: Name and description at their maximum length
    Given a provider is logged in and owns a business
    When they register a service whose "<field>" has <length> characters
    Then the service is created in their business

    Examples:
      | field       | length |
      | name        | 150    |
      | description | 500    |

  # Límite definido por el equipo (supuesto S-06), no por la HU
  @exception
  Scenario Outline: Name and description longer than allowed
    Given a provider is logged in and owns a business
    When they register a service whose "<field>" has <length> characters
    Then the request is rejected and the service is not created
    And they receive an error message about the field "<field>"

    Examples:
      | field       | length |
      | name        | 151    |
      | description | 501    |

  @exception
  Scenario Outline: Service with a repeated name in the same business
    Given a business already has a service called "Consulta general"
    When its provider tries to create another service called "<name>"
    Then the request is rejected and no duplicate service is created
    And they receive a message saying a service with that name already exists

    Examples:
      | name             |
      | Consulta general |
      | CONSULTA GENERAL |

  @access
  Scenario: A provider tries to create a service in another provider's business
    Given a provider is logged in
    When they try to create a service in a business that belongs to another provider
    Then access is denied and the service is not created

  @access
  Scenario: A client tries to create a service
    Given a user with the Client role is logged in
    When they try to create a service in a business
    Then access is denied

  @access
  Scenario: Attempt to create a service without a valid session
    Given a user has not logged in
    When they try to create a service in a business
    Then access is denied
    And they must log in again

  @positive
  Scenario: The creation of the service is recorded
    Given a provider created a service
    When the service is saved
    Then it is recorded who created it and when
