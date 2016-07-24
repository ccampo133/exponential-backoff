# exponential-backoff

[![Build Status](https://travis-ci.org/ccampo133/exponential-backoff.svg?branch=master)](https://travis-ci.org/ccampo133/exponential-backoff)

A small Java API for running arbitrary tasks with exponential backoff

## Usage

Sometimes you want to run a task and retry if it fails. For things like
network and external service calls, retrying immediately may not be the most
efficient use of your time. It is unlikely your service is going to
be able to respond just a few milliseconds later (it might be down, for 
example). With exponential backoff, successive attempts are retried after
waiting an amount of time, exponentially greater than the previous wait time.

While this is great for increasing client reliability, in the case where you
may have multiple concurrent clients, using plain exponential backoff may
still result in poor performance (see: 
https://www.awsarchitectureblog.com/2015/03/backoff.html)

This is where the concept of jitter (randomness) comes in. Plainly, using
using jitter with exponential backoff means choosing a random wait versus
a plain exponential wait time. The algorithm actually chooses between the
random time or the exponential time, with equal probability given to both.
While this may seem counter-intuitive, using jitter can greatly increase
performance for competing clients (again, see the link above).

This library allows you to run arbitrary tasks which will retry with
exponential backoff (and potentially jitter). If an exception is thrown
anywhere in your task (and not caught), the task will be retried, waiting
some amount of time between the next execution (determined by the backoff
algorithm). There are a few examples in the unit tests
`ExponentialBackOffTest.java`, however the general usage is as follows:

```java
final BackOffResult<String> result = ExponentialBackOff.<String>builder()
        .withBase(100)
        .withCap(5000)
        .withMaxAttempts(5)
        .withJitter()
        .withTask(() -> "Do something")
        .retryIf(result -> result.equals("retry"))
        .withExceptionHandler(e -> System.out.println("Do something with " + e))
        .execute();
        
if (result.status == BackOffResult.SUCCESSFUL) {
    System.out.println("All done! Returned " + result.data.orElse("empty"));
} else {
    System.out.println("Failed to complete task within 5 attempts");
}
```

See the JavaDocs or source code for more specific information on usage. The
preferred approach is to use the builder to generate and execute the tasks.

## Dependency Information

Found on Maven Central (http://search.maven.org)

Gradle:

    compile 'me.ccampo:exponential-backoff:1.0.0'
    
Maven:

    <dependency>
      <groupId>me.ccampo</groupId>
      <artifactId>exponential-backoff</artifactId>
      <version>1.0.0</version>
    </dependency>

## Development

To build (OS X or -nix)

    ./gradlew build
    
or on Windows

    gradlew.bat build
