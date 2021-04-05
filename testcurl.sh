#!/bin/bash
set -e

curl -f -X PUT -H "Content-Type: application/json" http://localhost:8080/foo -d \
  '{"dims": 2, "searchStrategy": "euclideanhnsw", "maxConnections": 32, "beamWidth": 100}'; echo

curl -f -X POST -H "Content-Type: application/json" http://localhost:8080/foo -d \
  '[[0.2, 0.3],[0.1, 0.2],[0.3,0.4]]'; echo

curl -f -X POST http://localhost:8080/foo/close; echo

curl -f -X POST -H "Content-Type: application/json" http://localhost:8080/foo/search -d \
  '{"k": 3, "params": {"numSeed": 10}, "vector": [0.09, 0.19]}'; echo

curl -f -X DELETE http://localhost:8080/foo; echo