#!/bin/sh
PID=`jps | grep SocketServer | awk '{print $1}'`
if [ "x$PID" != "x" ]; then
  kill -9 $PID
  echo "kill JavaSocketServer $PID"
else
  echo "JavaSocketServer was already killed"
fi
