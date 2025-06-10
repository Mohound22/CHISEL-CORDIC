# CORDIC Algorithm Implementation in Chisel and Scala

This repository contains a comprehensive implementation of the CORDIC (Coordinate Rotation Digital Computer) algorithm for trigonometric, hyperbolic, and linear functions. It provides a multi-layered approach, including pure Scala behavioral models, standalone Chisel modules for each geometry, and a final, unified Chisel core that combines all functionalities. This structure allows for clear simulation, isolated hardware implementation, and an efficient, all-in-one hardware solution.

## Why Use the CORDIC Algorithm?

The CORDIC algorithm is a hardware-efficient method for calculating a wide range of elementary functions, including trigonometric, hyperbolic, and linear operations. Its primary advantage is that it avoids the need for explicit multipliers, which are resource-intensive in digital logic. Instead, CORDIC relies on simple iterative shift-and-add operations.

In each iteration, the algorithm rotates a vector by a known angle, using bit-shifts for multiplication by powers of two, and adders/subtractors to accumulate the result. This makes it particularly well-suited for implementation in FPGAs and ASICs, where it can compute complex functions with a minimal hardware footprint.

## Project Structure

The project is organized into three main implementation layers:

1.  **Scala Iterable Models**: These are pure Scala software models that implement the CORDIC algorithm iteratively. They serve as a golden reference for behavior, are easy to debug, and are used to verify the correctness of the Chisel hardware implementations.
2.  **Standalone Chisel Modules**: Each CORDIC geometry (Trigonometric, Hyperbolic, Linear) has its own dedicated Chisel hardware implementation. These modules (`trigCHISEL.scala`, `hyperCHISEL.scala`, `linearCHISEL.scala`) are self-contained and can be used independently.
3.  **Unified `CORDICcore` Module**: The centerpiece of the project, `CORDICcore.scala`, is a highly configurable and synthesizable Chisel module that integrates all three CORDIC geometries into a single core. It uses a decoupled interface and can be parameterized at compile-time to include only the necessary functions, optimizing resource usage.

---

## Development Process and Presentation

For a more detailed walkthrough of the development process, the inner workings of the CORDIC algorithm, and a visual explanation of the project structure, please see the included presentation:

-   [**FinalPresentation.pdf**](./FinalPresentation.pdf)

This presentation covers the journey from theoretical concepts to the final unified hardware core, including the verification strategy and testing results.

---

## The `CORDICcore` Unified Module

`CORDICcore` is the main hardware module, designed for flexibility and efficiency. It acts as a CORDIC co-processor that can be controlled to perform a wide variety of mathematical functions. The core always includes the fundamental circular (trigonometric) functions (Sin/Cos, Arctan/Magnitude), while support for linear and hyperbolic functions can be optionally included at compile-time to optimize resource usage.

### How It Works
The core contains a state machine that manages the CORDIC iterations. When a mode and inputs are provided via its decoupled interface, the core initializes its internal registers (`x`, `y`, `z`) based on the selected function. It then proceeds through a fixed number of `cycleCount` iterations to perform the CORDIC rotation or vectoring operations. Upon completion, the results are presented on the decoupled output ports.

### Configuration Parameters
The `CORDICcore` can be instantiated with the following parameters:

-   `width: Int`: The data width in bits for all inputs, outputs, and internal registers.
-   `cycleCount: Int`: The number of CORDIC iterations to perform. More cycles yield higher precision. A good rule of thumb is that each cycle effectively add one more bit of precision so for 16 bits of precision, a `cycleCount` of 16 is plenty.
-   `integerBits: Int`: The number of bits to reserve for the integer part of the fixed-point representation.
-   `gainCorrection: Boolean`: When `true`, compensates for the inherent CORDIC gain. For functions like Sin/Cos and Sinh/Cosh, this is done efficiently by pre-scaling the initial values. However, for calculating vector magnitudes (both trigonometric and hyperbolic), this requires a multiplication step after the CORDIC iterations, which adds a multiplier to the hardware. The angle results (Arctan, Atanh) and linear operations (multiply/divide) do not require gain correction.
-   `includeLinear: Boolean`: If `true`, the hardware for linear operations (multiply/divide) is included.
-   `includeHyperbolic: Boolean`: If `true`, the hardware for hyperbolic operations is included.

### Operating Modes
The core's function is selected via the `mode` input, using the `CORDICModeAll` enum:

-   `TrigSinCos`: Calculates Sine and Cosine.
-   `TrigArctanMagnitude`: Calculates Arctangent and vector magnitude.
-   `LinearMultiply`: Multiplies two numbers.
-   `LinearDivide`: Divides two numbers.
-   `HyperSinhCosh`: Calculates Hyperbolic Sine and Cosine.
-   `HyperAtanhMagnitude`: Calculates Hyperbolic Arctangent and magnitude.
-   `Exponential`: Computes `exp(x)` and `exp(-x)` (part of the hyperbolic hardware).
-   `NaturalLog`: Computes `ln(x)` (part of the hyperbolic hardware).

---

## File Descriptions

### `src/main/scala/CORDIC/`

-   **`CORDICcore.scala`**: The main, unified Chisel hardware module that combines all CORDIC functionalities. This is the primary hardware artifact of the project.
-   **`trigCHISEL.scala`**: A standalone Chisel implementation for Trigonometric CORDIC functions (Sin, Cos, Atan).
-   **`hyperCHISEL.scala`**: A standalone Chisel implementation for Hyperbolic CORDIC functions (Sinh, Cosh, Atanh, Exp, Ln).
-   **`linearCHISEL.scala`**: A standalone Chisel implementation for Linear CORDIC functions (Multiply, Divide).
-   **`scalaTrigModelIterable.scala`**: A pure Scala behavioral model for the trigonometric CORDIC algorithm. Used as a reference for testing.
-   **`scalaHyperModelIterable.scala`**: A pure Scala behavioral model for the hyperbolic CORDIC algorithm. Used as a reference for testing.
-   **`scalaLinearModelIterable.scala`**: A pure Scala behavioral model for the linear CORDIC algorithm. Used as a reference for testing.

### `src/test/scala/CORDIC/`

-   **`CORDICcoreTester.scala`**: Contains comprehensive unit tests for the unified `CORDICcore` Chisel module, testing all its operational modes.
-   **`ChiselVsScalaModelTests.scala`**: Contains tests that verify the standalone Chisel modules (`trigCHISEL`, `hyperCHISEL`, `linearCHISEL`) against their corresponding Scala behavioral models.
-   **`ScalaModelIterableTest.scala`**: Contains unit tests that verify the correctness of the Scala behavioral models themselves.

---

## Future Work

-   Conduct performance evaluations and analyze how each operation could be optimized.
-   Extend the mathematical domains for all operations, as most are currently defined for small input ranges.
-   Implement parameterizable parallelism to leverage the algorithm's low area and high efficiency.
-   Introduce pipelining to the core to improve throughput and reduce latency penalties.
-   Implement in-depth testing for a wider range of cycle counts and fixed-point configurations.

## Running Tests

To verify the entire implementation, run the test suite with:

```bash
sbt test
```

This will execute:
-   Unit tests for the individual Scala models.
-   Comparison tests between the standalone Scala models and Chisel implementations.
-   Unit tests for the unified `CORDICcore` module.
-   Verification of fixed-point arithmetic and scaling across all CORDIC modes.
-   Edge case handling for inputs like zero and special angles.
