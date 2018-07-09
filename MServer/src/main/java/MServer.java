import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import java.io.File;

public class MServer {

    static final int PORT = Integer.parseInt(System.getProperty("port", "8080"));

    public void bind(int port) throws Exception {

        File certChainFile = new File("./src/main/resources/ssl/server.crt");
        File keyFile = new File("./src/main/resources/ssl/pkcs8_server.key");
        File rootFile = new File("./src/main/resources/ssl/ca.crt");
        SslContext sslCtx = SslContextBuilder.forServer(certChainFile, keyFile).trustManager(rootFile).clientAuth(ClientAuth.REQUIRE).build();


        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).option(ChannelOption.SO_BACKLOG, 1024).childHandler(new ChannelInitializer<SocketChannel>() {

                @Override
                protected void initChannel(SocketChannel ch) throws Exception {

                    /*  SSL/TLS Handler */
                    ch.pipeline().addLast(sslCtx.newHandler(ch.alloc()));
                    /*  Protobuf編碼器  */
                    ch.pipeline().addLast(new ProtobufVarint32LengthFieldPrepender());
                    ch.pipeline().addLast(new ProtobufEncoder());
                    /*  Protobuf解碼器  */
                    ch.pipeline().addLast("ProtoDecoder", new ProtobufVarint32FrameDecoder());
                    ch.pipeline().addLast("RequestMsg", new ProtobufDecoder(MessageProto.RequestMsg.getDefaultInstance()));
                    /*  自訂  */
                    ch.pipeline().addLast(new AisHandler());

                }

            });
            ChannelFuture f = b.bind(port).sync();
            f.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) {
        try {
            new MServer().bind(PORT);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}