@HU01 @identity
Feature: Client registration


  @positivo
  Scenario: Successful registration with valid data
    Given an unregistered visitor wants to create a client account
    When they complete all the data with valid information and submit the request
    Then the account is created successfully
    And it has the role "Cliente" by default

  @excepcion
  Scenario: Registration with an email that already exists
    Given a client account is already registered
    When a visitor tries to register with the same email as that account
    Then the request is rejected and no duplicate account is created
    And they receive a message saying the email is already in use

  @excepcion
  Scenario: Registration with an invalid email format
    Given an unregistered visitor wants to create a client account
    When they enter the email "usuario@@dominio" and submit the request
    Then the request is rejected and no account is created
    And they receive an error message saying the email format is not valid

  @excepcion
  Scenario Outline: Cell phone number with an invalid format
    Given a visitor has a valid name, email and password
    When they submit the request with the cell phone number "<cell phone>"
    Then the request is rejected and no account is created
    And they receive an error message saying the cell phone number is not valid

    Examples:
      | cell phone |
      | abcde12345 |
      | 12345      |
      | 123        |
      | 1234567890 |

  @excepcion
  Scenario: Registration with a cell phone number already registered to another account
    Given a client account is already registered
    When a visitor tries to register with the same cell phone number as that account
    Then the request is rejected and no duplicate account is created
    And they receive a message saying the cell phone number is already in use

  @positivo
  Scenario Outline: Valid password with an allowed special character
    Given a visitor has a valid name, email and cell phone number
    When they submit the password "<password>" that includes the special character "<special character>"
    Then the account is created successfully

    Examples:
      | special character | password    |
      | ¡                 | Segura¡2026 |
      | *                 | Segura*2026 |
      | +                 | Segura+2026 |
      | °                 | Segura°2026 |

  @excepcion
  Scenario Outline: Password that does not meet the security policy
    Given a visitor has a valid name, email and cell phone number
    When they submit the password "<password>" that fails the requirement "<requirement>"
    Then the request is rejected and no account is created
    And they receive a message stating the security requirement that was not met

    Examples:
      | requirement                              | password      |
      | contain at least one uppercase letter    | clave#segura1 |
      | contain at least one lowercase letter    | CLAVE#SEGURA1 |
      | have at least 8 characters               | Ab#xyz1       |
      | contain at least one special character   | ClaveSegura1  |

  @excepcion
  Scenario Outline: Missing required field
    Given an unregistered visitor wants to create a client account
    When they leave the field "<field>" empty and submit the request
    Then the request is rejected and no account is created
    And they receive a specific error message for the field "<field>"

    Examples:
      | field             |
      | full name         |
      | email             |
      | cell phone number |
      | password          |

