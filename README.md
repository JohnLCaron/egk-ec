[![License](https://img.shields.io/github/license/JohnLCaron/egk-ec)](https://github.com/JohnLCaron/egk-ec/blob/main/LICENSE.txt)
![GitHub branch checks state](https://img.shields.io/github/actions/workflow/status/JohnLCaron/egk-ec/unit-tests.yml)
![Coverage](https://img.shields.io/badge/coverage-90.8%25%20LOC%20(6930/7630)-blue)

# ElectionGuard-Kotlin Elliptic Curve

_last update 04/24/2024_

EGK Elliptic Curve (egk-ec) is an experimental implementation of [ElectionGuard](https://github.com/microsoft/electionguard), 
[version 2.0](https://github.com/microsoft/electionguard/releases/download/v2.0/EG_Spec_2_0.pdf), 
available under an MIT-style open source [License](LICENSE.txt). 

This version adds the option to use [Elliptic Curves](https://en.wikipedia.org/wiki/Elliptic-curve_cryptography) 
for the cryptography. This is an experimental feature and is not part of the ElectionGuard specification.
The implementation for Elliptic Curves (EC) is taken largely from the [Verificatum library](https://www.verificatum.org/),
including the option to use the Verificatum C library. See [VCR License](LICENSE_VCR.txt) for the license for this part of
the library.

Switching to Elliptic Curves is mostly transparent to the ElectionGuard specification, so we are calling this
version EGK 2.1, which uses the ElectionGuard 2.0 specification on top of elliptic curves.

This library also can use the Electionguard Integer Group, and so can also be used for Electionguard 2.0 compliant applications.

See [EGK EC mixnet](https://github.com/JohnLCaron/egk-ec-mixnet) for an implementation of a mixnet using this library with Elliptic Curves.

See [EGK webapps](https://github.com/JohnLCaron/egk-webapps) for HTTP client/server applications that use this library to allow remote workflows.

## Documentation
* [Getting Started](docs/GettingStarted.md)
* [Workflow and Command Line Programs](docs/CommandLineInterface.md)
* [Input Validation](docs/InputValidation.md)
* [Verification](docs/Verification.md)

## Serialization

The elliptic curve group uses base64 encoding in the JSON, with point compression that reduces the byte count per ElementModP to 33 bytes.
Json serialization of 1000 encrypted ballots with 34 ciphertexts each (including all the proofs) is 44.5 Mb. 
Zipping all into a zip archive is 12.3 Mb.

We are using the following provisional JSON serialization specification:

* [JSON serialization 2.1](docs/JsonSerializationSpec2.1.md)
* [Election Record JSON directory and file layout](docs/ElectionRecordJson.md)

## Authors
- [John Caron](https://github.com/JohnLCaron) (ElectionGuard Kotlin, Elliptic Curves)
- [Dan S. Wallach](https://www.cs.rice.edu/~dwallach/) (ElectionGuard Kotlin core package)
- [Douglas Wikström](https://www.verificatum.org/) (Verificatum Elliptic Curves)