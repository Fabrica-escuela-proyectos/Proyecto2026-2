@HU03 @identity
Feature: Provider registration

  @positivo
  Scenario: Successful provider registration with valid data
    Given an unregistered visitor wants to create a provider account
    When they complete all personal and business data with valid information and submit the request
    Then the provider account is created successfully
    And the business information is registered and linked to that account

  @positivo
  Scenario: Assignment of the Provider role
    Given an unregistered visitor wants to create a provider account
    When they finish registering with valid data
    Then they have the role "Proveedor" in a controlled way

  @excepcion
  Scenario: Provider registration with an email that already exists
    Given a provider account is already registered
    When a visitor tries to register as a provider with the same email as that account
    Then the request is rejected and no duplicate account is created
    And they receive a message saying the email is already in use

  @excepcion
  Scenario: Provider registration without a business name
    Given an unregistered visitor wants to create a provider account
    When they leave the field "business name" empty and submit the request
    Then the request is rejected and no account is created
    And they receive a specific error message for the field "business name"

  @acceso
  Scenario: The provider cannot assign themselves a privileged role
    Given an unregistered visitor wants to create a provider account
    When they try to register with the role "Administrador"
    Then the requested role is ignored and they are given the role "Proveedor" in a controlled way
    And the account is registered only with the authorized role
