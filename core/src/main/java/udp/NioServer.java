package udp;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 270k req p second on localhost
 * 340k - 430k req p second with spinning (32 retries)
 * 300k - 380k req p second without retries.
 */
public class NioServer {


    private static final udp.Statistics stats = new udp.Statistics();

    public static void main(String[] args) throws Exception {
        InetSocketAddress bindAddr;
        if (args.length == 2) {
            bindAddr = new InetSocketAddress(args[0], Integer.valueOf(args[1]));
        } else {
            bindAddr = new InetSocketAddress(9876);
        }
        DatagramChannel channel = DatagramChannel.open();
        channel.bind(bindAddr);
        channel.configureBlocking(false);
        DatagramSocket serverSocket = channel.socket();
        serverSocket.setSendBufferSize(66000 * 100);
        serverSocket.setReceiveBufferSize(66000 * 100);
        System.out.println("receive buff size: " + serverSocket.getReceiveBufferSize());
        System.out.println("send buff size: " + serverSocket.getSendBufferSize());
        int queueCapacity = 10000;
        LinkedBlockingQueue<SocketAddress> addresses = new LinkedBlockingQueue<>(queueCapacity);

        byte[] sendData = "dupa".getBytes();
        ByteBuffer sendBuffer = ByteBuffer.allocate(sendData.length);
        sendBuffer.put(sendData);
        sendBuffer.flip();

        ByteBuffer receiveBuffer = ByteBuffer.allocate(1024);

        Selector selector = Selector.open();
        channel.register(selector, SelectionKey.OP_READ);

        while (true) {
            //Here we wait for input messages. We can't get constant stream of input messages even with 150 clients
            //on three remote hosts. In case of some longer request processing waiting may be unnecessary.
            //with active waiting the results are better (spinning)
//            System.out.printf("waiting");
            selector.select();
            SelectionKey key = selector.selectedKeys().iterator().next();
            if (key.isWritable()) {
                //socket is always writable during tests so this branch of code is not used.
                SocketAddress address;
                while ((address = addresses.peek()) != null) {
                    int sentBytes = channel.send(sendBuffer, address);
                    sendBuffer.rewind();
                    if (sentBytes == 0) {
                        key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
//                        System.out.println("Waiting for write or read");
                        break;
                    } else {
                        addresses.poll();
                        stats.onSent();
                    }
                }
//                System.out.printf("Waiting for read");
                key.interestOps(SelectionKey.OP_READ);
            } else if (key.isReadable()) {
                while (true) {
                    int maxTriesCount = 1;
                    SocketAddress receiveAddress = null;
                    while (maxTriesCount-- > 0 && receiveAddress == null) {
                        receiveAddress = channel.receive(receiveBuffer);
                    }
                    receiveBuffer.clear();
                    if (receiveAddress != null) {
                        int sentBytes = channel.send(sendBuffer, receiveAddress);
                        sendBuffer.rewind();
                        if (sentBytes == 0) {
                            System.out.print(" output full ");
                            addresses.offer(receiveAddress);
                            if (addresses.size() == queueCapacity) {
                                System.out.println("queue full");
                                key.interestOps(SelectionKey.OP_WRITE);
//                                System.out.println("waiting for write");
                                break;
                            }
                        } else {
                            stats.onSent();
                            SocketAddress address;
                            while ((address = addresses.peek()) != null) {
                                int sentBytes2 = channel.send(sendBuffer, address);
                                sendBuffer.rewind();
                                if (sentBytes2 == 0) {
                                    break;
                                } else {
                                    addresses.poll();
                                    stats.onSent();
                                }
                            }
                        }
                    } else {
                        //not enough to read
                        if (!addresses.isEmpty()) {
                            key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                        }
                        break;
                    }
                }
            }
        }
    }

}
