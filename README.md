## 客户端代理

配合[HttpProxy](https://github.com/arloor/HttpProxy)使用

在本机的1080启动socks5代理，本机3128启动http代理

## 配置文件

```json
{
  "socks5Proxy": {
    "port": 1080,
    "user": "user",
    "passwd": "passwd",
    "checkAuth": false,
    "onlyLocalhost": false,
    "finalServer": {
      "host": "xxxxx",
      "port": 443,
      "userName": "xxx",
      "password": "xxx"
    },
    "routers": [{
      "addrSuffixes": ["nflxso.net", "nflxvideo.net", "netflix.com", "nflxext.com"],
      "server": {
        "host": "xxx",
        "port": 443,
        "userName": "xxx",
        "password": "xxx"
      }
    }]
  },
  "httpProxy": {
    "port": 3128,
    "user": "xx",
    "passwd": "xx",
    "checkAuth": false,
    "onlyLocalhost": false,
    "server": {
      "host": "xx",
      "port": 443,
      "userName": "xx",
      "password": "xx"
    }
  },
  "controlServer": {
    "port": 7229,
    "onlyLocalhost": true
  }
}
```