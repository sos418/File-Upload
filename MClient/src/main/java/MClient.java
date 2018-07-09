import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import java.io.File;

public class MClient {

    static final String HOST = System.getProperty("host", "127.0.0.1");
    static final int PORT = Integer.parseInt(System.getProperty("port", "8080"));

    public void connect(int port, String host) throws Exception {

        File certChainFile = new File("./src/main/resources/ssl/client.crt");
        File keyFile = new File("./src/main/resources/ssl/pkcs8_client.key");
        File rootFile = new File("./src/main/resources/ssl/ca.crt");
        final SslContext sslCtx = SslContextBuilder.forClient().keyManager(certChainFile, keyFile).trustManager(rootFile).build();

        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group).channel(NioSocketChannel.class).option(ChannelOption.TCP_NODELAY, true).handler(new ChannelInitializer<SocketChannel>() {

                @Override
                protected void initChannel(SocketChannel ch) throws Exception {

                    /*  SSL/TLS Handler */
                    ch.pipeline().addLast(sslCtx.newHandler(ch.alloc()));
                    /*  Protobuf解碼器  */
                    ch.pipeline().addLast(new ProtobufVarint32FrameDecoder());
                    ch.pipeline().addLast(new ProtobufDecoder(MessageProto.ResponseMsg.getDefaultInstance()));
                    /*  Protobuf編碼器  */
                    ch.pipeline().addLast("Protobuf", new ProtobufVarint32LengthFieldPrepender());
                    ch.pipeline().addLast("ProtobufEncoder", new ProtobufEncoder());
                    /*  自訂  */
                    ch.pipeline().addLast(new AisHandler());
                }

            });
            ChannelFuture f = b.connect(host, port).sync();
            f.channel().closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
    }

    public static void main(String[] args) {
        try {
            new MClient().connect(PORT, HOST);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}