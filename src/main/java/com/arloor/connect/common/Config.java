package com.arloor.connect.common;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class Config {
    @JsonIgnore
    private static final String POUND_SIGN = "\u00A3";
    private HttpProxy httpProxy;
    private ControlServer controlServer;


    public HttpProxy getHttpProxy() {
        return httpProxy;
    }

    public void setHttpProxy(HttpProxy httpProxy) {
        this.httpProxy = httpProxy;
    }


    public ControlServer getControlServer() {
        return controlServer;
    }

    public void setControlServer(ControlServer controlServer) {
        this.controlServer = controlServer;
    }

    public static final class HttpProxy {
        private int port = 1080;
        private boolean onlyLocalhost;
        private Server finalServer;
        private List<Router> routers;

        public HttpProxy() {
        }

        public Server route(String targetAddr){
            for (Router router : routers) {
                for (String addrSuffix : router.addrSuffixes) {
                    if (targetAddr.endsWith(addrSuffix)){
                        return router.getServer();
                    }
                }
            }
            return finalServer;
        }

        public HttpProxy(int port,  boolean onlyLocalhost, Server finalServer, List<Router> routers) {
            this.port = port;
            this.onlyLocalhost = onlyLocalhost;
            this.finalServer = finalServer;
            this.routers = routers;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public boolean isOnlyLocalhost() {
            return onlyLocalhost;
        }

        public void setOnlyLocalhost(boolean onlyLocalhost) {
            this.onlyLocalhost = onlyLocalhost;
        }

        public Server getFinalServer() {
            return finalServer;
        }

        public void setFinalServer(Server finalServer) {
            this.finalServer = finalServer;
        }

        public List<Router> getRouters() {
            return routers;
        }

        public void setRouters(List<Router> routers) {
            this.routers = routers;
        }
    }

    public static final class ControlServer {
        private int port = 7229;
        private boolean onlyLocalhost;

        public ControlServer() {
        }

        public ControlServer(int port, boolean onlyLocalhost) {
            this.port = port;
            this.onlyLocalhost = onlyLocalhost;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public boolean isOnlyLocalhost() {
            return onlyLocalhost;
        }

        public void setOnlyLocalhost(boolean onlyLocalhost) {
            this.onlyLocalhost = onlyLocalhost;
        }
    }


    public static final class Router {
        private List<String> addrSuffixes = new ArrayList<>();
        private Server server;

        public List<String> getAddrSuffixes() {
            return addrSuffixes;
        }

        public void setAddrSuffixes(List<String> addrSuffixes) {
            this.addrSuffixes = addrSuffixes;
        }

        public Server getServer() {
            return server;
        }

        public void setServer(Server server) {
            this.server = server;
        }

        public Router(List<String> addrSuffixes, Server server) {
            this.addrSuffixes = addrSuffixes;
            this.server = server;
        }

        public Router() {
        }
    }

    public static final class Server {
        private String host;
        private int port;
        private String userName;
        private String password;

        public Server() {
        }

        public String base64Auth() {
            String userPasswd = userName + ":" + password;
            return Base64.getEncoder().encodeToString(userPasswd.getBytes(StandardCharsets.UTF_8));
        }


        public Server(String host, int port, String userName, String password) {
            this.host = host;
            this.port = port;
            this.userName = userName;
            this.password = password;
        }

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
