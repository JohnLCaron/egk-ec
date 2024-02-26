# ElectionGuard-Kotlin

_last update 02/26/2024_

ElectionGuard-Kotlin (EGK) is an experimental implementation of [ElectionGuard](https://github.com/microsoft/electionguard), [version 2.0.0](https://github.com/microsoft/electionguard/releases/download/v2.0/EG_Spec_2_0.pdf), 
available under an MIT-style open source [License](LICENSE). 

This version adds the option to use [Elliptical Curves](https://en.wikipedia.org/wiki/Elliptic-curve_cryptography) 
for the cryptography. This is a prototype feature and is not part of the ElectionGuard specification.
The implementation for Elliptical Curves (EC) is taken largely from the [Verificatum library](https://www.verificatum.org/,
including the option to use the Verificatum C library. The EC implementation is not stable and will change in the future.

Switching to Elliptical Curves is mostly transparent to most of the ElectionGuard specification, so we are calling this
version EGK 2.1, which uses the ElectionGuard 2.0 specification on top of elliptic curves.

This library also can use the Electionguard Integer Group, and so can also be used for Electionguard 2.0 compliant applications.

See [EGK EC mixnet](https://github.com/JohnLCaron/egk-ec-mixnet) for an implementation of a mixnet using this library with Elliptic Curves.

## Getting Started
* [Getting Started](docs/GettingStarted.md)

## Workflow and Command Line Programs
* [Workflow and Command Line Programs](docs/CommandLineInterface.md)

## Serialization

_We are waiting for the 2.0 JSON serialization specification before finalizing our serialization. For now,
we are still mostly using the 1.9 serialization._

Support for Integer Group [Protocol Buffers](https://en.wikipedia.org/wiki/Protocol_Buffers) has been moved to [this repo](https://github.com/JohnLCaron/egk-protobuf).

### JSON Serialization specification
* [JSON serialization 1.9](docs/JsonSerializationSpec1.9.md)
* [Election Record JSON directory and file layout](docs/ElectionRecordJson.md)

## Validation
* [Input Validation](docs/InputValidation.md)

## Verification
* [Verification](docs/Verification.md)

## Authors
- [John Caron](https://github.com/JohnLCaron) (ElectionGuard Kotlin, Elliptical Curves)
- [Dan S. Wallach](https://www.cs.rice.edu/~dwallach/) (ElectionGuard Kotlin core package)
- [Douglas Wikström](https://www.verificatum.org/) (Verificatum Mixnet)