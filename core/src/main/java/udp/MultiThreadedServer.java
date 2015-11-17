package udp;

/**
 * .
 */

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;

class MultiThreadedServer {

    private static final Statistics stats = new Statistics();

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
        Runnable runnable = () -> {
            byte[] receiveData = new byte[1024];
            byte[] sendData = "dupa".getBytes();
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length);
            try {
                while (true) {
                    serverSocket.receive(receivePacket);
                    InetAddress IPAddress = receivePacket.getAddress();
                    int port = receivePacket.getPort();
                    sendPacket.setAddress(IPAddress);
                    sendPacket.setPort(port);
                    serverSocket.send(sendPacket);
                    stats.onSent();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
        Thread t1 = new Thread(runnable);
        Thread t2 = new Thread(runnable);
        t1.start();
        t2.start();
        System.out.println("threads started");
        t1.join();
        t2.join();
    }
}