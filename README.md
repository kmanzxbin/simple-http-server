# Simple-Http-Server

## Change log

### v2021-2-21  
1. supported send diff response by diff method, file name will be like this response_{url}[{METHOD}].txt  
2. support ${UUID} variable for random String in response content  

### v2020-11-23  
1. switch implementation to NIO  
2. Support {} as a wildcard to match path a~b~c~{} can match a/b/c/foo path, internally it will be automatically converted to a/b/c\w+ as the key for regular matching  
  
## About Simple-Http-Server
1. Lightweight HTTP server, which can return the corresponding response message according to the path/method and then return it, mainly used as an interface testing tool  
2. No external dependence, based on the principle of simplicity and practicality

## How to use
chmod u+x *.sh  
./start.sh  
./stop.sh  

## How to config
server's configuration stored in file conf/conf.properties  
U can config port, response delay time and so on, U can find more explanation in conf.properties  
U can also define response delay time for special URL like this 'soTimeout.iisFoo'  
U can also define proportional response delay time in respTime.properties  