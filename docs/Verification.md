# Verification

_last update 02.25.2024_

ElectionGuard-Kotlin fully implements the **Verifier** section 6 of the ElectionGuard specification.
Use the classes in the _src/main/kotlin/electionguard/verifier_ package in your own program, 
or the existing CLI program.

## Run Verifier Command Line Interface

See _Building a library uber jar_ in [GettingStarted](GettingStarted.md), then run the verifier like:

```
/usr/bin/java \
  -classpath build/libs/egk-ec-2.1-SNAPSHOT-uber.jar \
  org.cryptobiotic.eg.cli.RunVerifier \
  -in /path/to/election_record
```

The help output of that program is:

```` 
Usage: RunVerifier options_list
Options: 
    --inputDir, -in -> Directory containing input election record (always required) { String }
    --nthreads, -nthreads [11] -> Number of parallel threads to use { Int }
    --showTime, -time [false] -> Show timing 
    --help, -h -> Usage info
````

Since the main class of the uberJar is _org.cryptobiotic.eg.cli.RunVerifier_ you can also run the verifier as:

```
/usr/bin/java \
  -jar build/libs/egk-ec-2.1-SNAPSHOT-uber.jar \
  -in /path/to/election_record
```