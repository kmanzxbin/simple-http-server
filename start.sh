#!/bin/sh
java -cp ./conf/*:./lib/* SocketServer > log/server.log &
tail -f log/server.log