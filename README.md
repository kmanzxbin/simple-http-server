# simple-http-server

##变更说明
v2020-11-23  
1.修改为NIO实现  
2.支持{}作为通配符匹配path a~b~c~{} 可以匹配 a/b/c/foo路径，内部会自动转化为a/b/c\w+作为key进行正则匹配  
  
##功能说明
1.轻量级的HTTP服务端，能够根据path匹配对应的响应消息然后返回，主要作为接口测试工具使用  
2.没有外部依赖，以简单实用为原则  

##使用方式
chmod u+x *.sh  
./start.sh启动程序  
./stop.sh杀进程  

##配置说明
服务的配置保存在conf/conf.properties中，  
可配置端口、响应等待时长等配置  
也可以通过soTimeout.iisFoo来指定某个path的响应时长  
也可以通过respTime.properties来配置按比例分布的响应时长  