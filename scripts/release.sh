#!/bin/bash
set -e
curl -XPOST\
  -H "Accept: application/vnd.github.everest-preview+json" \
  -H "Authorization: token ${GITHUB_TOKEN}" \
  --data '{"event_type": "release", "client_payload": {}}' \
  https://api.github.com/repos/alexklibisz/ann-benchmarks-lucene/dispatches