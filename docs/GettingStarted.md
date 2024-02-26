# ElectionGuard-Kotlin Getting Started

_last update 02/25/2024_

<!-- TOC -->
* [ElectionGuard-Kotlin Getting Started](#electionguard-kotlin-getting-started)
  * [Requirements](#requirements)
  * [Building the egk-ec library](#building-the-egk-ec-library)
  * [Using the Verificatum library (optional)](#using-the-verificatum-library-optional)
    * [Installing or Building the GMP library](#installing-or-building-the-gmp-library)
    * [Building the Verificatum Elliptic Curve library](#building-the-verificatum-elliptic-curve-library)
  * [Using the egk-ec library in your own project](#using-the-egk-ec-library-in-your-own-project)
  * [Building a library with all dependencies ("fat jar")](#building-a-library-with-all-dependencies-fat-jar)
<!-- TOC -->

## Requirements

1. Clone the egk Elliptic Curve repo

```
  cd devhome
  git clone https://github.com/JohnLCaron/egk-ec.git
```

2. **Java 21**. Install as needed, and make it your default Java when working with egk.

    _In general, we will use the latest version of the JVM with long-term-support (aka LTS). 
    This is the "language level" or bytecode version, along with the library API's, that our code assumes. 
    Since new Java version are backwards compatible, you can use any version of the JVM greater than or equal to 17._

3. **Gradle 8.6**. The correct version of gradle will be installed when you invoke a gradle command. 
   To do so explicitly, you can do:

```
  cd devhome/egk-ec
  ./gradlew wrapper --gradle-version=8.6 --distribution-type=bin
```

4. **Kotlin 1.9.22**. The correct version of kotlin will be installed when you invoke a gradle command.
   Similarly, all needed library dependencies are downloaded when you invoke a gradle command.

Alternatively, you can use the IntelliJ IDE (make sure you update to the latest version). 
Do steps 1 and 2 above. Then, in the IDE top menu: 
   1. use File / New / "Project from existing sources"
   2. in popup window, navigate to _devhome/egk-ec_ and select that directory
   3. "Import project from existing model" / Gradle

IntelliJ will create and populate an IntelliJ project with the egk-ec sources. There's
lots of online help for using IntelliJ. Recommended if you plan on doing a good amount of Java/Kotlin coding.

## Building the egk-ec library

To build the complete library and run the standard tests:

```
  cd devhome/egk-ec
  ./gradlew build
```

To just do a clean build (no tests):

```
  cd devhome/egk-ec
  ./gradlew clean assemble
```

You should find that the library jar file is placed into:

`build/libs/egk-ec-2.1-SNAPSHOT.jar
`

## Using the Verificatum library (optional)

### Installing or Building the GMP library

1. You can check to see if there is a pre-built gmp library available for your machine.

2. Otherwise download and build the latest GMP release from https://gmplib.org/. We are using GMP 6.3.0, but an older 
version is probably fine.

3. Install into one of the library paths, usually /usr/lib.


### Building the Verificatum Elliptic Curve library

```
  cd devhome
  git clone https://github.com/verificatum/verificatum-vec.git
  cd verificatum-vec
  make -f Makefile.build
  ./configure
  make
```

Install into one of the library paths, usually _/usr/lib_. You can also use

```
sudo make install
```

which will install into _/usr/local/lib_. Make sure that directory is in your library paths, for example add this to 
~/.bashrc :

```
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/usr/local/lib
```

While VEC and GMP are optional, they are needed for good performance. If the libraries are available on the load path, 
they will automatically be used. To check, build the fat jar (below) then run:

```
/usr/bin/java \
  -classpath build/libs/egkec-2.1-SNAPSHOT-all.jar \
  org.cryptobiotic.eg.cli.RunShowSystem \
  -show hasVEC
```
If successful, you will see the message "VEC and GMP are installed".


## Using the egk-ec library in your own project

We do not yet have egk uploaded to Maven Central, which is the standard way to distribute JVM libraries.

However, you can add the library to your gradle application by pointing directly to the jar, along with all
of its dependencies, for example:

```
  dependencies {
    implementation(files("devhome/egk-ec/build/libs/egk-ec-2.1-SNAPSHOT.jarr"))
    implementation("io.github.microutils:kotlin-logging:3.0.5")
    implementation("com.michael-bull.kotlin-result:kotlin-result:1.1.18")
    
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.0")
    ...
  }
```

## Building a library with all dependencies ("fat jar")

If you are using the electionguard library as standalone (eg for the command line tools), its easier to build a 
"fat jar" that includes all of its dependencies: 

```
  cd devhome/egk-ec
  ./gradlew fatJar
```

You should find that the fat jar file is placed into:

`build/libs/egkec-2.1-SNAPSHOT-all.jar
`

You can put this jar into your own gradle build like

```
  dependencies {
    implementation(files("/egkhome/build/libs/egkec-2.1-SNAPSHOT-all.jar"))
     ...
  }
```

And you can add it to your classpath to execute [command line programs](CommandLineInterface.md):

```
/usr/bin/java \
    -classpath /egkhome/build/libs/egkec-2.1-SNAPSHOT-all.jar \
    org.cryptobiotic.eg.cli.RunVerifier \
    -in /path/to/election_record
```

The fat jar includes logback as a logging implementation. The full set of included libraries are described
[here](../dependencies.txt).
