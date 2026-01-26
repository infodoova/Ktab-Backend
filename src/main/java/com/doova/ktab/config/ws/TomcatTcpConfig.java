//package com.doova.ktab.config.ws;
//
//import org.apache.coyote.ProtocolHandler;
//import org.apache.coyote.http11.AbstractHttp11Protocol;
//import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class TomcatTcpConfig {
//
//    @Bean
//    public TomcatServletWebServerFactory tomcatFactory() {
//        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
//
//        factory.addConnectorCustomizers(connector -> {
//            ProtocolHandler handler = connector.getProtocolHandler();
//            if (handler instanceof AbstractHttp11Protocol<?> protocol) {
//                protocol.setTcpNoDelay(true);     // ✅ disable Nagle
//            }
//        });
//
//        return factory;
//    }
//}
//
