package udp;

/**
 * Created by Paweł Sikora.
 */

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Configuration: receiver Thread -> Protocol thread -> Application thread -> sending Thread. Gives 350000 req p second on localhost.
 * Configuration: receiver Thread -> Protocol thread -> Application thread -> sending Thread. Gives 450000 - 500000  req p second with remote clients.
 * Configuration: receiver Thread -> Protocol thread -> Application thread. - similar results.
 */
class QueueServerWithProcessing {

    public static class Sender implements Runnable {
        final byte[] sendData = "dupa".getBytes();
        final DatagramSocket socket;
        final Statistics stats;
        final LinkedBlockingQueue<InetSocketAddress> addresses;

        public Sender(DatagramSocket socket, Statistics stats, LinkedBlockingQueue<InetSocketAddress> addresses) {
            this.socket = socket;
            this.stats = stats;
            this.addresses = addresses;
        }

        public void send(InetSocketAddress address) {
            final DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length);
            sendPacket.setAddress(address.getAddress());
            sendPacket.setPort(address.getPort());
            try {
                socket.send(sendPacket);
            } catch (IOException e) {
                e.printStackTrace();
            }
            stats.onSent();
        }


        @Override
        public void run() {
            try {
                while (true) {
                    InetSocketAddress address = addresses.poll(100, TimeUnit.DAYS);
                    send(address);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    public static void main(String args[]) throws Exception {
        InetSocketAddress bindAddr;
        if (args.length == 2) {
            bindAddr = new InetSocketAddress(args[0], Integer.valueOf(args[1]));
        } else {
            bindAddr = new InetSocketAddress(9876);
        }
        final DatagramSocket serverSocket = new DatagramSocket(bindAddr);
        serverSocket.setSendBufferSize(66000 * 100);
        serverSocket.setReceiveBufferSize(66000 * 100);
        System.out.println("receive buff size: " + serverSocket.getReceiveBufferSize());
        System.out.println("send buff size: " + serverSocket.getSendBufferSize());
        final LinkedBlockingQueue<InetSocketAddress> addresses = new LinkedBlockingQueue<>(10000);
        Statistics stats = new Statistics();
        Executor processingExecutor = Executors.newFixedThreadPool(1, r -> {
            Thread th = new Thread(r);
            th.setDaemon(true);
            th.setName("queueServer-executor");
            return th;
        });
        Executor appLogicExecutor = Executors.newFixedThreadPool(1, r -> {
            Thread th = new Thread(r);
            th.setDaemon(true);
            th.setName("appLogic-executor");
            return th;
        });
        Sender sender = new Sender(serverSocket, stats, addresses);
        Runnable receiver = () -> {
            byte[] receiveData = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            try {
                while (true) {
                    serverSocket.receive(receivePacket);
                    final InetSocketAddress addr = new InetSocketAddress(receivePacket.getAddress(), receivePacket.getPort());
                    processingExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            long val = 0;
                            try {
//                                for (int i = 0; i < 1000; ++i) {
//                                    val = System.currentTimeMillis();
//                                }
//                                addresses.add(addr);
                                appLogicExecutor.execute(new Runnable() {
                                    @Override
                                    public void run() {
//                                        addresses.add(addr);
                                        sender.send(addr);
                                    }
                                });
                            } catch (Exception e) {
                                System.out.printf(String.valueOf(val));
                                e.printStackTrace();
                            }
                        }
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        };



        Thread senderThread = new Thread(sender);
        Thread receiverThread = new Thread(receiver);
        senderThread.start();
        receiverThread.start();
        receiverThread.join();
    }
}