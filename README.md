## 客户端代理

配合[HttpProxy](https://github.com/arloor/HttpProxy)使用

在本机的1080启动socks5代理，本机3128启动http代理

## 配置文件

```json
{
  "socks5Port": 1080,
  "httpPort": 3128,
  "configPort": 1234,
  "speedLimitKB": 0,
  "use": 0,
  "servers": [
    {
      "host": "localhost",
      "port": 443,
      "userName": "aaaa",
      "password": "aaaaa"
    }
  ],
  "user": "aaaa",
  "pass": "aaaa",
  "auth": true,
  "supportDomain": true,
  "supportIPv4": true,
  "supportIPv6": false
}
```