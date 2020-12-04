#!/bin/sh
PID=`jps | grep SocketServer | awk '{print $1}'`
if [ "x$PID" != "x" ]; then
  kill -9 $PID
  echo "kill SimpleHttpServer PID[$PID]"
else
  echo "SimpleHttpServer was already killed"
fi
