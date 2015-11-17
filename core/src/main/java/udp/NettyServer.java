package udp;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.CharsetUtil;

/**
 * @author Paweł Sikora
 */

public class NettyServer {


    private static final int PORT = Integer.parseInt(System.getProperty("port", "9876"));

    private static final Statistics stats = new Statistics();

    public static void main(String[] args) throws Exception {
        EventLoopGroup executor = new NioEventLoopGroup(1);
        try {
            Bootstrap b = new Bootstrap();
            b.group(executor)
                    .channel(NioDatagramChannel.class)
                    .handler(new QuoteOfTheMomentServerHandler());

            ChannelFuture channelFuture = b.bind(PORT);
            channelFuture.sync().channel().closeFuture().await();

        } finally {
            executor.shutdownGracefully();
        }
    }

    public static class QuoteOfTheMomentServerHandler extends SimpleChannelInboundHandler<DatagramPacket> {

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            // We don't close the channel because we can keep serving requests.
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
            ChannelFuture fut = ctx.writeAndFlush(new DatagramPacket(Unpooled.copiedBuffer("dupa", CharsetUtil.UTF_8), packet.sender()));
            if (fut.isSuccess()) {
                stats.onSent();
            } else {
                fut.addListener(future -> {
                    if (fut.isSuccess()) {
                        System.out.printf("Delayed success");
                        stats.onSent();
                    } else {
                        System.out.println("send failed");
                    }
                });
            }
        }
    }
}
