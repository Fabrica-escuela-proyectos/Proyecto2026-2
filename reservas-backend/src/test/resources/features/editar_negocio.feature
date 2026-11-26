@HU07 @business
Feature: Edit the information of the business
  As a provider
  I want to update the data of my business
  so that clients see correct information

  @happy_path @positive
  Scenario: Successful edition of all the data of the business
    Given a provider is logged in and owns a business
    When they change the name, description, address, business type and phone with valid data
    Then the changes are saved
    And they receive the confirmation that the information was updated

  @positive
  Scenario: Edition of only one piece of data of the business
    Given a provider is logged in and owns a business
    When they change only the address with a valid value
    Then the change is saved and the other data of the business stay the same

  @positive
  Scenario: Clients see the updated information
    Given a provider updated the information of their business
    When a client checks that business
    Then they see the updated name, description, address, business type and phone

  @positive
  Scenario: Optional data of the business left empty
    Given a provider is logged in and owns a business with all its data filled in
    When they leave the address, the business type and the phone empty
    Then the changes are saved
    And the business no longer shows an address, a business type or a phone

  @positive
  Scenario: Editing a business does not affect the other businesses of the provider
    Given a provider has more than one business
    When they change the information of one of them
    Then the other businesses keep their information

  @positive
  Scenario Outline: Business phone with a valid format
    Given a provider is logged in and owns a business
    When they enter the phone "<phone>"
    Then the changes are saved

    Examples:
      | phone      |
      | 3001234567 |
      | 6045551234 |

  @positive
  Scenario: The business type does not depend on uppercase or lowercase letters
    Given a provider is logged in and owns a business
    When they choose the business type "belleza"
    Then the changes are saved
    And the business type of the business is "BELLEZA"

  @exception
  Scenario: Attempt to leave the name of the business empty
    Given a provider is editing their business
    When they leave the name of the business empty
    Then the change is rejected and the business keeps its previous information
    And they receive a message saying the name of the business is required

  @exception
  Scenario Outline: Business phone with an invalid format
    Given a provider is editing their business with the other data valid
    When they enter the phone "<phone>"
    Then the change is rejected and the business keeps its previous information
    And they receive a message saying the phone is not valid

    Examples:
      | phone      |
      | abcde12345 |
      | 12345      |
      | 123        |

  @exception
  Scenario Outline: Business type that does not exist
    Given a provider is editing their business with the other data valid
    When they choose the business type "<type>"
    Then the change is rejected and the business keeps its previous information
    And they receive a message saying the business type is not valid

    Examples:
      | type      |
      | Panadería |
      | 123       |

  # Límite definido por el equipo, no por la HU: el nombre admite máximo 150 caracteres
  @positive
  Scenario: Name of the business at its maximum length
    Given a provider is logged in and owns a business
    When they change the name of the business to one of 150 characters
    Then the changes are saved

  # Límite definido por el equipo, no por la HU: el nombre admite máximo 150 caracteres
  @exception
  Scenario: Name of the business longer than allowed
    Given a provider is editing their business
    When they change the name of the business to one of 151 characters
    Then the change is rejected and the business keeps its previous information

  @access
  Scenario: A provider tries to edit the business of another provider
    Given a provider is logged in
    When they try to modify a business that belongs to another provider
    Then access is denied
    And that business does not change

  @access
  Scenario: A client tries to edit a business
    Given a user with the Client role is logged in
    When they try to modify the information of a business
    Then access is denied

  @access
  Scenario: Attempt to edit a business without a valid session
    Given a user has not logged in
    When they try to modify the information of a business
    Then access is denied
    And they must log in again

  @positive
  Scenario: The edition of the business is recorded
    Given a provider modified the information of their business
    When the change is saved
    Then it is recorded who made the change and when
