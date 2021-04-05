# ann-benchmarks-lucene

Lucene-based approximate nearest neighbor implementations for use in the [ann-benchmarks project](https://github.com/erikbern/ann-benchmarks).

This is basically just a simple HTTP server that sits in front of Lucene-based approximate nearest neighbor search implementations.
Wrapping the implementations in a server makes it possible to use in the ann-benchmarks project, which expects a Python implementation.
(The Python implementation can just hit the server endpoints).

The server can be downloaded as a Jar from each release.
It's still a work in progress so documentation is basically non-existent.
