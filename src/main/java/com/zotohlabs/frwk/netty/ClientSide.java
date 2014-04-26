
package com.zotohlabs.frwk.netty;

public enum ClientSide {
;

  private static ChannelHandler makeChannelInitor(PipelineConfigurator cfg, JSONObject options) {
    return new ChannelInitializer() {
      public void initChannel(Channel ch) {
        cfg.handle(ch.pipeline(), options);
      }
    };
  }

  public static Bootstrap initClientSide(PipelineConfigurator cfg, JSONObject options) {
    Bootstrap bs= new Bootstrap();
    bs.group( new NioEventLoopGroup() );
    bs.channel(NioSocketChannel.class);
    bs.option(ChannelOption.SO_KEEPALIVE,true);
    bs.option(ChannelOption.TCP_NODELAY,true);
    bs.handler( makeChannelInitor(cfg, options));
    return bs;
  }

  private static final URL_KEY = new AttributeKey("targetUrl");

  public static Channel connect(Bootstrap bs, URL targetUrl) throws IOException {
    boolean ssl = "https".equals(targetUrl.getProtocol());
    int pnum = targetUrl.getPort();
    int port = (pnum < 0) ? (ssl ?  443 : 80) : pnum;
    String host = targetUrl.getHost();
    InetSocketAddress sock = new InetSocketAddress( host, port);
    ChannelFuture cf = bs.connect(sock).sync();
    if (! cf.isSuccess() ) {
      throw new IOException("Connect failed: ", cf.cause() );
    }
    Channel c= cf.channel();
    c.attr(URL_KEY).set(targetUrl);
    return c;
  }

  public static  XData post(Channel c, XData data, JSONObject options) {
    return send(c, "POST", data, options);
  }

  public static  XData post(Channel c, XData data) {
    return send(c,"POST", data, new JSONObject() );
  }

  public static  XData get(Channel c, JSONObject options) {
    return send(c, "GET", options);
  }

  public static  XData get(Channel c) {
    return send(c,"GET", new JSONObject() );
  }

  private static void send(Channel ch, String op, XData xdata, JSONObject options) {
    long clen = (xdata == null) ?  0L : xdata.size();
    URL targetUrl = (URL) ch.attr(URL_KEY).get();
    String mo = options.optString("override");
         md (if (> clen 0)
              (if (hgl? mo) "POST")
              (if (hgl? mo) mo "GET"))
         mt (if-let [mo mo] mo md)
         req (DefaultHttpRequest. HttpVersion/HTTP_1_1
                                  (HttpMethod/valueOf mt)
                                  (nsb targetUrl))
         presend (:presend options) ]

    (HttpHeaders/setHeader req "Connection" (if (:keep-alive options) "keep-alive" "close"))
    (HttpHeaders/setHeader req "host" (.getHost targetUrl))
    (when (fn? presend) (presend ch req))

    (let [ ct (HttpHeaders/getHeader req "content-type") ]
      (when (and (cstr/blank? ct)
                 (> clen 0))
        (HttpHeaders/setHeader req "content-type" "application/octet-stream")))

    (HttpHeaders/setContentLength req clen)
    (log/debug "Netty client: about to flush out request (headers)")
    (log/debug "Netty client: content has length " clen)
    (with-local-vars [wf nil]
      (var-set wf (WWrite ch req))
      (if (> clen 0)
        (var-set wf (if (> clen (com.zotohlabs.frwk.io.IOUtils/streamLimit))
                      (WFlush ch (ChunkedStream. (.stream xdata)))
                      (WFlush ch (Unpooled/wrappedBuffer (.javaBytes xdata)))))
        (NetUtils/flush ch))
      (CloseCF @wf (:keep-alive options) ))
  ))

  }


}


