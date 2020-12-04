#!/bin/sh

log_dir="log"
if [ ! -d "$log_dir" ]; then
  mkdir $log_dir
  echo "mkdir $log_dir"
fi

java -cp ./conf/*:./lib/* SocketServer > log/server.log &
tail -f log/server.log
