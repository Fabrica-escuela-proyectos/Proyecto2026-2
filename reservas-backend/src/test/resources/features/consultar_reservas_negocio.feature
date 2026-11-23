@HU24 @reservation
Feature: View the bookings of the business

  @positivo
  Scenario: Successfully viewing the bookings of the business
    Given an authenticated Provider has registered services with active bookings
    When they request the list of bookings of their business
    Then they see all the bookings associated with their services
    And each booking shows the date, start time, end time, service and Client name

  @positivo
  Scenario: Viewing bookings when the business has none
    Given an authenticated Provider has no bookings in their services
    When they request the list of bookings of their business
    Then they see an empty list
    And the Provider is informed that there are no registered bookings

  @excepcion @acceso
  Scenario: Provider tries to view the bookings of another business
    Given an authenticated Provider wants to view bookings
    When they try to access the bookings of another Provider's services
    Then access is denied
    And they receive a message saying they do not have permission to view them

  # The platform is an API: "redirected to the login" means it answers 401 (authenticate again).
  @excepcion @sesion
  Scenario: Unauthenticated user tries to view the bookings of the business
    Given a user has not logged in
    When they try to access the list of bookings of the business
    Then they are not allowed to access the feature
    And they are redirected to the login

  @excepcion @acceso
  Scenario: Client tries to access the bookings of the business
    Given a user with the Client profile is authenticated
    When they try to access the list of bookings of the business
    Then access is denied because their profile does not have permission
    And they receive a message saying they do not have permission to view them
