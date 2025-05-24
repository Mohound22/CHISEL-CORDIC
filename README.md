# CORDIC Trigonometric Algorithm Implementation

This repository contains implementations of the CORDIC (Coordinate Rotation Digital Computer) algorithm for trigonometric functions in both Scala (iterable model) and Chisel (hardware description). The implementation supports sine, cosine, and arctangent calculations with configurable fixed-point precision and iteration counts.

## Features

- **Two Implementations**:
  - `scalaModelIterable.scala`: Pure Scala iterable model of the CORDIC algorithm
  - `trigCHISEL.scala`: Chisel hardware implementation of the same algorithm

- **Configurable Parameters**:
  - Variable data width
  - Adjustable number of iterations (cycle count)
  - Configurable fixed-point representation (number of integer bits)

- **Comprehensive Test Coverage**:
  - Unit tests for the Scala model (`scalaTestIterable.scala`)
  - Verification against Chisel implementation (`scalaCHISELtest.scala`)
  - Edge case testing
  - Fixed-point scaling verification
  - Sequential operation testing

## Implemented Functionality

- Basic trigonometric CORDIC operations:
  - Sine calculation
  - Cosine calculation
  - Arctangent calculation
- Fixed-point arithmetic with configurable scaling
- Parameterized cycle count (iteration control)
- Extensive test coverage including:
  - Basic functionality verification
  - Edge case handling
  - Fixed-point scaling validation
  - Sequential operation testing

## Future Work

- Extend angle coverage to second and third quadrants
- Implement in-depth testing for different cycle counts and fixed-point configurations
- Add hyperbolic CORDIC functionality
- Implement linear CORDIC operations
- Add parameterized gain correction at initialization

## Running Tests

To verify the implementation, run the test suite with:

```bash
sbt test
```


This will execute:
- Unit tests for the Scala model
- Comparison tests between Scala model and Chisel implementation
- Fixed-point scaling verification
- Edge case testing

Test Coverage
The test suite verifies:
- Correct calculation of arctangent for various coordinates
- Proper sine/cosine scaling
- Handling of zero inputs in vectoring mode
- Negative coordinate handling
- Special angle cases (0, π/2, etc.)
- Minimum/maximum input values
- Consecutive operations
- Idle state behavior
- Fixed-point scaling accuracy
