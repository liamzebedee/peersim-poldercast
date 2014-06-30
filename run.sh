#!/bin/bash
export CLASSPATH=./bin:./libs/*
java -Xmx2g -javaagent:./libs/SizeOf.jar peersim.Simulator config1.txt