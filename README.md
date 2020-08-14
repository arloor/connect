## 客户端代理

配合[HttpProxy](https://github.com/arloor/HttpProxy)使用

在本机的1080启动socks5代理，本机3128启动http代理

## 配置文件

```json
{
  "Socks5Port": 1080,
  "HttpPort": 3128,
  "SpeedLimitKB": 0,
  "Use": 0,
  "Servers": [
    {
      "ProxyAddr": "https_server",
      "ProxyPort": 443,
      "UserName": "aaa",
      "Password": "aaaaa"
    },
    {
      "ProxyAddr": "localhost",
      "ProxyPort": 443,
      "UserName": "aaa",
      "Password": "aaaaa"
    }
  ],
  "User": "aaaa",
  "Pass": "aaaa",
  "Auth":true,
  "SupportDomain": true,
  "SupportIPv4": true,
  "SupportIPv6": false
}
```