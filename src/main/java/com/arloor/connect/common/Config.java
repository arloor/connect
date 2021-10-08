package com.arloor.connect.common;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

public class Config {
    private static final String POUND_SIGN = "\u00A3";

    private int httpPort = 3128;
    private int socks5Port = 1080;
    private int configPort = 1234;
    private int speedLimitKB = 0;
    private int use = 0;
    private List<Server> servers;
    private String user;
    private String pass;
    private boolean auth;
    private boolean supportDomain = true;
    private boolean supportIPv4 = true;
    private boolean supportIPv6 = false;
    private boolean localhost = true;


    public int getRemotePort() {
        return servers.get(use).getPort();
    }

    public String getRemoteHost() {
        return servers.get(use).getHost();
    }

    /**
     * https://datatracker.ietf.org/doc/html/rfc7617
     * The user's name is "test", and the password is the string "123"
     * followed by the Unicode character U+00A3 (POUND SIGN).  Using the
     * character encoding scheme UTF-8, the user-pass becomes:
     * <p>
     * 't' 'e' 's' 't' ':' '1' '2' '3' pound
     * 74  65  73  74  3A  31  32  33  C2  A3
     * <p>
     * Encoding this octet sequence in Base64 ([RFC4648], Section 4) yields:
     * <p>
     * dGVzdDoxMjPCow==
     *
     * @return
     */
    public String getRemoteBasicAuth() {
        Server server = servers.get(use);
        String userPasswd = server.getUserName() + ":" + server.getPassword();
        userPasswd += POUND_SIGN;
        return Base64.getEncoder().encodeToString(userPasswd.getBytes(StandardCharsets.UTF_8));
    }

    public String getClientBasicAuth() {
        String userPasswd = user + ":" + pass;
        userPasswd += POUND_SIGN;
        return Base64.getEncoder().encodeToString(userPasswd.getBytes(StandardCharsets.UTF_8));
    }

    public int getConfigPort() {
        return configPort;
    }

    public void setConfigPort(int configPort) {
        this.configPort = configPort;
    }

    public int getHttpPort() {
        return httpPort;
    }

    public void setHttpPort(int httpPort) {
        this.httpPort = httpPort;
    }

    public int getSocks5Port() {
        return socks5Port;
    }

    public void setSocks5Port(int socks5Port) {
        this.socks5Port = socks5Port;
    }

    public int getSpeedLimitKB() {
        return speedLimitKB;
    }

    public void setSpeedLimitKB(int speedLimitKB) {
        this.speedLimitKB = speedLimitKB;
    }

    public int getUse() {
        return use;
    }

    public void setUse(int use) {
        this.use = use;
    }

    public List<Server> getServers() {
        return servers;
    }

    public void setServers(List<Server> servers) {
        this.servers = servers;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPass() {
        return pass;
    }

    public void setPass(String pass) {
        this.pass = pass;
    }

    public boolean isAuth() {
        return auth;
    }

    public void setAuth(boolean auth) {
        this.auth = auth;
    }

    public boolean isSupportDomain() {
        return supportDomain;
    }

    public void setSupportDomain(boolean supportDomain) {
        this.supportDomain = supportDomain;
    }

    public boolean isSupportIPv4() {
        return supportIPv4;
    }

    public void setSupportIPv4(boolean supportIPv4) {
        this.supportIPv4 = supportIPv4;
    }

    public boolean isSupportIPv6() {
        return supportIPv6;
    }

    public void setSupportIPv6(boolean supportIPv6) {
        this.supportIPv6 = supportIPv6;
    }

    public boolean isLocalhost() {
        return localhost;
    }

    public void setLocalhost(boolean localhost) {
        this.localhost = localhost;
    }


    private static final class Server {
        private String host;
        private int port;
        private String userName;
        private String password;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
