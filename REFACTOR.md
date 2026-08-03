# Microservices Refactoring & Design Guidelines

## 🎯 Objective
This document outlines the standard operating procedure and architectural guidelines for refactoring the microservices within this project. The primary goal is to ensure stability, enforce a standardized architecture across all services, achieve high test coverage, and maintain a pristine, self-documenting codebase.

---

## 🏗️ 1. Architectural Standardization
Every microservice must strictly adhere to the layered architecture established in the `user-microservice` template. Bypassing layers is strictly prohibited.

* **Controllers (API Layer):** 
  * Responsible *only* for handling HTTP requests, routing to the appropriate service, and returning HTTP responses. 
  * No business logic should reside here.
* **Services (Business Layer):** 
  * The core of the application. Contains all business logic, orchestrates data flow, and applies domain rules.
* **Repositories (Data Access Layer):** 
  * Responsible for interacting with the database or external persistence mechanisms.
* **DTOs (Data Transfer Objects):** 
  * Internal domain entities must **never** be exposed directly to the API clients. 
  * Use DTOs for all incoming requests and outgoing responses to decouple the database schema from the API contract.

---

## 🔄 2. Execution Strategy: Incremental Refactoring
To minimize risk and deployment blast radius, refactoring will follow an incremental, strangler-fig style approach.

1. **One Service at a Time:** Do not attempt sweeping cross-service changes. Isolate refactoring to a single microservice boundary.
2. **Establish the Baseline:** Before altering business logic, ensure the current service builds and has a baseline of tests.
3. **Refactor & Standardize:** Realign the code to the Controller -> Service -> Repository template.
4. **Verify & Validate:** Run the test suite. A service is not considered "done" until its tests pass and edge cases are accounted for.

---

## 🛡️ 3. Defensive Programming Mindset
Assume all inputs are malicious and external systems are unreliable. The codebase must be resilient against unexpected states.

* **Strict Input Validation:** Validate all incoming payloads at the Controller layer (e.g., using `@Valid`, `@NotNull`, etc.). Reject malformed requests immediately with a `400 Bad Request`.
* **Null Safety:** Avoid returning `null`. Utilize `Optional` for return types that might not exist to force the caller to handle the absence of a value.
* **Immutability:** Favor immutable data structures. Use `final` fields, unmodifiable collections, or Records for DTOs where applicable to prevent unintended side effects.
* **Global Error Handling:** Use centralized exception handling to intercept errors and return standardized, sanitized API error responses (do not leak stack traces to the client).
* **Fail Fast:** Validate state and arguments at the beginning of methods. Throw exceptions early rather than allowing a system to enter an invalid state deeper in the execution flow.

---

## 🧼 4. Clean Code & Readability
The code must speak for itself. A new software engineer should be able to trace the flow of data without needing English translations.

* **No Redundant Comments:** Avoid comments that explain *what* the code is doing. If code requires a comment to be understood, it must be extracted into a well-named method or variable. Reserve comments strictly for explaining *why* a highly counter-intuitive technical decision was made (e.g., a specific workaround for an external library bug).
* **Expressive Naming:** Variables, methods, and classes must reveal their intent. (`getUserById` instead of `getData`, `isActive` instead of `flag`).
* **Single Responsibility Principle (SRP):** Classes and methods should do one thing and do it well. If a Service method is spanning 100+ lines, it is likely doing too much and should be broken down into private helper methods.

---

## 🧪 5. Testing Strategy
Automated testing is non-negotiable and must be implemented as part of the refactoring process for each microservice.

* **Unit Testing First:** Focus heavily on unit testing the **Service Layer**, as this is where the business logic lives.
* **Mock Dependencies:** Isolate the unit under test. Use mocking frameworks (like Mockito) to mock out Repositories and external clients. 
* **Clear Test Structure:** Use the `Given-When-Then` (Arrange-Act-Assert) pattern for writing tests to ensure test readability.
* **Test the Edge Cases:** Do not just write "happy path" tests. Explicitly test null inputs, boundary values, and exception scenarios to validate your defensive programming implementations.