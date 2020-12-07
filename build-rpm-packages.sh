#!/usr/bin/env bash

set -xe

spectool -g -C ~/rpmbuild/SOURCES supercollider.spec
rpmbuild -ba supercollider.spec
